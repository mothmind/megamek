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
package megamek.common.actions.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import megamek.common.ToHitData;
import megamek.common.equipment.EquipmentType;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import megamek.common.options.PilotOptions;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Mek;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Showboat, the house SPA that gives a pilot -1 to-hit with weapons in a round their Mek strikes a pose.
 */
class ShowboatTest {

    private final Game game = new Game();

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    private Mek mek(boolean showboat, boolean posing) {
        BipedMek mek = new BipedMek();
        mek.setChassis("Test");
        mek.setModel("Showboat");
        mek.setWeight(50.0);
        mek.setCrew(new Crew(CrewType.SINGLE));
        mek.setGame(game);
        mek.getCrew().getOptions().getOption(OptionsConstants.GUNNERY_SHOWBOAT).setValue(showboat);
        mek.setPosing(posing);
        return mek;
    }

    private int crewModifier(Mek mek) {
        return ComputeAttackerToHitMods.compileCrewToHitMods(game, mek, new ToHitData(), null).getValue();
    }

    @Test
    @DisplayName("a showboat who poses shoots at -1")
    void posingShowboatGetsTheBonus() {
        Mek mek = mek(true, true);

        assertTrue(ComputeAttackerToHitMods.isShowboating(mek));
        ToHitData toHit = ComputeAttackerToHitMods.compileCrewToHitMods(game, mek, new ToHitData(), null);
        assertEquals(-1, toHit.getValue());
        assertTrue(toHit.getDesc().contains("Showboat"), "the modifier must be named in the to-hit breakdown");
    }

    @Test
    @DisplayName("a showboat who did not pose gets nothing")
    void showboatWithoutPoseGetsNothing() {
        Mek mek = mek(true, false);

        assertFalse(ComputeAttackerToHitMods.isShowboating(mek));
        assertEquals(0, crewModifier(mek));
    }

    @Test
    @DisplayName("posing without the ability gets nothing")
    void poseWithoutAbilityGetsNothing() {
        Mek mek = mek(false, true);

        assertFalse(ComputeAttackerToHitMods.isShowboating(mek));
        assertEquals(0, crewModifier(mek));
    }

    @Test
    @DisplayName("the SPA is registered so pilots can actually be given it")
    void abilityIsRegistered() {
        PilotOptions options = new PilotOptions();
        assertNotNull(options.getOption(OptionsConstants.GUNNERY_SHOWBOAT), "showboat must be a registered pilot option");
        assertFalse(options.booleanOption(OptionsConstants.GUNNERY_SHOWBOAT), "it must default to off");
    }
}
