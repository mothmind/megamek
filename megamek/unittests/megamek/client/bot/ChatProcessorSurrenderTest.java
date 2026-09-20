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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import megamek.common.Player;
import megamek.common.game.Game;
import megamek.server.Server;
import megamek.server.commands.DefeatCommand;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ChatProcessor#shouldBotAcknowledgeVictory(String, BotClient)}: a bot accepts an enemy's surrender
 * on behalf of its side only when no human shares that side.
 */
class ChatProcessorSurrenderTest {

    private static final String OFFER = Server.formatChatMessage(Server.ORIGIN, DefeatCommand.getWantsDefeat("Hal"));

    private static Player player(int id, String name, int team, boolean bot) {
        Player player = mock(Player.class);
        when(player.getId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getTeam()).thenReturn(team);
        when(player.isBot()).thenReturn(bot);
        return player;
    }

    private static void teams(List<Player> players) {
        for (Player a : players) {
            for (Player b : players) {
                // Read the mocks before stubbing; a mock call inside thenReturn trips UnfinishedStubbingException.
                boolean enemies = (a.getId() != b.getId()) && (a.getTeam() != b.getTeam());
                when(a.isEnemyOf(b)).thenReturn(enemies);
            }
        }
    }

    private static BotClient botFor(Player me, List<Player> players) {
        Game game = mock(Game.class);
        when(game.getPlayersList()).thenReturn(players);
        BotClient bot = mock(BotClient.class);
        when(bot.getLocalPlayer()).thenReturn(me);
        when(bot.getGame()).thenReturn(game);
        return bot;
    }

    @Test
    void botWithAHumanTeammateLeavesTheDecisionToTheHuman() {
        Player hal = player(1, "Hal", 2, true);
        Player vger = player(2, "V'Ger", 1, true);
        Player dave = player(3, "Dave", 1, false);
        List<Player> players = List.of(hal, vger, dave);
        teams(players);
        BotClient bot = botFor(vger, players);

        assertFalse(new ChatProcessor().shouldBotAcknowledgeVictory(OFFER, bot));
        verify(bot, never()).sendChat("/victory");
    }

    @Test
    void botOnAnAllBotSideAcceptsTheSurrender() {
        Player hal = player(1, "Hal", 2, true);
        Player vger = player(2, "V'Ger", 1, true);
        Player dave = player(3, "Dave", 2, false);
        List<Player> players = List.of(hal, vger, dave);
        teams(players);
        BotClient bot = botFor(vger, players);

        assertTrue(new ChatProcessor().shouldBotAcknowledgeVictory(OFFER, bot));
        verify(bot).sendChat("/victory");
    }

    @Test
    void anObservingHumanDoesNotCount() {
        Player hal = player(1, "Hal", 2, true);
        Player vger = player(2, "V'Ger", 1, true);
        Player watcher = player(3, "Watcher", 1, false);
        when(watcher.isObserver()).thenReturn(true);
        List<Player> players = List.of(hal, vger, watcher);
        teams(players);
        BotClient bot = botFor(vger, players);

        assertTrue(new ChatProcessor().shouldBotAcknowledgeVictory(OFFER, bot));
    }

    @Test
    void aFriendlySurrenderOfferIsIgnored() {
        Player hal = player(1, "Hal", 1, true);
        Player vger = player(2, "V'Ger", 1, true);
        List<Player> players = List.of(hal, vger);
        teams(players);
        BotClient bot = botFor(vger, players);

        assertFalse(new ChatProcessor().shouldBotAcknowledgeVictory(OFFER, bot));
        verify(bot, never()).sendChat("/victory");
    }
}
