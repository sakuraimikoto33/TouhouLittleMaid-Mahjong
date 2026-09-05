package io.github.mahjongmaid.integration;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.TheMahjongDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One match's fixed roster; never recruits new maids in the middle of a match. */
public final class TableMaidSession {
    private static final String NBT_KEY = "MahjongMaidParticipants";
    private final MahjongTableBlockEntity table;
    private final Map<Integer, Participant> participants = new LinkedHashMap<>();
    private int missingEntityGrace;

    public TableMaidSession(MahjongTableBlockEntity table) {
        this.table = table;
    }

    @Nullable
    public String name(int seat) {
        Participant participant = participants.get(seat);
        return participant == null ? null : participant.name;
    }

    public boolean contains(UUID uuid) {
        return participants.values().stream().anyMatch(p -> p.uuid.equals(uuid));
    }

    public int size() { return participants.size(); }

    public void start() {
        cancel();
        if (!(table.getLevel() instanceof ServerLevel level) || table.driver() == null) return;
        Vec3 center = Vec3.atCenterOf(table.getBlockPos());
        List<EntityMaid> candidates = new ArrayList<>(level.getEntitiesOfClass(EntityMaid.class,
                new AABB(center, center).inflate(MaidTables.SEARCH_RADIUS),
                maid -> MaidTables.eligible(maid)
                        && maid.distanceToSqr(center) <= MaidTables.SEARCH_RADIUS * MaidTables.SEARCH_RADIUS));
        candidates.sort(Comparator.comparingDouble((EntityMaid maid) -> maid.distanceToSqr(center))
                .thenComparing(EntityMaid::getUUID));
        var seats = table.seats().subList(0, table.driver().match().playerCount());
        int wanted = SeatSelection.available(seats.stream().map(s -> s.enabled()).toList(),
                seats.stream().map(s -> s.occupant().isPresent()).toList(), candidates.size()).size();
        for (int seat = 0; seat < seats.size() && participants.size() < wanted; seat++) {
            if (!seats.get(seat).enabled() || seats.get(seat).occupant().isPresent()) continue;
            Iterator<EntityMaid> iterator = candidates.iterator();
            while (iterator.hasNext()) {
                EntityMaid maid = iterator.next();
                Vec3 position = seatPosition(seat);
                if (!safeSeat(level, maid, position)) continue;
                iterator.remove();
                participants.put(seat, new Participant(maid.getUUID(), maid.getName().getString()));
                MaidTables.hold(maid, table);
                maid.teleportTo(position.x, position.y, position.z);
                maid.setDeltaMovement(Vec3.ZERO);
                maid.fallDistance = 0;
                faceTable(maid);
                break;
            }
        }
        sync();
    }

