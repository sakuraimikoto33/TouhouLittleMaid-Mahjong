package io.github.mahjongmaid.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskIdle;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjongforge.mahjongtable.RuleSetPreset;
import com.themahjong.driver.MatchPhase;
import com.themahjong.driver.bots.StupidActiveBot;
import io.github.mahjongmaid.integration.MaidTableAccess;
import io.github.mahjongmaid.integration.MaidTables;
import io.github.mahjongmaid.integration.TableMaidSession;
import io.github.mahjongmaid.maid.MahjongTask;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Exercises the integration through registered entities, table APIs and applied mixins. */
@GameTestHolder("touhou_little_maid_mahjong")
@PrefixGameTestTemplate(false)
public final class MahjongIntegrationTests {
    private static final BlockPos TABLE = new BlockPos(10, 1, 10);

    private MahjongIntegrationTests() {}

    @GameTest(template = "empty")
    public static void noHumansStillLimitsMaidsToThree(GameTestHelper helper) {
        checkSeatCapacity(helper, 0, 3);
    }

    @GameTest(template = "empty")
    public static void oneHumanLeavesThreeMaidSeats(GameTestHelper helper) {
        checkSeatCapacity(helper, 1, 3);
    }

    @GameTest(template = "empty")
    public static void twoHumansLeaveTwoMaidSeats(GameTestHelper helper) {
        checkSeatCapacity(helper, 2, 2);
    }

    @GameTest(template = "empty")
    public static void threeHumansLeaveOneMaidSeat(GameTestHelper helper) {
        checkSeatCapacity(helper, 3, 1);
    }

    @GameTest(template = "empty")
    public static void fourHumansLeaveNoMaidSeats(GameTestHelper helper) {
        checkSeatCapacity(helper, 4, 0);
    }

    @GameTest(template = "empty")
    public static void sanmaWithoutHumansAllowsThreeMaids(GameTestHelper helper) {
        checkSanmaSeatCapacity(helper, 0, 3);
    }

    @GameTest(template = "empty")
    public static void sanmaWithOneHumanAllowsTwoMaids(GameTestHelper helper) {
        checkSanmaSeatCapacity(helper, 1, 2);
    }

    @GameTest(template = "empty")
    public static void sanmaWithTwoHumansAllowsOneMaid(GameTestHelper helper) {
        checkSanmaSeatCapacity(helper, 2, 1);
    }

    @GameTest(template = "empty")
    public static void sanmaWithThreeHumansAllowsNoMaids(GameTestHelper helper) {
        checkSanmaSeatCapacity(helper, 3, 0);
    }

    private static void checkSanmaSeatCapacity(GameTestHelper helper, int humans, int expected) {
        try (Fixture fixture = new Fixture(helper, humans, RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            for (int i = 0; i < 4; i++) fixture.maid(i, true);
            fixture.start();
            helper.assertTrue(fixture.session().size() == expected,
                    "Sanma with " + humans + " humans must recruit exactly " + expected + " maids");
            helper.assertTrue(fixture.maids.stream().filter(MaidTables::isPlaying).count() == expected,
                    "Only the recruited sanma maids may be held at the table");
            assertSanmaTable(helper, fixture);
            for (int seat = 0; seat < humans; seat++) {
                helper.assertTrue(fixture.table.seats().get(seat).occupant().orElseThrow()
                                .equals(fixture.humans.get(seat)),
                        "Sanma recruitment must preserve human seat " + seat);
                helper.assertTrue(fixture.session().name(seat) == null,
                        "A human sanma seat must never receive a maid name");
            }
            for (int seat = humans; seat < 3; seat++) {
                helper.assertTrue(fixture.table.driver().playerAt(seat) instanceof StupidActiveBot,
                        "Sanma maid seats must use the upstream bot implementation");
            }
            // The reported crash occurs in the real table's saveAdditional, including client updates.
            assertSanmaTag(helper, fixture.table.getUpdateTag());
            helper.assertTrue(fixture.table.getUpdatePacket() != null,
                    "A running sanma table must produce its ordinary client update packet");
            fixture.tick();
            assertSanmaTable(helper, fixture);
        }
        helper.succeed();
    }

    private static void assertSanmaTable(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.table.driver() != null && fixture.table.driver().match().playerCount() == 3,
                "The sanma match must retain exactly three engine players");
        helper.assertTrue(fixture.table.seats().size() == 4 && fixture.table.enabledSeatCount() == 3,
                "The table must retain its four physical seats with three enabled");
        helper.assertFalse(fixture.table.seats().get(3).enabled(),
                "The fourth physical seat must stay disabled in sanma");
        helper.assertTrue(fixture.table.seats().get(3).occupant().isEmpty()
                        && fixture.session().name(3) == null,
                "The disabled fourth seat must have neither a human nor a maid");
    }

