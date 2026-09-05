package io.github.mahjongmaid.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.mojang.datafixers.util.Pair;
import io.github.mahjongmaid.integration.MaidTables;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Waits near a table until the table assigns this maid to a bot seat. */
public final class MahjongTask implements IMaidTask {
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid_mahjong", "mahjong");
    private static final ResourceLocation TABLE_ITEM =
            ResourceLocation.fromNamespaceAndPath("riichi_mahjong", "mahjong_table_new");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        Item table = BuiltInRegistries.ITEM.get(TABLE_ITEM);
        return new ItemStack(table == null || table == Items.AIR ? Items.BAMBOO : table);
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_IDLE.get(), 0.5f);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return new ArrayList<>();
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        return new ArrayList<>();
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return !MaidTables.isPlaying(maid);
    }

    @Override
    public boolean enablePanic(EntityMaid maid) {
        return !MaidTables.isPlaying(maid);
    }

    @Override
    public boolean enableEating(EntityMaid maid) {
        return !MaidTables.isPlaying(maid);
    }
}