    public void tick() {
        if (participants.isEmpty() || !(table.getLevel() instanceof ServerLevel level)) return;
        TheMahjongDriver driver = table.driver();
        if (driver == null || table.state() != MahjongTableBlockEntity.State.GAME) {
            cancel();
            return;
        }
        boolean changed = false;
        boolean waitingForMaid = false;
        if (missingEntityGrace > 0) missingEntityGrace--;
        Iterator<Map.Entry<Integer, Participant>> iterator = participants.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int seat = entry.getKey();
            EntityMaid maid = resolve(level, entry.getValue());
            boolean validSeat = seat < driver.match().playerCount()
                    && table.seats().get(seat).enabled() && table.seats().get(seat).occupant().isEmpty();
            if (maid == null && validSeat && missingEntityGrace > 0) {
                waitingForMaid = true;
                continue;
            }
            if (!validSeat || maid == null || !maid.isAlive() || !MaidTables.belongsTo(maid, table)
                    || !MaidTables.TASK.equals(maid.getTask().getUid()) || !maid.isMaidInSittingPose()
                    || maid.isPassenger() || maid.distanceToSqr(seatPosition(seat)) > 4.0) {
                if (maid != null && MaidTables.belongsTo(maid, table)) MaidTables.release(maid);
                iterator.remove();
                changed = true;
                continue;
            }
            faceTable(maid);
        }
        // Observe the terminal phase after advance, not a per-hand win/result animation.
        if (driver.currentPhase() instanceof MatchPhase.MatchEnded && !waitingForMaid) {
            finish(level, driver);
        } else if (changed) {
            sync();
        }
    }

    private void finish(ServerLevel level, TheMahjongDriver driver) {
        var round = driver.match().currentRound();
        for (var entry : participants.entrySet()) {
            EntityMaid maid = resolve(level, entry.getValue());
            if (maid == null || !MaidTables.belongsTo(maid, table)) continue;
            maid.getFavorabilityManager().apply(MaidTables.PLAYED);
            if (round.isPresent() && maid.getOwnerUUID() != null) {
                var ownerSeat = table.seatOfPlayer(maid.getOwnerUUID());
                if (ownerSeat.isPresent() && ownerSeat.getAsInt() < round.get().players().size()
                        && round.get().players().get(ownerSeat.getAsInt()).points()
                        > round.get().players().get(entry.getKey()).points()) {
                    maid.getFavorabilityManager().apply(MaidTables.OWNER_WON);
                }
            }
        }
        cancel();
    }

    public void cancel() {
        if (participants.isEmpty()) return;
        if (table.getLevel() instanceof ServerLevel level) {
            for (var entry : participants.entrySet()) {
                EntityMaid maid = resolve(level, entry.getValue());
                if (maid != null && MaidTables.belongsTo(maid, table)) MaidTables.release(maid);
            }
        }
        participants.clear();
        sync();
    }

    private Vec3 seatPosition(int seat) {
        Direction direction = table.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (int i = 0; i < seat; i++) direction = direction.getCounterClockWise();
        return Vec3.atBottomCenterOf(table.getBlockPos())
                .add(direction.getStepX() * 2.1, 0, direction.getStepZ() * 2.1);
    }

    private boolean safeSeat(ServerLevel level, EntityMaid maid, Vec3 target) {
        BlockPos below = BlockPos.containing(target).below();
        return level.getWorldBorder().isWithinBounds(BlockPos.containing(target))
                && level.hasChunkAt(BlockPos.containing(target))
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.noCollision(maid, maid.getBoundingBox().move(target.subtract(maid.position())))
                && !level.containsAnyLiquid(maid.getBoundingBox().move(target.subtract(maid.position())));
    }

    private void faceTable(EntityMaid maid) {
        Vec3 center = Vec3.atCenterOf(table.getBlockPos());
        float yaw = (float) Math.toDegrees(Math.atan2(center.z - maid.getZ(), center.x - maid.getX())) - 90;
        maid.setYRot(yaw);
        maid.setYHeadRot(yaw);
        maid.setYBodyRot(yaw);
        maid.getLookControl().setLookAt(center.x, center.y + 0.5, center.z);
    }

    @Nullable
    private EntityMaid resolve(ServerLevel level, Participant participant) {
        return level.getEntity(participant.uuid) instanceof EntityMaid maid ? maid : null;
    }

    private void sync() {
        table.setChanged();
        if (table.getLevel() instanceof ServerLevel level && !table.isRemoved()) {
            level.sendBlockUpdated(table.getBlockPos(), table.getBlockState(), table.getBlockState(), 3);
        }
    }

    public void save(CompoundTag tag) {
        tag.remove(NBT_KEY);
        if (participants.isEmpty()) return;
        CompoundTag session = new CompoundTag();
        session.putLong("Table", table.getBlockPos().asLong());
        ListTag list = new ListTag();
        for (var entry : participants.entrySet()) {
            CompoundTag participant = new CompoundTag();
            participant.putInt("Seat", entry.getKey());
            participant.putUUID("Maid", entry.getValue().uuid);
            participant.putString("Name", entry.getValue().name);
            list.add(participant);
        }
        session.put("Participants", list);
        tag.put(NBT_KEY, session);
    }

    public void load(CompoundTag tag) {
        participants.clear();
        CompoundTag session = tag.getCompound(NBT_KEY);
        // A moved/dropped/record-cloned table must not claim the original table's maids.
        if (!session.contains("Table") || session.getLong("Table") != table.getBlockPos().asLong()) return;
        ListTag list = session.getList("Participants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && participants.size() < 3; i++) {
            CompoundTag value = list.getCompound(i);
            int seat = value.getInt("Seat");
            if (seat >= 0 && seat < 4 && value.hasUUID("Maid") && !contains(value.getUUID("Maid"))) {
                participants.put(seat, new Participant(value.getUUID("Maid"), value.getString("Name")));
            }
        }
        missingEntityGrace = 200;
    }

    private record Participant(UUID uuid, String name) {}
}
