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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.Vector;

import megamek.common.Hex;
import megamek.common.PhysicalResult;
import megamek.common.Player;
import megamek.common.ToHitData;
import megamek.common.actions.DfaAttackAction;
import megamek.common.board.Board;
import megamek.common.board.Coords;
import megamek.common.enums.GamePhase;
import megamek.common.equipment.EquipmentType;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import megamek.common.rolls.PilotingRollData;
import megamek.common.rolls.Roll;
import megamek.common.units.Entity;
import megamek.common.units.Targetable;
import megamek.testUtilities.MMTestUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Landing behaviour for Drop Kick Guru, the Dead Reckoning house SPA.
 *
 * <p>A normal pilot rolls to stay upright after a successful death from above, and simply falls after a missed one.
 * A guru skips the check on a hit and is given one on a miss, which is the whole point of the ability - a botched
 * drop currently means prone in the open with no roll to avoid it.</p>
 */
class DropKickGuruLandingTest {

    private static final int ATTACKER_ID = 5;
    private static final int TARGET_ID = 6;
    private static final Coords ATTACKER_POSITION = new Coords(1, 1);
    private static final Coords TARGET_POSITION = new Coords(1, 2);
    /** Any to-hit number; the roll is chosen relative to it to force a hit or a miss. */
    private static final int TO_HIT = 8;

    private TWGameManager gameManager;
    private Game game;
    private Entity attacker;
    private Entity target;

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        game = new Game();
        game.setBoard(flatBoard(4, 4));

        Player attackingPlayer = new Player(0, "Attacker");
        attackingPlayer.setTeam(1);
        game.addPlayer(0, attackingPlayer);
        Player defendingPlayer = new Player(1, "Defender");
        defendingPlayer.setTeam(2);
        game.addPlayer(1, defendingPlayer);

        attacker = MMTestUtilities.getEntityForUnitTesting("Shadow Hawk SHD-5D", false);
        assertNotNull(attacker, "Attacking unit could not be loaded");
        attacker.setId(ATTACKER_ID);
        attacker.setOwner(attackingPlayer);
        game.addEntity(attacker);
        attacker.setPosition(ATTACKER_POSITION);
        attacker.setFacing(3);
        // The movement phase leaves a DFA attacker one level above the target hex.
        attacker.setElevation(1);
        attacker.setDeployed(true);

        target = MMTestUtilities.getEntityForUnitTesting("Locust LCT-1V", false);
        assertNotNull(target, "Target unit could not be loaded");
        target.setId(TARGET_ID);
        target.setOwner(defendingPlayer);
        game.addEntity(target);
        target.setPosition(TARGET_POSITION);
        target.setDeployed(true);

        game.setPhase(GamePhase.PHYSICAL);

        gameManager = mock(TWGameManager.class);
        doCallRealMethod().when(gameManager).setGame(any());
        doCallRealMethod().when(gameManager).resolvePhysicalAttacks();
        gameManager.setGame(game);
        // A mocked manager skips field initialization; hand it the report vector its constructor would have built.
        setFinalField(gameManager, "mainPhaseReport", new Vector<megamek.common.Report>());
        // The real methods return report vectors that callers merge; a bare mock returns null and blows up.
        org.mockito.Mockito.lenient()
              .when(gameManager.doEntityDisplacement(any(), any(), any(), any()))
              .thenReturn(new Vector<>());
        org.mockito.Mockito.lenient()
              .when(gameManager.doEntityFall(any(), any(), anyInt(), anyInt(), any(), anyBoolean(), anyBoolean()))
              .thenReturn(new Vector<>());
    }

    /** Assigns a field on the mocked manager that its real constructor would have initialized. */
    private static void setFinalField(TWGameManager manager, String name, Object value)
          throws ReflectiveOperationException {
        Field field = TWGameManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static Board flatBoard(int width, int height) {
        Hex[] hexes = new Hex[width * height];
        for (int hex = 0; hex < hexes.length; hex++) {
            hexes[hex] = new Hex();
        }
        return new Board(width, height, hexes);
    }

    private void giveAttackerDropKickGuru() {
        attacker.getCrew().getOptions().getOption(OptionsConstants.PILOT_DROP_KICK_GURU).setValue(true);
    }

    /** Queues a resolved death from above whose roll decides hit or miss, then runs the physical phase. */
    private void resolveDeathFromAbove(boolean hits) throws ReflectiveOperationException {
        DfaAttackAction deathFromAbove = new DfaAttackAction(ATTACKER_ID,
              Targetable.TYPE_ENTITY,
              TARGET_ID,
              TARGET_POSITION);
        attacker.setDisplacementAttack(deathFromAbove);

        PhysicalResult result = new PhysicalResult();
        result.aaa = deathFromAbove;
        result.toHit = new ToHitData(TO_HIT, "test");
        Roll roll = mock(Roll.class);
        int rolled = hits ? 12 : 2;
        org.mockito.Mockito.lenient().when(roll.getIntValue()).thenReturn(rolled);
        org.mockito.Mockito.lenient().when(roll.getReport()).thenReturn(String.valueOf(rolled));
        result.roll = roll;
        result.damage = DfaAttackAction.getDamageFor(attacker, false);

        Vector<PhysicalResult> results = new Vector<>();
        results.add(result);
        Field physicalResults = TWGameManager.class.getDeclaredField("physicalResults");
        physicalResults.setAccessible(true);
        physicalResults.set(gameManager, results);

        gameManager.resolvePhysicalAttacks();
    }

    @Test
    @DisplayName("an ordinary pilot rolls to stay upright after a successful drop")
    void ordinaryPilotChecksAfterAHit() throws ReflectiveOperationException {
        resolveDeathFromAbove(true);

        verify(gameManager).doEntityDisplacement(eq(attacker),
              eq(TARGET_POSITION),
              eq(TARGET_POSITION),
              any(PilotingRollData.class));
    }

    @Test
    @DisplayName("a guru needs no check at all after a successful drop")
    void guruSkipsTheCheckAfterAHit() throws ReflectiveOperationException {
        giveAttackerDropKickGuru();

        resolveDeathFromAbove(true);

        // A null roll is what tells doEntityDisplacement not to queue a piloting check.
        verify(gameManager).doEntityDisplacement(eq(attacker),
              eq(TARGET_POSITION),
              eq(TARGET_POSITION),
              isNull());
    }

    @Test
    @DisplayName("an ordinary pilot simply falls after a missed drop")
    void ordinaryPilotFallsAfterAMiss() throws ReflectiveOperationException {
        resolveDeathFromAbove(false);

        verify(gameManager).doEntityFall(eq(attacker),
              any(Coords.class),
              anyInt(),
              anyInt(),
              any(PilotingRollData.class),
              eq(false),
              eq(false));
    }

    @Test
    @DisplayName("a guru is given a check instead of falling after a missed drop")
    void guruChecksAfterAMiss() throws ReflectiveOperationException {
        giveAttackerDropKickGuru();

        resolveDeathFromAbove(false);

        verify(gameManager, never()).doEntityFall(eq(attacker),
              any(Coords.class),
              anyInt(),
              anyInt(),
              any(PilotingRollData.class),
              eq(false),
              eq(false));
        verify(gameManager).doEntityDisplacement(eq(attacker),
              any(Coords.class),
              any(Coords.class),
              any(PilotingRollData.class));
    }
}
