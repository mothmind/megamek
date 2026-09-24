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
package megamek.common.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import megamek.common.options.OptionsConstants;
import megamek.common.options.PilotOptions;
import megamek.common.units.Entity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Drop Kick Guru, the Dead Reckoning house SPA that makes Death From Above survivable enough to be worth
 * attempting.
 */
class DropKickGuruTest {

    private Entity pilot(boolean hasAbility) {
        Entity entity = mock(Entity.class);
        lenient().when(entity.hasAbility(OptionsConstants.PILOT_DROP_KICK_GURU)).thenReturn(hasAbility);
        return entity;
    }

    @Test
    @DisplayName("the ability is recognised only when the pilot actually has it")
    void abilityDetection() {
        assertTrue(DfaAttackAction.isDropKickGuru(pilot(true)));
        assertFalse(DfaAttackAction.isDropKickGuru(pilot(false)));
        assertFalse(DfaAttackAction.isDropKickGuru(null), "a missing attacker must not blow up");
    }

    @Test
    @DisplayName("a guru takes half the self-damage of a normal pilot")
    void selfDamageIsHalved() {
        Entity guru = pilot(true);
        Entity ordinary = pilot(false);

        // A 100 ton mek takes ceil(100/5) = 20 from a successful DFA.
        assertEquals(20, DfaAttackAction.reduceSelfDamageForGuru(ordinary, 20));
        assertEquals(10, DfaAttackAction.reduceSelfDamageForGuru(guru, 20));

        // A 35 ton mek takes 7; halving rounds down.
        assertEquals(7, DfaAttackAction.reduceSelfDamageForGuru(ordinary, 7));
        assertEquals(3, DfaAttackAction.reduceSelfDamageForGuru(guru, 7));
    }

    @Test
    @DisplayName("halving stacks with the Reinforced Legs quirk down to a quarter")
    void stacksWithReinforcedLegs() {
        // The caller applies the quirk first, so the ability sees the already-halved figure.
        int afterQuirk = (int) Math.floor(20 / 2.0);
        assertEquals(5, DfaAttackAction.reduceSelfDamageForGuru(pilot(true), afterQuirk));
    }

    @Test
    @DisplayName("self-damage never goes negative")
    void zeroDamageIsSafe() {
        assertEquals(0, DfaAttackAction.reduceSelfDamageForGuru(pilot(true), 0));
        assertEquals(0, DfaAttackAction.reduceSelfDamageForGuru(pilot(true), 1), "one point halves away");
    }

    @Test
    @DisplayName("the SPA is registered so pilots can actually be given it")
    void abilityIsRegistered() {
        PilotOptions options = new PilotOptions();
        assertTrue(options.getOption(OptionsConstants.PILOT_DROP_KICK_GURU) != null,
              "drop_kick_guru must be a registered pilot option");
        assertFalse(options.booleanOption(OptionsConstants.PILOT_DROP_KICK_GURU), "it must default to off");
    }
}
