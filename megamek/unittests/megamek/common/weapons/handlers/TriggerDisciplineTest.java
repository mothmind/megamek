/*
 * Copyright (C) 2026 The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPL),
 * version 3 or (at your option) any later version,
 * as published by the Free Software Foundation.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * A copy of the GPL should have been included with this project;
 * if not, see <https://www.gnu.org/licenses/>.
 */
package megamek.common.weapons.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Vector;

import megamek.common.Report;
import megamek.common.compute.Compute;
import megamek.common.options.OptionsConstants;
import megamek.common.rolls.Roll;
import megamek.common.units.Crew;
import megamek.common.units.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Tests for Trigger Discipline ({@link UltraWeaponHandler#triggerDisciplineAvertsJam}), the Dead Reckoning house SPA
 * that lets a gunner ease off a volley to keep a Rotary or Ultra AC from jamming.
 */
class TriggerDisciplineTest {

    private static final int GUNNERY = 4;
    private static final int VOLLEY = 6;
    /** A Gunnery 4 pilot catching a six-shot burst needs a 7 or better. */
    private static final int TARGET = UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, VOLLEY);

    private Roll rollOf(int value) {
        Roll roll = mock(Roll.class);
        when(roll.getIntValue()).thenReturn(value);
        lenient().when(roll.getReport()).thenReturn(String.valueOf(value));
        return roll;
    }

    private Entity gunner(boolean hasAbility) {
        Entity entity = mock(Entity.class);
        Crew crew = mock(Crew.class);
        lenient().when(crew.getGunnery()).thenReturn(GUNNERY);
        lenient().when(entity.getCrew()).thenReturn(crew);
        lenient().when(entity.hasAbility(OptionsConstants.GUNNERY_TRIGGER_DISCIPLINE)).thenReturn(hasAbility);
        return entity;
    }

    private boolean attempt(Entity attacker, int shotsFired, int rolled, Vector<Report> reports) {
        Roll check = rollOf(rolled);
        try (MockedStatic<Compute> mockedCompute = mockStatic(Compute.class)) {
            mockedCompute.when(() -> Compute.rollD6(2)).thenReturn(check);
            return UltraWeaponHandler.triggerDisciplineAvertsJam(attacker, shotsFired, 1, reports);
        }
    }

    @Test
    @DisplayName("a gunner without the ability never eases off, and never rolls for it")
    void withoutTheAbilityNothingHappens() {
        Vector<Report> reports = new Vector<>();
        assertFalse(attempt(gunner(false), VOLLEY, 12, reports));
        assertTrue(reports.isEmpty(), "no report should be written when the pilot lacks the ability");
    }

    @Test
    @DisplayName("meeting the target number averts the jam")
    void meetingTheTargetAvertsTheJam() {
        Vector<Report> reports = new Vector<>();
        assertTrue(attempt(gunner(true), VOLLEY, TARGET, reports), "rolling exactly the target must succeed");
        assertEquals(1, reports.size(), "the check should be reported");
    }

    @Test
    @DisplayName("falling short of the target lets the jam stand")
    void fallingShortLetsTheJamStand() {
        Vector<Report> reports = new Vector<>();
        assertFalse(attempt(gunner(true), VOLLEY, TARGET - 1, reports), "one under the target must fail");
        assertEquals(1, reports.size(), "the failed check should still be reported");
    }

    @Test
    @DisplayName("a single-shot volley cannot be eased any further")
    void aSingleShotVolleyAlwaysJams() {
        Vector<Report> reports = new Vector<>();
        assertFalse(attempt(gunner(true), 1, 12, reports), "there is no shot left to give up");
        assertTrue(reports.isEmpty(), "no roll should be made for a volley that cannot shrink");
    }

    @Test
    @DisplayName("a better gunner eases off more often")
    void betterGunnersNeedLess() {
        Entity crack = mock(Entity.class);
        Crew crackCrew = mock(Crew.class);
        lenient().when(crackCrew.getGunnery()).thenReturn(0);
        lenient().when(crack.getCrew()).thenReturn(crackCrew);
        lenient().when(crack.hasAbility(OptionsConstants.GUNNERY_TRIGGER_DISCIPLINE)).thenReturn(true);

        Vector<Report> reports = new Vector<>();
        int wouldFailForGunneryFour = TARGET - 1;
        assertTrue(attempt(crack, VOLLEY, wouldFailForGunneryFour, reports),
              "a roll that a Gunnery 4 pilot misses should still save a Gunnery 0 pilot's gun");
    }

    @Test
    @DisplayName("a heavier burst is harder to catch")
    void targetScalesWithVolleySize() {
        assertEquals(GUNNERY + 1, UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 2), "two shots");
        assertEquals(GUNNERY + 2, UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 4), "four shots");
        assertEquals(GUNNERY + 3, UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 6), "six shots");
        // Odd volleys round down, so three shots are no harder than two.
        assertEquals(GUNNERY + 1, UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 3), "three shots");
        assertEquals(GUNNERY + 2, UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 5), "five shots");
    }

    @Test
    @DisplayName("a two-shot Ultra volley is the easiest to catch")
    void twoShotVolleyUsesTheLowestTarget() {
        Vector<Report> reports = new Vector<>();
        int twoShotTarget = UltraWeaponHandler.triggerDisciplineTarget(GUNNERY, 2);
        assertTrue(attempt(gunner(true), 2, twoShotTarget, reports), "exactly the target must succeed");
        assertFalse(attempt(gunner(true), 2, twoShotTarget - 1, new Vector<>()), "one under must fail");
    }

    @Test
    @DisplayName("a pilot with no crew at all is handled without throwing")
    void missingCrewIsSafe() {
        Entity entity = mock(Entity.class);
        lenient().when(entity.hasAbility(OptionsConstants.GUNNERY_TRIGGER_DISCIPLINE)).thenReturn(true);
        lenient().when(entity.getCrew()).thenReturn(null);
        assertFalse(attempt(entity, VOLLEY, 12, new Vector<>()));
        assertFalse(UltraWeaponHandler.triggerDisciplineAvertsJam(null, VOLLEY, 1, new Vector<>()));
    }
}
