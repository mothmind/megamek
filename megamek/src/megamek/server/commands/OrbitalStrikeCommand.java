/*
 * Copyright (C) 2025-2026 The MegaMek Team. All Rights Reserved.
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

import java.util.List;

import megamek.client.ui.Messages;
import megamek.common.OrbitalBay;
import megamek.common.OrbitalSupport;
import megamek.common.Player;
import megamek.common.board.Coords;
import megamek.common.options.OptionsConstants;
import megamek.server.Server;
import megamek.server.commands.arguments.Argument;
import megamek.server.commands.arguments.Arguments;
import megamek.server.commands.arguments.CoordXArgument;
import megamek.server.commands.arguments.CoordYArgument;
import megamek.server.commands.arguments.MultiWordStringArgument;
import megamek.server.totalWarfare.TWGameManager;

/**
 * Calls down fire from the JumpShip or WarShip supporting a force from orbit, spending one of its naval bays.
 *
 * <p>Game master only, and deliberately so: it is a test harness and a fallback, not the route players take. Players
 * call strikes from the orbital bombardment window during the targeting phase, where the attack goes through the
 * artillery pipeline and picks up the to-hit modifiers and scatter the rules call for. This command schedules the
 * bombardment directly, which is useful for checking the damage and blast behave, and for a GM who simply wants to
 * drop one.</p>
 *
 * <p>Unlike {@link OrbitalBombardmentCommand}, which is a fiat strike of arbitrary strength, this one spends a real
 * bay belonging to the calling player and takes its damage from that bay's Attack Value.</p>
 *
 * @author mothmind
 */
public class OrbitalStrikeCommand extends GamemasterServerCommand {

    public static final String X = "x";
    public static final String Y = "y";
    public static final String BAY = "bay";

    public OrbitalStrikeCommand(Server server, TWGameManager gameManager) {
        super(server, gameManager, "orbitalstrike",
              Messages.getString("Orbital.cmd.orbitalStrike.help"),
              Messages.getString("Orbital.cmd.orbitalStrike.longName"));
    }

    @Override
    public List<Argument<?>> defineArguments() {
        return List.of(
              new CoordXArgument(X, Messages.getString("Gamemaster.cmd.x")),
              new CoordYArgument(Y, Messages.getString("Gamemaster.cmd.y")),
              new MultiWordStringArgument(BAY, Messages.getString("Orbital.cmd.orbitalStrike.bay"), ""));
    }

    @Override
    protected boolean preRun(int connId) {
        // GM check first: this command is a test and fallback path, not the way players call strikes.
        if (!super.preRun(connId)) {
            return false;
        }

        if (!gameManager.getGame()
              .getOptions()
              .booleanOption(OptionsConstants.ADVANCED_ORBITAL_BOMBARDMENT_SUPPORT)) {
            server.sendServerChat(connId, Messages.getString("Orbital.cmd.orbitalStrike.error.disabled"));
            return false;
        }

        Player player = server.getPlayer(connId);
        if ((player == null) || !player.getOrbitalSupport().isAvailable()) {
            server.sendServerChat(connId, Messages.getString("Orbital.cmd.orbitalStrike.error.noSupport"));
            return false;
        }

        return true;
    }

    @Override
    protected void runCommand(int connId, Arguments args) {
        if (isOutsideOfBoard(connId, args)) {
            return;
        }

        Player player = server.getPlayer(connId);
        // Board numbers shown to players are 1-based, internal coordinates are 0-based.
        Coords position = new Coords((int) args.get(X).getValue() - 1, (int) args.get(Y).getValue() - 1);
        String requestedBay = String.valueOf(args.get(BAY).getValue());

        OrbitalSupport before = player.getOrbitalSupport();
        OrbitalBay fired = gameManager.callOrbitalSupportStrike(player, position, requestedBay);
        if (fired == null) {
            // A named bay that did not match is the likely mistake, so answer with what is actually loaded rather
            // than a bare refusal.
            server.sendServerChat(connId,
                  Messages.getString("Orbital.cmd.orbitalStrike.error.noBay", describeBays(before)));
            return;
        }

        server.sendServerChat(connId,
              Messages.getString("Orbital.cmd.orbitalStrike.success",
                    fired.shipName(),
                    fired.name(),
                    fired.damage(),
                    position.getBoardNum(),
                    player.getOrbitalSupport().strikesRemaining()));
    }

    /**
     * @return A readable list of the bays still loaded, each with its damage, or a note that none remain
     */
    private String describeBays(OrbitalSupport support) {
        List<OrbitalBay> available = support.availableBays();
        if (available.isEmpty()) {
            return Messages.getString("Orbital.cmd.orbitalStrike.noBaysLeft");
        }
        return available.stream()
              .map(bay -> bay.qualifiedName() + " (" + bay.damage() + ")")
              .collect(java.util.stream.Collectors.joining(", "));
    }
}
