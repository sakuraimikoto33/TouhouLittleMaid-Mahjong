package io.github.mahjongmaid.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeatSelectionTest {
    private static final List<Boolean> FOUR_OPEN_SEATS = List.of(true, true, true, true);

    @ParameterizedTest(name = "{0} human players leave maid seats {2}")
    @MethodSource("humanOccupancy")
    void preservesEveryHumanSeatAndCapsParticipationAtThree(
            int humans, List<Boolean> occupied, List<Integer> expected) {
        assertEquals(expected, SeatSelection.available(FOUR_OPEN_SEATS, occupied, 8));
    }

    private static Stream<Arguments> humanOccupancy() {
        return Stream.of(
                Arguments.of(0, List.of(false, false, false, false), List.of(0, 1, 2)),
                Arguments.of(1, List.of(false, false, true, false), List.of(0, 1, 3)),
                Arguments.of(2, List.of(true, false, true, false), List.of(1, 3)),
                Arguments.of(3, List.of(true, true, false, true), List.of(2)),
                Arguments.of(4, List.of(true, true, true, true), List.of()));
    }

    @Test
    void skipsClosedSeatsWithoutShiftingTheRemainingSeatIndices() {
        assertEquals(List.of(2), SeatSelection.available(
                List.of(false, true, true, false),
                List.of(false, true, false, false), 3));
    }

    @Test
    void assignsOnlyAsManySeatsAsThereAreEligibleMaids() {
        assertEquals(List.of(1, 2), SeatSelection.available(
                FOUR_OPEN_SEATS, List.of(true, false, false, false), 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void noEligibleMaidsLeaveAllBotsInPlace(int candidates) {
        assertEquals(List.of(), SeatSelection.available(
                FOUR_OPEN_SEATS, List.of(false, false, false, false), candidates));
    }

    @Test
    void rejectsMismatchedSeatLayouts() {
        assertThrows(IllegalArgumentException.class, () -> SeatSelection.available(
                FOUR_OPEN_SEATS, List.of(false, false, false), 3));
    }
}
