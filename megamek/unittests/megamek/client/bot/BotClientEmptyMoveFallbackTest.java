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

package megamek.client.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import megamek.client.bot.princess.BehaviorSettings;
import megamek.common.board.BoardLocation;
import megamek.common.board.Coords;
import megamek.common.enums.GamePhase;
import megamek.common.event.player.GamePlayerChatEvent;
import megamek.common.game.Game;
import megamek.common.game.GameTurn;
import megamek.common.moves.MovePath;
import megamek.common.units.BipedMek;
import megamek.common.units.Entity;
import org.junit.jupiter.api.Test;

/**
 * A bot that cannot find a path on its last retry submits an empty move, so the server is never left waiting on a turn
 * that is not answered. That used to happen only when the turn named the unit to move. On an ordinary movement turn
 * the bot chooses the unit, so no unit was named and the bot fell silent, hanging the game. It was seen live when the
 * only unit a beaten force had left was crippled and withdrawing.
 */
class BotClientEmptyMoveFallbackTest {

    private static final int NAMED_ID = 4;
    private static final int FIRST_ELIGIBLE_ID = 9;

    @Test
    void turnThatNamesAUnitUsesThatUnit() {
        Game game = mock(Game.class);
        GameTurn turn = mock(GameTurn.class);
        when(game.getEntity(NAMED_ID)).thenReturn(mock(Entity.class));

        assertEquals(NAMED_ID, BotClient.emptyMoveFallbackId(game, NAMED_ID, turn));
        verify(game, never()).getFirstEntityNum(any());
    }

    @Test
    void ordinaryMovementTurnFallsBackToTheFirstUnitThatMayAct() {
        Game game = mock(Game.class);
        GameTurn turn = mock(GameTurn.class);
        when(game.getFirstEntityNum(turn)).thenReturn(FIRST_ELIGIBLE_ID);
        when(game.getEntity(FIRST_ELIGIBLE_ID)).thenReturn(mock(Entity.class));

        assertEquals(FIRST_ELIGIBLE_ID, BotClient.emptyMoveFallbackId(game, Entity.NONE, turn));
    }

    @Test
    void namedUnitThatNoLongerExistsFallsBackToTheFirstUnitThatMayAct() {
        Game game = mock(Game.class);
        GameTurn turn = mock(GameTurn.class);
        when(game.getEntity(NAMED_ID)).thenReturn(null);
        when(game.getFirstEntityNum(turn)).thenReturn(FIRST_ELIGIBLE_ID);
        when(game.getEntity(FIRST_ELIGIBLE_ID)).thenReturn(mock(Entity.class));

        assertEquals(FIRST_ELIGIBLE_ID, BotClient.emptyMoveFallbackId(game, NAMED_ID, turn));
    }

    @Test
    void noUnitThatMayActGivesNone() {
        Game game = mock(Game.class);
        GameTurn turn = mock(GameTurn.class);
        when(game.getFirstEntityNum(turn)).thenReturn(Entity.NONE);

        assertEquals(Entity.NONE, BotClient.emptyMoveFallbackId(game, Entity.NONE, turn));
    }

    @Test
    void noTurnGivesNone() {
        Game game = mock(Game.class);
        when(game.getFirstEntityNum(null)).thenReturn(Entity.NONE);

        assertEquals(Entity.NONE, BotClient.emptyMoveFallbackId(game, Entity.NONE, null));
    }

    /**
     * The live case end to end: an ordinary movement turn, a path calculation that always comes back empty, and the
     * last retry. The bot must answer the turn with an empty move for the unit that may act.
     */
    @Test
    void lastRetryOnAnOrdinaryMovementTurnSubmitsAnEmptyMove() throws Exception {
        PathlessBotClient bot = new PathlessBotClient();
        BipedMek mek = addMek(bot.getGame());

        boolean answered = runMovementWorker(bot, true);

        assertTrue(answered, "the turn must count as answered");
        assertEquals(List.of(mek.getId()), bot.movedIds, "an empty move must be sent for the unit that may act");
        assertTrue(bot.movedPaths.get(0).getStepVector().isEmpty(), "the move must be empty");
    }

    /** Earlier retries still report failure and send nothing, so the bot gets its normal chance to try again. */
    @Test
    void earlierRetryOnAnOrdinaryMovementTurnSendsNothing() throws Exception {
        PathlessBotClient bot = new PathlessBotClient();
        addMek(bot.getGame());

        boolean answered = runMovementWorker(bot, false);

        assertFalse(answered, "an earlier retry must report failure so the turn is retried");
        assertTrue(bot.movedIds.isEmpty(), "nothing may be sent before the last retry");
    }

    private static BipedMek addMek(Game game) {
        BipedMek mek = new BipedMek();
        mek.setId(FIRST_ELIGIBLE_ID);
        mek.setGame(game);
        mek.setPosition(new Coords(3, 3));
        mek.setDeployed(true);
        game.addEntity(mek);
        game.setPhase(GamePhase.MOVEMENT);
        return mek;
    }

    private static boolean runMovementWorker(BotClient bot, boolean lastAttempt) throws Exception {
        Method worker = BotClient.class.getDeclaredMethod("calculateMyTurnWorker", boolean.class);
        worker.setAccessible(true);
        return (boolean) worker.invoke(bot, lastAttempt);
    }

    /** A bot that never finds a path, owns the current turn, and records the moves it submits instead of sending them. */
    private static final class PathlessBotClient extends BotClient {

        private final GameTurn turn = mock(GameTurn.class);
        private final List<Integer> movedIds = new ArrayList<>();
        private final List<MovePath> movedPaths = new ArrayList<>();

        PathlessBotClient() {
            super("tester", "localhost", 0);
            when(turn.isValidEntity(any(Entity.class), any(Game.class))).thenReturn(true);
        }

        @Override
        public GameTurn getMyTurn() {
            return turn;
        }

        @Override
        public void moveEntity(int id, MovePath md) {
            movedIds.add(id);
            movedPaths.add(md);
        }

        @Override
        public void initialize() {
        }

        @Override
        protected void processChat(GamePlayerChatEvent gamePlayerChatEvent) {
        }

        @Override
        protected void initMovement() {
        }

        @Override
        protected void initFiring() {
        }

        @Override
        protected MovePath calculateMoveTurn() {
            return null;
        }

        @Override
        protected void calculateFiringTurn() {
        }

        @Override
        protected void calculateDeployment() {
        }

        @Override
        public void setBehaviorSettings(BehaviorSettings behaviorSettings) {
        }

        @Override
        protected PhysicalOption calculatePhysicalTurn() {
            return null;
        }

        @Override
        protected void calculatePreEndDeclarationsTurn() {
        }

        @Override
        protected void calculateInfantryVsInfantryCombatTurn() {
        }

        @Override
        protected MovePath continueMovementFor(Entity entity) {
            return null;
        }

        @Override
        protected Vector<BoardLocation> calculateArtyAutoHitHexes() {
            return new Vector<>();
        }

        @Override
        protected void checkMorale() {
        }

        @Override
        protected void postMovementProcessing() {
        }
    }
}