    private static void assertSanmaTag(GameTestHelper helper, CompoundTag tag) {
        var seats = tag.getList("Seats", Tag.TAG_COMPOUND);
        helper.assertTrue(seats.size() == 4 && !seats.getCompound(3).getBoolean("enabled"),
                "Table serialization must preserve the disabled fourth physical seat");
        helper.assertTrue(tag.contains("Driver", Tag.TAG_COMPOUND),
                "Sanma serialization must retain the running driver state");
        var players = tag.getList("HumanPlayers", Tag.TAG_COMPOUND);
        helper.assertTrue(players.size() == 3,
                "Sanma must serialize player input state for its three engine players only");
        for (int seat = 0; seat < players.size(); seat++) {
            helper.assertTrue(players.getCompound(seat).getInt("Seat") == seat,
                    "Serialized sanma player input state must retain engine seat " + seat);
        }
    }

    @GameTest(template = "empty")
    public static void sanmaCanReturnToFourPlayersAfterEnding(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1, RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            for (int i = 0; i < 3; i++) fixture.maid(i, true);
            fixture.start();
            helper.assertTrue(fixture.session().size() == 2, "Sanma must first recruit only two maids");
            assertSanmaTag(helper, fixture.table.getUpdateTag());
            fixture.table.endGame();
            fixture.table.selectPreset(RuleSetPreset.MAHJONG_SOUL_4P);
            fixture.start();
            fixture.tick();
            helper.assertTrue(fixture.table.driver().match().playerCount() == 4
                            && fixture.table.enabledSeatCount() == 4,
                    "Selecting four-player mahjong must reopen the fourth seat and create four engine players");
            helper.assertTrue(fixture.session().size() == 3 && fixture.session().name(3) != null,
                    "The reopened fourth seat must allow the third maid to join");
            CompoundTag saved = fixture.table.saveWithoutMetadata();
            helper.assertTrue(saved.getList("Seats", Tag.TAG_COMPOUND).size() == 4
                            && saved.getList("Seats", Tag.TAG_COMPOUND).getCompound(3).getBoolean("enabled")
                            && saved.getList("HumanPlayers", Tag.TAG_COMPOUND).size() == 4,
                    "Four-player serialization must retain all four enabled seats and player input states");
            fixture.table.load(saved);
            fixture.tick();
            helper.assertTrue(fixture.table.driver().match().playerCount() == 4 && fixture.session().size() == 3,
                    "The four-player match and all three maids must survive save/load after sanma");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sanmaWithoutMaidsCanSyncSaveLoadAndTick(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1, RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            fixture.start();
            helper.assertTrue(fixture.session().size() == 0, "This sanma regression requires no maid participants");
            assertSanmaTag(helper, fixture.table.getUpdateTag());
            helper.assertTrue(fixture.table.getUpdatePacket() != null,
                    "Sanma updates must also succeed without maid participants");
            CompoundTag saved = fixture.table.saveWithoutMetadata();
            assertSanmaTag(helper, saved);
            fixture.table.load(saved);
            fixture.tick();
            assertSanmaTable(helper, fixture);
            helper.assertTrue(fixture.table.seats().get(0).occupant().orElseThrow().equals(fixture.humans.get(0)),
                    "The sanma human occupant must survive a table save and reload");
            for (int seat = 1; seat < 3; seat++) {
                helper.assertTrue(fixture.table.driver().playerAt(seat) instanceof StupidActiveBot,
                        "Sanma empty seats must remain ordinary bots after reloading");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sanmaSaveLoadPreservesMaidSeatsAndClosedSeat(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1, RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            fixture.maid(0, true);
            fixture.maid(1, true);
            fixture.start();
            String firstName = fixture.session().name(1);
            String secondName = fixture.session().name(2);
            CompoundTag saved = fixture.table.saveWithoutMetadata();
            assertSanmaTag(helper, saved);
            fixture.table.load(saved);
            fixture.tick();
            assertSanmaTable(helper, fixture);
            helper.assertTrue(fixture.session().size() == 2
                            && fixture.maids.stream().allMatch(maid -> fixture.session().contains(maid.getUUID())
                                    && MaidTables.isPlaying(maid)),
                    "Both sanma maids must remain associated after save, load and the next server tick");
            helper.assertTrue(firstName.equals(fixture.session().name(1))
                            && secondName.equals(fixture.session().name(2)),
                    "Sanma save/load must preserve each maid at the same seat");
            assertSanmaTag(helper, fixture.table.getUpdateTag());
            helper.assertTrue(fixture.table.getUpdatePacket() != null,
                    "The restored sanma table must still produce client updates");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void completedSanmaRewardsAllThreeMaidsOnce(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0, RuleSetPreset.MAHJONG_SOUL_SANMA_3P)) {
            for (int i = 0; i < 3; i++) fixture.maid(i, true);
            List<Integer> before = fixture.maids.stream().map(EntityMaid::getFavorability).toList();
            fixture.start();
            helper.assertTrue(fixture.session().size() == 3, "All three maids must join the sanma bot match");
            fixture.tick();
            finishBotMatch(helper, fixture);
            // Observe completion through the normal table ticker and its completion synchronization.
            fixture.tick();
            assertSanmaTable(helper, fixture);
            helper.assertTrue(fixture.session().size() == 0,
                    "Completing a real sanma bot match must clear the participant roster");
            for (int i = 0; i < fixture.maids.size(); i++) {
                EntityMaid maid = fixture.maids.get(i);
                helper.assertTrue(maid.getFavorability() == before.get(i) + 2,
                        "Each sanma maid must receive exactly two completed-game enjoyment points");
                helper.assertFalse(MaidTables.isPlaying(maid), "A completed sanma match must release every maid");
            }
            fixture.tick();
            for (int i = 0; i < fixture.maids.size(); i++) {
                helper.assertTrue(fixture.maids.get(i).getFavorability() == before.get(i) + 2,
                        "Repeated sanma completion ticks must not duplicate favorability rewards");
            }
        }
        helper.succeed();
    }

    private static void checkSeatCapacity(GameTestHelper helper, int humans, int expected) {
        try (Fixture fixture = new Fixture(helper, humans)) {
            for (int i = 0; i < 4; i++) fixture.maid(i, true);
            fixture.start();
            helper.assertTrue(fixture.session().size() == expected,
                    humans + " human seats must leave exactly " + expected + " maid participants");
            long held = fixture.maids.stream().filter(MaidTables::isPlaying).count();
            helper.assertTrue(held == expected, "Only recruited maids should be held at the table");
            for (int seat = 0; seat < humans; seat++) {
                helper.assertTrue(fixture.table.seats().get(seat).occupant().orElseThrow()
                                .equals(fixture.humans.get(seat)),
                        "Maid recruitment must preserve human seat " + seat);
                helper.assertTrue(fixture.session().name(seat) == null,
                        "A human seat must never receive a maid name");
            }
            for (int seat = humans; seat < 4; seat++) {
                helper.assertTrue(fixture.table.driver().playerAt(seat) instanceof StupidActiveBot,
                        "Maid seats must retain the upstream bot implementation");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyNearbyTamedMahjongMaidsParticipate(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid eligible = fixture.maid(0, true);
            EntityMaid wrongTask = fixture.maid(1, false);
            EntityMaid untamed = fixture.maid(2, true);
            untamed.setTame(false);
            EntityMaid far = fixture.maid(3, true);
            Vec3 farPosition = helper.absoluteVec(new Vec3(10.5, 1, 19.5));
            far.moveTo(farPosition.x, farPosition.y, farPosition.z, 0, 0);
            fixture.start();
            helper.assertTrue(fixture.session().size() == 1 && fixture.session().contains(eligible.getUUID()),
                    "Only the nearby tamed maid with the mahjong task may participate");
            helper.assertFalse(MaidTables.isPlaying(wrongTask), "An idle maid must remain free");
            helper.assertFalse(MaidTables.isPlaying(untamed), "An untamed maid must remain free");
            helper.assertFalse(MaidTables.isPlaying(far), "A maid outside the search radius must remain free");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptySeatsRemainBotsWithoutEligibleMaids(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            fixture.maid(0, false);
            fixture.start();
            helper.assertTrue(fixture.session().size() == 0, "Idle maids must not be recruited");
            Object bot = fixture.table.driver().playerAt(1);
            fixture.tick();
            helper.assertTrue(fixture.table.driver().playerAt(1) == bot,
                    "An ordinary empty seat must keep its original bot instance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void maidsAreNeverRecruitedAfterMatchStart(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            fixture.start();
            EntityMaid late = fixture.maid(0, true);
            for (int tick = 0; tick < 20; tick++) fixture.tick();
            helper.assertTrue(fixture.session().size() == 0,
                    "The participant roster must remain fixed after the start event");
            helper.assertFalse(MaidTables.isPlaying(late), "A late arrival must wait for the next match");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedFirstBotSeatFallsBackToLaterSafeSeat(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            // Seat 0 is occupied by the human; seat 1 is west of the north-facing table.
            helper.setBlock(8, 1, 10, Blocks.STONE);
            helper.setBlock(8, 2, 10, Blocks.STONE);
            fixture.start();
            helper.assertTrue(fixture.session().size() == 1 && fixture.session().contains(maid.getUUID()),
                    "An obstructed first bot seat must not prevent joining at a later safe seat");
            helper.assertTrue(fixture.session().name(1) == null,
                    "The obstructed seat must remain an ordinary bot seat");
            helper.assertTrue(maid.getName().getString().equals(fixture.session().name(2)),
                    "The maid must occupy the next safe bot seat");
            helper.assertTrue(MaidTables.isPlaying(maid), "The safely seated maid must be held for the match");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cancelReleasesMaidsWithoutFavorabilityReward(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            int before = maid.getFavorability();
            fixture.start();
            helper.assertTrue(MaidTables.isPlaying(maid) && maid.isMaidInSittingPose(),
                    "The recruited maid must stay seated during the match");
            fixture.table.endGame();
            helper.assertTrue(fixture.session().size() == 0, "Ending an unfinished match must clear its roster");
            helper.assertFalse(MaidTables.isPlaying(maid), "Cancellation must clear the maid's table marker");
            helper.assertFalse(maid.isMaidInSittingPose(), "Cancellation must release the sitting pose");
            helper.assertTrue(maid.getFavorability() == before,
                    "An interrupted match must not grant a completed-game reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveLoadPreservesTheSameMaidAndSeat(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            fixture.start();
            String name = fixture.session().name(1);
            CompoundTag savedTable = fixture.table.saveWithoutMetadata();
            fixture.table.load(savedTable);
            helper.assertTrue(fixture.session().size() == 1 && fixture.session().contains(maid.getUUID()),
                    "Table NBT must preserve the participant UUID");
            helper.assertTrue(name.equals(fixture.session().name(1)),
                    "Table NBT must preserve the maid name at the same seat");
            fixture.tick();
            helper.assertTrue(MaidTables.isPlaying(maid) && fixture.session().contains(maid.getUUID()),
                    "The restored participant must remain associated after the next server tick");
            helper.assertTrue(fixture.table.driver().playerAt(1) instanceof StupidActiveBot,
                    "A restored maid seat must continue to use the upstream bot");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copiedTableCannotStealTheOriginalMaids(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            fixture.start();
            CompoundTag savedTable = fixture.table.saveWithoutMetadata();
            MahjongTableBlockEntity copy = new MahjongTableBlockEntity(
                    fixture.table.getBlockPos().offset(6, 0, 0), fixture.table.getBlockState());
            copy.setLevel(helper.getLevel());
            copy.load(savedTable);
            helper.assertTrue(((MaidTableAccess) copy).mahjongmaid$session().size() == 0,
                    "A table loaded at another position must reject the old participant identity");
            helper.assertTrue(fixture.session().contains(maid.getUUID()) && MaidTables.isPlaying(maid),
                    "Loading a copied table must leave the original roster and maid intact");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changingTaskDuringDealingReleasesOnlyThatMaid(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            EntityMaid remaining = fixture.maid(1, true);
            fixture.start();
            helper.assertTrue(fixture.table.driver().currentPhase() instanceof MatchPhase.Dealing,
                    "This regression test must leave the maid during the dealing animation");
            Object bot = fixture.table.driver().playerAt(1);
            int before = maid.getFavorability();
            maid.setTask(new TaskIdle());
            fixture.tick();
            helper.assertFalse(fixture.session().contains(maid.getUUID()),
                    "A task change must remove the maid even during the initial dealing animation");
            helper.assertFalse(MaidTables.isPlaying(maid), "Changing task must clear the table hold");
            helper.assertFalse(maid.isMaidInSittingPose(), "Changing task must release the sitting pose");
            helper.assertTrue(maid.getFavorability() == before, "Leaving during dealing must not grant favorability");
            helper.assertTrue(fixture.session().contains(remaining.getUUID()),
                    "The other participant must continue playing");
            helper.assertTrue(fixture.table.driver().playerAt(1) == bot,
                    "Releasing a maid must preserve the original bot's decision state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void completedMatchRewardsOnceAndHonorsCooldown(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0)) {
            EntityMaid maid = fixture.maid(0, true);
            int before = maid.getFavorability();
            fixture.start();
            finishBotMatch(helper, fixture);
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 2,
                    "Completing a real bot match must grant exactly two enjoyment points");
            helper.assertTrue(fixture.session().size() == 0 && !MaidTables.isPlaying(maid),
                    "A completed match must release its participants");
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 2,
                    "Observing a completed match twice must not duplicate its reward");
            fixture.table.endGame();
            fixture.start();
            helper.assertTrue(fixture.session().contains(maid.getUUID()),
                    "The same maid must be able to join the next match");
            finishBotMatch(helper, fixture);
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 2,
                    "A second completion during the favorability cooldown must not grant extra points");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void endingImmediatelyAfterCompletionStillRewardsOnce(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0)) {
            EntityMaid maid = fixture.maid(0, true);
            int before = maid.getFavorability();
            fixture.start();
            finishBotMatch(helper, fixture);
            // Deliberately do not tick the session between engine completion and ending the table.
            fixture.table.endGame();
            helper.assertTrue(maid.getFavorability() == before + 2,
                    "Ending a terminal table before its next tick must still grant the completion reward");
            helper.assertTrue(fixture.session().size() == 0 && !MaidTables.isPlaying(maid),
                    "Immediate table ending must release the completed match's maid");
            fixture.table.endGame();
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 2,
                    "Repeated ending and ticking must not duplicate the completion reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ownerFinishingAboveMaidGrantsOwnerWinBonus(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1)) {
            EntityMaid maid = fixture.maid(0, true);
            int before = maid.getFavorability();
            fixture.start();
            helper.assertTrue(maid.getName().getString().equals(fixture.session().name(1)),
                    "The owner-bonus fixture requires the maid at seat 1 and its owner at seat 0");
            // Automate only the test owner's inputs while preserving its human seat UUID.
            fixture.table.driver().replacePlayer(0, StupidActiveBot.humanLike());
            finishBotMatch(helper, fixture);
            var players = fixture.table.driver().match().currentRound().orElseThrow().players();
            // With seed 123456789 the actual engine finishes at [25000, 23000, 41000, 11000].
            helper.assertTrue(players.get(0).points() > players.get(1).points(),
                    "The deterministic match must actually exercise the owner's higher-score outcome");
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 6,
                    "Finishing below her owner must grant two enjoyment points plus four owner-win points");
            fixture.session().tick();
            helper.assertTrue(maid.getFavorability() == before + 6,
                    "The owner-win bonus must not be awarded twice for the same match");
        }
        helper.succeed();
    }

    private static void finishBotMatch(GameTestHelper helper, Fixture fixture) {
        var driver = fixture.table.driver();
        driver.setAnimationsEnabled(false);
        for (int step = 0; step < 20000 && !(driver.currentPhase() instanceof MatchPhase.MatchEnded); step++) {
            if (driver.currentPhase() instanceof MatchPhase.RoundEnded
                    || driver.currentPhase() instanceof MatchPhase.BetweenRounds) {
                driver.advanceRound(false, 0);
            } else {
                driver.advance(10);
            }
        }
        helper.assertTrue(driver.currentPhase() instanceof MatchPhase.MatchEnded,
                "The real upstream bots must reach the end of the match within the step limit");
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final MahjongTableBlockEntity table;
        private final List<UUID> humans = new ArrayList<>();
        private final List<EntityMaid> maids = new ArrayList<>();

        private Fixture(GameTestHelper helper, int humanCount) {
            this(helper, humanCount, RuleSetPreset.MAHJONG_SOUL_4P);
        }

        private Fixture(GameTestHelper helper, int humanCount, RuleSetPreset preset) {
            this.helper = helper;
            // The template is 21 x 5 x 21, containing all nearby and out-of-range candidates.
            for (int x = 0; x < 21; x++) {
                for (int z = 0; z < 21; z++) helper.setBlock(x, 0, z, Blocks.STONE);
            }
            var block = RiichiMahjongForgeMod.MAHJONG_TABLE.get();
            var state = block.defaultBlockState();
            helper.setBlock(TABLE, state);
            block.setPlacedBy(helper.getLevel(), helper.absolutePos(TABLE), state, null, ItemStack.EMPTY);
            helper.assertTrue(helper.getBlockEntity(TABLE) instanceof MahjongTableBlockEntity,
                    "The registered mahjong table must create its master block entity");
            table = (MahjongTableBlockEntity) helper.getBlockEntity(TABLE);
            helper.assertTrue(table instanceof MaidTableAccess, "The table integration mixin must be applied");
            table.selectPreset(preset);
            for (int seat = 0; seat < humanCount; seat++) {
                UUID human = UUID.randomUUID();
                humans.add(human);
                helper.assertTrue(table.claimSeat(seat, human), "Human seat " + seat + " must be claimable");
            }
        }

        private EntityMaid maid(int index, boolean mahjongTask) {
            EntityMaid maid = InitEntities.MAID.get().create(helper.getLevel());
            helper.assertTrue(maid != null, "The registered maid entity must be creatable");
            maid.setTame(true);
            maid.setOwnerUUID(humans.isEmpty() ? UUID.randomUUID() : humans.get(0));
            maid.setCustomName(Component.literal("Mahjong test maid " + index));
            var task = mahjongTask ? TaskManager.findTask(MahjongTask.UID).orElseThrow(
                    () -> new IllegalStateException("The TLM extension must register the mahjong task"))
                    : TaskManager.getIdleTask();
            if (mahjongTask) {
                helper.assertTrue(task.getIcon().is(RiichiMahjongForgeMod.MAHJONG_TABLE_ITEM.get()),
                        "The registered mahjong task must display the Riichi Mahjong table item");
            }
            maid.setTask(task);
            Vec3 position = helper.absoluteVec(new Vec3(7.5 + index * 1.5, 1, 5.5));
            maid.moveTo(position.x, position.y, position.z, 0, 0);
            maid.setPersistenceRequired();
            helper.assertTrue(helper.getLevel().addFreshEntity(maid), "The test maid must enter the server level");
            maids.add(maid);
            return maid;
        }

        private TableMaidSession session() {
            return ((MaidTableAccess) table).mahjongmaid$session();
        }

        private void start() {
            helper.assertTrue(table.tryStartMatch(123456789L) == MahjongTableBlockEntity.StartMatchResult.STARTED,
                    "The table's ordinary match start must succeed");
        }

        private void tick() {
            MahjongTableBlockEntity.serverTicker().tick(helper.getLevel(), table.getBlockPos(),
                    table.getBlockState(), table);
        }

        @Override
        public void close() {
            table.endGame();
            maids.forEach(EntityMaid::discard);
        }
    }
}
