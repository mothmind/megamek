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
package megamek.client.bot.princess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import megamek.common.Player;
import megamek.common.board.Coords;
import megamek.common.game.Game;
import megamek.common.units.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SurrenderUtil}. The dice are fixed per test so each case pins one branch of the target-number
 * logic: the hard gates that skip the roll, the modifiers that build the number, and the roll comparison itself.
 */
class SurrenderUtilTest {

    private static final int OWN_INITIAL_BV = 1000;

    private Player me;
    private Player enemy;
    private Game game;
    private List<Entity> entities;
    private Vector<Entity> outOfGame;
    private FixedDiceSurrenderUtil surrenderUtil;

    private static class FixedDiceSurrenderUtil extends SurrenderUtil {
        int roll = 7;
        int rolls = 0;

        @Override
        protected int rollDice() {
            rolls++;
            return roll;
        }
    }

    @BeforeEach
    void setUp() {
        me = mock(Player.class);
        when(me.getId()).thenReturn(1);
        when(me.getName()).thenReturn("Princess");
        when(me.getInitialBV()).thenReturn(OWN_INITIAL_BV);
        enemy = mock(Player.class);
        when(enemy.getId()).thenReturn(2);
        when(enemy.isEnemyOf(me)).thenReturn(true);
        when(me.isEnemyOf(enemy)).thenReturn(true);

        entities = new ArrayList<>();
        outOfGame = new Vector<>();
        game = mock(Game.class);
        when(game.getEntitiesVector()).thenReturn(entities);
        when(game.getOutOfGameEntitiesVector()).thenReturn(outOfGame);
        surrenderUtil = new FixedDiceSurrenderUtil();
    }

    private Entity unit(Player owner, int bv) {
        Entity entity = mock(Entity.class);
        when(entity.getOwner()).thenReturn(owner);
        when(entity.calculateBattleValue()).thenReturn(bv);
        when(entity.getPosition()).thenReturn(new Coords(1, 1));
        return entity;
    }

    private Entity own(int bv) {
        Entity entity = unit(me, bv);
        entities.add(entity);
        return entity;
    }

    private Entity hostile(int bv) {
        Entity entity = unit(enemy, bv);
        entities.add(entity);
        return entity;
    }

