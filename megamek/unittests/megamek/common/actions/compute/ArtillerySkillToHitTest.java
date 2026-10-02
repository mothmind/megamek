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
 *
 * NOTICE: The MegaMek organization is a non-profit group of volunteers
 * creating free software for the BattleTech community.
 *
 * MechWarrior, BattleMech, `Mech and AeroTech are registered trademarks
 * of The Topps Company, Inc. All Rights Reserved.
 *
 * Catalyst Game Labs and the Catalyst Game Labs logo are trademarks of
 * InMediaRes Productions, LLC.
 *
 * MechWarrior Copyright Microsoft Corporation. MegaMek was created under
 * Microsoft's "Game Content Usage Rules"
 * <https://www.xbox.com/en-US/developers/rules> and it is not endorsed by or
 * affiliated with Microsoft.
 */
package megamek.common.actions.compute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import megamek.common.equipment.WeaponType;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * With the optional Artillery skill in play, indirect artillery rolls against it, but direct-fire artillery uses
 * Gunnery like any other weapon attack (TO:AR p.153).
 */
class ArtillerySkillToHitTest {

    private Game game;
    private WeaponType artillery;

    @BeforeEach
    void setUp() {
        game = new Game();
        artillery = mock(WeaponType.class);
        when(artillery.hasFlag(WeaponType.F_ARTILLERY)).thenReturn(true);
    }

    private void artillerySkill(boolean inPlay) {
        game.getOptions().getOption(OptionsConstants.RPG_ARTILLERY_SKILL).setValue(inPlay);
    }

    @Test
    void indirectArtilleryUsesTheArtillerySkill() {
        artillerySkill(true);
        assertTrue(ComputeToHit.usesArtillerySkill(game, artillery, false));
    }

    @Test
    void directFireArtilleryUsesGunnery() {
        artillerySkill(true);
        assertFalse(ComputeToHit.usesArtillerySkill(game, artillery, true));
    }

    @Test
    void withoutTheOptionArtilleryUsesGunnery() {
        artillerySkill(false);
        assertFalse(ComputeToHit.usesArtillerySkill(game, artillery, false));
    }

    @Test
    void otherWeaponsNeverUseTheArtillerySkill() {
        artillerySkill(true);
        assertFalse(ComputeToHit.usesArtillerySkill(game, mock(WeaponType.class), false));
    }
}
