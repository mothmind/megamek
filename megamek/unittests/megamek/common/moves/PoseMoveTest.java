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
package megamek.common.moves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import megamek.common.GameBoardTestCase;
import megamek.common.enums.MoveStepType;
import megamek.common.units.BipedMek;
import megamek.common.units.ConvInfantry;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.EntityMovementType;
import megamek.common.units.Mek;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Pose action: a Mek gives up its whole movement to strike a pose, which the movement rules treat as
 * standing still.
 */
class PoseMoveTest extends GameBoardTestCase {

    static {
        initializeBoard("OPEN_GROUND", """
              size 1 2
              hex 0101 0 "" ""
              hex 0102 0 "" ""
              end""");
    }

    @BeforeEach
    void useOpenGround() {
        setBoard("OPEN_GROUND");
    }

    private Mek mek() {
        BipedMek mek = new BipedMek();
        mek.setChassis("Test");
        mek.setModel("Poser");
        mek.setWeight(50.0);
        mek.setOriginalWalkMP(5);
        mek.setOriginalJumpMP(0);
        mek.autoSetInternal();
        for (int loc = 0; loc < mek.locations(); loc++) {
            mek.initializeArmor(10, loc);
            if (mek.hasRearArmor(loc)) {
                mek.initializeRearArmor(5, loc);
            }
        }
        mek.setCrew(new Crew(CrewType.SINGLE));
        return mek;
    }

    @Test
    @DisplayName("a pose on its own is a legal move that spends nothing and counts as standing still")
    void poseAloneIsStandingStill() {
        MovePath path = getMovePathFor(mek(), MoveStepType.POSE);

        assertTrue(path.isMoveLegal(), "posing in place must be allowed");
        assertEquals(0, path.getMpUsed(), "a pose costs no movement");
        assertEquals(EntityMovementType.MOVE_NONE, path.getLastStepMovementType(),
              "a posing unit has not moved, so to-hit treats it as stationary");
    }

    @Test
    @DisplayName("a pose cannot follow movement")
    void poseAfterMovingIsIllegal() {
        MovePath path = getMovePathFor(mek(), MoveStepType.FORWARDS, MoveStepType.POSE);

        assertFalse(path.isMoveLegal(), "the pose replaces movement, it does not end it");
    }

    @Test
    @DisplayName("nothing can follow a pose")
    void movingAfterPoseIsIllegal() {
        MovePath path = getMovePathFor(mek(), MoveStepType.POSE, MoveStepType.FORWARDS);

        assertFalse(path.isMoveLegal(), "a unit that posed has given up its movement");
    }

    @Test
    @DisplayName("a prone Mek cannot pose")
    void proneMekCannotPose() {
        Mek mek = mek();
        mek.setProne(true);

        assertFalse(mek.canPose());
        assertFalse(getMovePathFor(mek, MoveStepType.POSE).isMoveLegal());
    }

    @Test
    @DisplayName("a shut-down Mek cannot pose")
    void shutDownMekCannotPose() {
        Mek mek = mek();
        mek.setShutDown(true);

        assertFalse(mek.canPose());
    }

    @Test
    @DisplayName("a Mek whose pilot is out cannot pose")
    void unconsciousPilotCannotPose() {
        Mek mek = mek();
        mek.getCrew().setUnconscious(true);

        assertFalse(mek.canPose());
    }

    @Test
    @DisplayName("only Meks pose")
    void onlyMeksPose() {
        assertTrue(mek().canPose());
        assertFalse(new ConvInfantry().canPose(), "units other than Meks have no pose");
    }
}
