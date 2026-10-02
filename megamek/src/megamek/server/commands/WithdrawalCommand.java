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
package megamek.server.commands;

import megamek.common.event.GameWithdrawalEvent;
import megamek.common.game.Game;
import megamek.common.units.Entity;
import megamek.server.Server;

/**
 * Lets a bot report one of its units changing course under forced withdrawal - starting to withdraw once crippled, or
 * earning return fire after being shot at while withdrawing - so the server's game can tell its listeners. Forced
 * withdrawal is a bot behaviour the server does not otherwise know about. A player may only report their own units.
 */
public class WithdrawalCommand extends ServerCommand {

    public static final String COMMAND_NAME = "withdrawal";
    static final String WITHDRAWING = "withdrawing";
    static final String RETURN_FIRE = "returnfire";
    public static final String HELP_TEXT = "Reports a unit starting to withdraw, or a withdrawing unit returning fire. "
          + "Sent by bots. Usage: /" + COMMAND_NAME + " <unit id> " + WITHDRAWING + "|" + RETURN_FIRE;

    public WithdrawalCommand(Server server) {
        super(server, COMMAND_NAME, HELP_TEXT);
    }

    /**
     * @param entityId      the unit that changed course
     * @param returningFire {@code true} for a withdrawing unit that may now return fire, {@code false} for one that has
     *                      just started to withdraw
     *
     * @return the chat line a bot sends to report it
     */
    public static String chatLine(int entityId, boolean returningFire) {
        return "/" + COMMAND_NAME + " " + entityId + " " + (returningFire ? RETURN_FIRE : WITHDRAWING);
    }

    @Override
    public void run(int connId, String[] args) {
        if ((args.length < 3) || !(server.getGame() instanceof Game game)) {
            server.sendServerChat(connId, HELP_TEXT);
            return;
        }
        boolean returningFire;
        if (RETURN_FIRE.equals(args[2])) {
            returningFire = true;
        } else if (WITHDRAWING.equals(args[2])) {
            returningFire = false;
        } else {
            server.sendServerChat(connId, HELP_TEXT);
            return;
        }

        int entityId;
        try {
            entityId = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            server.sendServerChat(connId, HELP_TEXT);
            return;
        }
        Entity entity = game.getEntity(entityId);
        if ((entity == null) || (entity.getOwnerId() != connId)) {
            server.sendServerChat(connId, "You can only report the withdrawal of your own units.");
            return;
        }
        game.processGameEvent(new GameWithdrawalEvent(this, entityId, returningFire));
    }
}
