package io.github.mahjongmaid.integration;

import java.util.ArrayList;
import java.util.List;

/** Human occupants and disabled seats can never be assigned to maids. */
public final class SeatSelection {
    private SeatSelection() {}

    public static List<Integer> available(List<Boolean> enabled, List<Boolean> occupied, int candidates) {
        if (enabled.size() != occupied.size()) throw new IllegalArgumentException("Mismatched seat lists");
        List<Integer> result = new ArrayList<>();
        int limit = Math.min(3, Math.max(0, candidates));
        for (int i = 0; i < enabled.size() && result.size() < limit; i++) {
            if (enabled.get(i) && !occupied.get(i)) result.add(i);
        }
        return result;
    }
}
