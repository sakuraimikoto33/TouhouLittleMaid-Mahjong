package io.github.mahjongmaid;

import io.github.mahjongmaid.integration.MaidTables;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

@Mod(MahjongMaidMod.ID)
public final class MahjongMaidMod {
    public static final String ID = "touhou_little_maid_mahjong";

    public MahjongMaidMod() {
        MaidTables.initializeFavorability();
        MinecraftForge.EVENT_BUS.addListener(MaidTables::onMaidTick);
    }
}
