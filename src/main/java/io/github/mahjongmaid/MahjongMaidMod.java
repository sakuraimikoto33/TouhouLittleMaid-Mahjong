package io.github.mahjongmaid;

import io.github.mahjongmaid.integration.MaidTables;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;

@Mod(MahjongMaidMod.ID)
public final class MahjongMaidMod {
    public static final String ID = "touhou_little_maid_mahjong";

    public MahjongMaidMod() {
        MaidTables.initializeFavorability();
        NeoForge.EVENT_BUS.addListener(MaidTables::onMaidTick);
    }
}
