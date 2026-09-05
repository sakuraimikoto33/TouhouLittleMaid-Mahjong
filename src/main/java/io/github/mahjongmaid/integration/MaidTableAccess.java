package io.github.mahjongmaid.integration;

import javax.annotation.Nullable;

public interface MaidTableAccess {
    TableMaidSession mahjongmaid$session();

    @Nullable
    default String mahjongmaid$name(int seat) {
        return mahjongmaid$session().name(seat);
    }
}
