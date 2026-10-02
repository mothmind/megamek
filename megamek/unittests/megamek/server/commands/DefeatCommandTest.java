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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import megamek.common.Player;
import megamek.common.event.GameListenerAdapter;
import megamek.common.event.GameSurrenderEvent;
import megamek.common.game.Game;
import megamek.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /defeat} tells the server's own game that a player declared defeat, so anything listening there - such as
 * MekHQ's pilot chatter - can react to a surrender without parsing chat.
 */
class DefeatCommandTest {

    private static final int CONN_ID = 3;

    private Game game;
    private Player player;
    private DefeatCommand command;
    private final List<GameSurrenderEvent> surrenders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        game = new Game();
        player = new Player(CONN_ID, "OpFor");
        player.setTeam(2);
        game.addPlayer(CONN_ID, player);
        game.addGameListener(new GameListenerAdapter() {
            @Override
            public void gameSurrender(GameSurrenderEvent e) {
                surrenders.add(e);
            }
        });

        Server server = mock(Server.class);
        when(server.getGame()).thenReturn(game);
        when(server.getPlayer(CONN_ID)).thenReturn(player);
        command = new DefeatCommand(server);
    }

    @Test
    void offeringToSurrenderFiresAnOfferEvent() {
        command.run(CONN_ID, new String[] { "/defeat" });

        assertEquals(1, surrenders.size());
        assertEquals(CONN_ID, surrenders.get(0).getPlayerId());
        assertFalse(surrenders.get(0).isAdmitted(), "no victory was declared, so this is an offer");
        assertFalse(player.admitsDefeat());
    }

    @Test
    void admittingDefeatToADeclaredVictoryFiresAnAdmittedEvent() {
        game.setForceVictory(true);

        command.run(CONN_ID, new String[] { "/defeat" });

        assertEquals(1, surrenders.size());
        assertTrue(surrenders.get(0).isAdmitted(), "a victory was declared, so this admits defeat");
        assertTrue(player.admitsDefeat());
    }
}