    @Test
    void noEnemyOnTheBoardMeansNoCheck() {
        own(100);
        Entity offBoard = hostile(2000);
        when(offBoard.getPosition()).thenReturn(null);
        surrenderUtil.roll = 2;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game));
        assertEquals(0, surrenderUtil.rolls, "no roll without an enemy to surrender to");
    }

    @Test
    void noInitialBvMeansNoCheck() {
        when(me.getInitialBV()).thenReturn(0);
        own(100);
        hostile(2000);
        surrenderUtil.roll = 2;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game));
        assertEquals(0, surrenderUtil.rolls);
    }

    @Test
    void mostlyIntactForceNeverChecks() {
        own(750);
        hostile(5000);
        surrenderUtil.roll = 2;
        assertFalse(surrenderUtil.shouldSurrender(0, me, game), "75% remaining is the no-check line");
        assertEquals(0, surrenderUtil.rolls);
    }

    @Test
    void forceStillAheadNeverChecks() {
        own(200);
        hostile(200);
        surrenderUtil.roll = 2;
        assertFalse(surrenderUtil.shouldSurrender(0, me, game), "equal weight is not losing");
        assertEquals(0, surrenderUtil.rolls);
    }

    @Test
    void neutralCommanderCrushedRollsAgainstSix() {
        own(200);
        hostile(600);
        surrenderUtil.roll = 6;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game), "<25% left, 3:1 outweighed: +4 +2 = TN 6");
        surrenderUtil.roll = 7;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game));
        assertEquals(2, surrenderUtil.rolls);
    }

    @Test
    void lostCommanderAddsOne() {
        own(200);
        hostile(600);
        Entity deadCommander = unit(me, 0);
        when(deadCommander.isCommander()).thenReturn(true);
        outOfGame.add(deadCommander);
        surrenderUtil.roll = 7;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game), "TN 6 + 1 for the lost commander");
    }

    @Test
    void liveCommanderCancelsTheLostCommanderModifier() {
        Entity commander = own(200);
        when(commander.isCommander()).thenReturn(true);
        hostile(600);
        Entity deadCommander = unit(me, 0);
        when(deadCommander.isCommander()).thenReturn(true);
        outOfGame.add(deadCommander);
        surrenderUtil.roll = 7;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game), "a commander is still on the field");
    }

    @Test
    void highestResolveOnlyFoldsOnSnakeEyes() {
        own(200);
        hostile(600);
        Entity deadCommander = unit(me, 0);
        when(deadCommander.isCommander()).thenReturn(true);
        outOfGame.add(deadCommander);
        surrenderUtil.roll = 2;
        assertTrue(surrenderUtil.shouldSurrender(10, me, game), "-5 +4 +2 +1 = TN 2");
        surrenderUtil.roll = 3;
        assertFalse(surrenderUtil.shouldSurrender(10, me, game));
    }

    @Test
    void highestResolveWithoutLostCommanderCannotFold() {
        own(200);
        hostile(600);
        surrenderUtil.roll = 2;
        assertFalse(surrenderUtil.shouldSurrender(10, me, game), "TN 1 is below the dice");
        assertEquals(0, surrenderUtil.rolls);
    }

    @Test
    void lowestResolveCrushedFoldsWithoutRolling() {
        own(200);
        hostile(600);
        Entity deadCommander = unit(me, 0);
        when(deadCommander.isCommander()).thenReturn(true);
        outOfGame.add(deadCommander);
        surrenderUtil.roll = 12;
        assertTrue(surrenderUtil.shouldSurrender(0, me, game), "+5 +4 +2 +1 = TN 12");
        assertEquals(0, surrenderUtil.rolls);
    }

    @Test
    void heavyLossesAndOutweighedTwoToOne() {
        own(400);
        hostile(800);
        surrenderUtil.roll = 3;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game), "<50% left, 2:1: +2 +1 = TN 3");
        surrenderUtil.roll = 4;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game));
    }

    @Test
    void crippledUnitsCountAsLost() {
        own(200);
        Entity crippled = own(600);
        when(crippled.isCrippled(true)).thenReturn(true);
        hostile(600);
        surrenderUtil.roll = 6;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game), "the crippled 600 BV must not count as remaining");
    }

    @Test
    void destroyedUnitsAreIgnored() {
        own(200);
        Entity wreck = own(600);
        when(wreck.isDestroyed()).thenReturn(true);
        hostile(600);
        surrenderUtil.roll = 6;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game));
    }

    @Test
    void alliesCountTowardOurSideOfTheRatio() {
        own(200);
        Player ally = mock(Player.class);
        when(ally.getId()).thenReturn(3);
        when(ally.isEnemyOf(me)).thenReturn(false);
        entities.add(unit(ally, 400));
        hostile(700);
        surrenderUtil.roll = 4;
        assertTrue(surrenderUtil.shouldSurrender(5, me, game), "700 vs 600 own+ally is under 2:1, so +4 only = TN 4");
        surrenderUtil.roll = 5;
        assertFalse(surrenderUtil.shouldSurrender(5, me, game));
    }

    @Test
    void resolveModIsLinearAndClamped() {
        assertEquals(5, SurrenderUtil.calcResolveMod(0));
        assertEquals(0, SurrenderUtil.calcResolveMod(5));
        assertEquals(-5, SurrenderUtil.calcResolveMod(10));
        assertEquals(-5, SurrenderUtil.calcResolveMod(15));
        assertEquals(5, SurrenderUtil.calcResolveMod(-3));
    }

    @Test
    void targetNumberTable() {
        assertEquals(7, SurrenderUtil.calcTargetNumber(5, 0.2, 3.5, true));
        assertEquals(6, SurrenderUtil.calcTargetNumber(5, 0.2, 3.0, false));
        assertEquals(3, SurrenderUtil.calcTargetNumber(5, 0.4, 2.0, false));
        assertEquals(0, SurrenderUtil.calcTargetNumber(5, 0.6, 1.5, false));
        assertEquals(-5, SurrenderUtil.calcTargetNumber(10, 0.6, 1.5, false));
        assertEquals(12, SurrenderUtil.calcTargetNumber(0, 0.1, 4.0, true));
    }
}
