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
package megamek.server.totalWarfare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import megamek.common.Player;
import megamek.common.Report;
import megamek.common.equipment.EquipmentType;
import megamek.common.game.Game;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Pose action on the server: striking the pose, and carrying it onto any kill the posing unit makes
 * before the round ends.
 */
class PoseTest {

    private Game game;

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() {
        game = new Game();
        game.addPlayer(0, new Player(0, "Poser"));
        game.addPlayer(1, new Player(1, "Victim"));
    }

    private Entity mek(int id, int owner) {
        BipedMek mek = new BipedMek();
        mek.setChassis("Test");
        mek.setModel("Mek " + id);
        mek.setWeight(50.0);
        mek.setCrew(new Crew(CrewType.SINGLE));
        mek.setId(id);
        mek.setGame(game);
        mek.setOwnerId(owner);
        game.addEntity(mek);
        return mek;
    }

    @Test
    @DisplayName("striking a pose marks the unit as posing and reports it")
    void strikePoseMarksAndReports() {
        Entity poser = mek(1, 0);

        Report report = MovePathHandler.strikePose(poser);

        assertTrue(poser.isPosing());
        assertEquals(2028, report.messageId);
        assertEquals(poser.getId(), report.subject);
    }

    @Test
    @DisplayName("a kill made while posing is recorded against the victim")
    void killWhilePosingIsRecorded() {
        Entity poser = mek(1, 0);
        Entity victim = mek(2, 1);
        MovePathHandler.strikePose(poser);

        poser.addKill(victim);

        assertEquals(poser.getId(), victim.getKillerId());
        assertTrue(victim.wasKilledByPosingUnit());
    }

    @Test
    @DisplayName("a kill made without posing is an ordinary kill")
    void killWithoutPosingIsOrdinary() {
        Entity killer = mek(1, 0);
        Entity victim = mek(2, 1);

        killer.addKill(victim);

        assertEquals(killer.getId(), victim.getKillerId());
        assertFalse(victim.wasKilledByPosingUnit());
    }

    @Test
    @DisplayName("the pose lasts only for the round it was struck in")
    void poseEndsWithTheRound() {
        Entity poser = mek(1, 0);
        Entity victim = mek(2, 1);
        MovePathHandler.strikePose(poser);

        poser.newRound(2);
        poser.addKill(victim);

        assertFalse(poser.isPosing());
        assertFalse(victim.wasKilledByPosingUnit(), "a kill in the next round does not earn the bonus");
    }
}
