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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import megamek.common.Player;
import megamek.common.event.GameListenerAdapter;
import megamek.common.event.GameWithdrawalEvent;
import megamek.common.game.Game;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.server.Server;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A bot reports a unit's forced-withdrawal course with {@code /withdrawal}; the server's game passes it to its
 * listeners, but only for the sender's own units.
 */
class WithdrawalCommandTest {

    private static final int BOT_ID = 2;
    private static final int OTHER_ID = 3;

    private Server server;
    private BipedMek botMek;
    private BipedMek otherMek;
    private WithdrawalCommand command;
    private final List<GameWithdrawalEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Game game = new Game();
        Player bot = new Player(BOT_ID, "OpFor");
        Player other = new Player(OTHER_ID, "Players");
        game.addPlayer(BOT_ID, bot);
        game.addPlayer(OTHER_ID, other);
        botMek = mek(game, bot);
        otherMek = mek(game, other);
        game.addGameListener(new GameListenerAdapter() {
            @Override
            public void gameWithdrawal(GameWithdrawalEvent e) {
                events.add(e);
            }
        });

        server = mock(Server.class);
        when(server.getGame()).thenReturn(game);
        command = new WithdrawalCommand(server);
    }

    private static BipedMek mek(Game game, Player owner) {
        BipedMek mek = new BipedMek();
        mek.setCrew(new Crew(CrewType.SINGLE));
        mek.setOwner(owner);
        game.addEntity(mek);
        return mek;
    }

    private void send(int connId, String line) {
        command.run(connId, line.split("\\s+"));
    }

    @Test
    void theBotsOwnLineReportsAUnitStartingToWithdraw() {
        send(BOT_ID, WithdrawalCommand.chatLine(botMek.getId(), false));

        assertEquals(1, events.size());
        assertEquals(botMek.getId(), events.get(0).getEntityId());
        assertFalse(events.get(0).isReturningFire());
    }

    @Test
    void theBotsOwnLineReportsAWithdrawingUnitReturningFire() {
        send(BOT_ID, WithdrawalCommand.chatLine(botMek.getId(), true));

        assertEquals(1, events.size());
        assertTrue(events.get(0).isReturningFire());
    }

    @Test
    void aPlayerCannotReportSomeoneElsesUnit() {
        send(BOT_ID, WithdrawalCommand.chatLine(otherMek.getId(), false));

        assertTrue(events.isEmpty());
        verify(server).sendServerChat(anyInt(), anyString());
    }

    @Test
    void malformedLinesReportNothing() {
        send(BOT_ID, "/withdrawal");
        send(BOT_ID, "/withdrawal notanumber withdrawing");
        send(BOT_ID, "/withdrawal " + botMek.getId() + " retreating");
        send(BOT_ID, "/withdrawal 9999 withdrawing");

        assertTrue(events.isEmpty());
    }
}
