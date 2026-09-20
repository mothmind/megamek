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

import java.text.DecimalFormat;

import megamek.common.Player;
import megamek.common.compute.Compute;
import megamek.common.game.Game;
import megamek.common.units.Entity;
import megamek.logging.MMLogger;

/**
 * Decides, once per round, whether a Princess force that is clearly beaten offers surrender. The check is a 2d6 roll
 * against a target number built from the bot's resolve, the share of its own Battle Value it has lost, how badly the
 * enemy outweighs its side, and whether its commander unit is gone. A force that is still ahead, or has lost less
 * than a quarter of its strength, never rolls.
 */
public class SurrenderUtil {
    private static final MMLogger logger = MMLogger.create(SurrenderUtil.class);
    private static final DecimalFormat DEC_FORMAT = new DecimalFormat("0.00");

    /** Single tuning knob: +1 shifts every situation one step toward surrender. */
    static final int BASE_TARGET_NUMBER = 0;
    /** No check at all while this share of the force's initial BV is still fighting. */
    static final double NO_CHECK_REMAINING_THRESHOLD = 0.75;
    static final double HEAVY_LOSSES_THRESHOLD = 0.50;
    static final double CRUSHING_LOSSES_THRESHOLD = 0.25;
    static final int HEAVY_LOSSES_MODIFIER = 2;
    static final int CRUSHING_LOSSES_MODIFIER = 4;
    static final double OUTWEIGHED_RATIO = 2.0;
    static final double OVERWHELMED_RATIO = 3.0;
    static final int OUTWEIGHED_MODIFIER = 1;
    static final int OVERWHELMED_MODIFIER = 2;
    static final int COMMANDER_LOST_MODIFIER = 1;
    /** Slider midpoint; each step away from it is one point on the target number. */
    static final int NEUTRAL_RESOLVE_INDEX = 5;

    public SurrenderUtil() {

    }

    /**
     * Runs the surrender check for the given bot player.
     *
     * @param resolveIndex The index of the resolve setting in {@link BehaviorSettings} (0 folds early, 10 fights on).
     * @param player       The {@link Player} of the Princess bot.
     * @param game         The current {@link Game}
     *
     * @return TRUE if the force offers surrender this round.
     */
    public boolean shouldSurrender(int resolveIndex, Player player, Game game) {
        StringBuilder logMsg = new StringBuilder("Surrender check for ").append(player.getName());

        try {
            ForceState state = assessForce(player, game);
            logMsg.append("\n\tOwn BV (not crippled) ").append(state.ownBv)
                  .append(" of initial ").append(state.initialBv)
                  .append(", friendly ").append(state.friendlyBv)
                  .append(", enemy ").append(state.enemyBv)
                  .append(", commander lost ").append(state.commanderLost);

            if (!state.enemyOnBoard) {
                logMsg.append("\n\tNo enemy on the board; no check.");
                return false;
            }
            if (state.initialBv <= 0) {
                logMsg.append("\n\tNo initial BV recorded; no check.");
                return false;
            }
            double remaining = (double) state.ownBv / state.initialBv;
            logMsg.append("\n\tRemaining = ").append(DEC_FORMAT.format(remaining));
            if (remaining >= NO_CHECK_REMAINING_THRESHOLD) {
                logMsg.append("; force is still mostly intact; no check.");
                return false;
            }
            if (state.enemyBv <= state.friendlyBv) {
                logMsg.append("\n\tOur side still outweighs the enemy; no check.");
                return false;
            }
            double ratio = (double) state.enemyBv / state.friendlyBv;
            logMsg.append("\n\tEnemy : friendly BV ratio = ").append(DEC_FORMAT.format(ratio));

            int targetNumber = calcTargetNumber(resolveIndex, remaining, ratio, state.commanderLost);
            logMsg.append("\n\tTarget Number = ").append(targetNumber);

            if (targetNumber < 2) {
                logMsg.append("; cannot be reached.");
                return false;
            }
            if (targetNumber >= 12) {
                logMsg.append("; automatic surrender.");
                return true;
            }

            int roll = rollDice();
            logMsg.append("\n\tRolled ").append(roll);
            boolean surrender = roll <= targetNumber;
            logMsg.append(surrender ? "; offering surrender." : "; fighting on.");
            return surrender;
        } finally {
            logger.info(logMsg.toString());
        }
    }

    static int calcTargetNumber(int resolveIndex, double remaining, double ratio, boolean commanderLost) {
        int targetNumber = BASE_TARGET_NUMBER + calcResolveMod(resolveIndex);
        if (remaining < CRUSHING_LOSSES_THRESHOLD) {
            targetNumber += CRUSHING_LOSSES_MODIFIER;
        } else if (remaining < HEAVY_LOSSES_THRESHOLD) {
            targetNumber += HEAVY_LOSSES_MODIFIER;
        }
        if (ratio >= OVERWHELMED_RATIO) {
            targetNumber += OVERWHELMED_MODIFIER;
        } else if (ratio >= OUTWEIGHED_RATIO) {
            targetNumber += OUTWEIGHED_MODIFIER;
        }
        if (commanderLost) {
            targetNumber += COMMANDER_LOST_MODIFIER;
        }
        return targetNumber;
    }

    /**
     * Linear rather than {@code MoraleUtil.calcBehaviorMod}, which flattens indices 4 to 6 into one value and would
     * make neighbouring resolve settings indistinguishable.
     *
     * @param resolveIndex The resolve slider index, 0 to 10.
     *
     * @return The target number modifier, +5 at index 0 down to -5 at index 10.
     */
    static int calcResolveMod(int resolveIndex) {
        return NEUTRAL_RESOLVE_INDEX - Math.clamp(resolveIndex, 0, 10);
    }

    /**
     * @return The result of a 2d6 roll from {@link Compute#d6(int)}
     */
    protected int rollDice() {
        return Compute.d6(2);
    }

    private ForceState assessForce(Player player, Game game) {
        ForceState state = new ForceState();
        state.initialBv = player.getInitialBV();
        boolean ownCommanderAlive = false;

        for (Entity entity : game.getEntitiesVector()) {
            if ((entity.getOwner() == null) || entity.isDestroyed() || entity.isDoomed()) {
                continue;
            }

            if (entity.getOwner().isEnemyOf(player)) {
                state.enemyBv += entity.calculateBattleValue();
                if ((entity.getPosition() != null) && !entity.isOffBoard()) {
                    state.enemyOnBoard = true;
                }
                continue;
            }

            // Crippled units count as lost. Units not yet deployed still count, otherwise a force whose
            // reinforcements have not arrived would look crushed on round one.
            if (entity.isCrippled(true)) {
                continue;
            }
            int bv = entity.calculateBattleValue();
            state.friendlyBv += bv;
            if (entity.getOwner().getId() == player.getId()) {
                state.ownBv += bv;
                if (entity.isCommander()) {
                    ownCommanderAlive = true;
                }
            }
        }

        if (!ownCommanderAlive) {
            for (Entity entity : game.getOutOfGameEntitiesVector()) {
                if ((entity.getOwner() != null) && (entity.getOwner().getId() == player.getId())
                      && entity.isCommander()) {
                    state.commanderLost = true;
                    break;
                }
            }
        }

        return state;
    }

    private static class ForceState {
        int initialBv;
        int ownBv;
        int friendlyBv;
        int enemyBv;
        boolean enemyOnBoard;
        boolean commanderLost;
    }
}
