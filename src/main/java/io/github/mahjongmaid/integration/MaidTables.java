package io.github.mahjongmaid.integration;

import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import io.github.mahjongmaid.MahjongMaidMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class MaidTables {
    public static final String MARKER = MahjongMaidMod.ID + ":seat";
    public static final ResourceLocation TASK = ResourceLocation.fromNamespaceAndPath(MahjongMaidMod.ID, "mahjong");
    public static final double SEARCH_RADIUS = 8.0;
    // Match the chess enjoyment/owner-win convention, with independent persisted cooldowns.
    static final Type PLAYED = new Type("MahjongMaidPlayed", 2, 24000);
    static final Type OWNER_WON = new Type("MahjongMaidOwnerWon", 4, 18000);

    private MaidTables() {}

    public static void initializeFavorability() { /* Force registration before entity NBT is read. */ }

    public static boolean isPlaying(EntityMaid maid) {
        return maid.getPersistentData().contains(MARKER);
    }

    static boolean eligible(EntityMaid maid) {
        return maid.isAlive() && maid.isTame() && TASK.equals(maid.getTask().getUid())
                && !maid.isMaidInSittingPose() && !maid.isSleeping()
                && !maid.isPassenger() && !maid.isVehicle() && !maid.isLeashed() && !isPlaying(maid);
    }

    static boolean belongsTo(EntityMaid maid, MahjongTableBlockEntity table) {
        CompoundTag marker = maid.getPersistentData().getCompound(MARKER);
        return marker.getLong("Table") == table.getBlockPos().asLong()
                && table.getLevel() != null
                && marker.getString("Dimension").equals(table.getLevel().dimension().location().toString());
    }

    static void hold(EntityMaid maid, MahjongTableBlockEntity table) {
        CompoundTag marker = new CompoundTag();
        marker.putLong("Table", table.getBlockPos().asLong());
        marker.putString("Dimension", table.getLevel().dimension().location().toString());
        maid.getPersistentData().put(MARKER, marker);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.setInSittingPose(true);
    }

    static void release(EntityMaid maid) {
        if (!isPlaying(maid)) return;
        maid.getPersistentData().remove(MARKER);
        maid.setInSittingPose(false);
    }

    /** Recovers a saved maid if its table was destroyed, moved, or unloaded first. */
    public static void onMaidTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel level)
                || !isPlaying(maid) || maid.tickCount < 40 || maid.tickCount % 20 != 0) return;
        CompoundTag marker = maid.getPersistentData().getCompound(MARKER);
        BlockPos pos = BlockPos.of(marker.getLong("Table"));
        if (!marker.getString("Dimension").equals(level.dimension().location().toString())
                || !level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)
                || !((MaidTableAccess) table).mahjongmaid$session().contains(maid.getUUID())) {
            release(maid);
        }
    }
}
