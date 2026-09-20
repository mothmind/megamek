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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import megamek.common.Player;
import megamek.common.game.Game;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Princess#checkForSurrender(int)}: the once-per-round guard, the single {@code /defeat} offer, the
 * periodic reminder, and the promise that a failing check never escapes into {@code changePhase}.
 */
class PrincessSurrenderTest {

    private Princess princess;
    private BehaviorSettings behavior;
    private SurrenderUtil surrenderUtil;

    @BeforeEach
    void setUp() {
        princess = spy(new Princess("TestPrincess", UUID.randomUUID().toString(), 1));
        doReturn(mock(Game.class)).when(princess).getGame();
        doReturn(mock(Player.class)).when(princess).getLocalPlayer();
        doNothing().when(princess).sendChat(anyString());

        behavior = new BehaviorSettings();
        behavior.setAllowSurrender(true);
        doReturn(behavior).when(princess).getBehaviorSettings();

        surrenderUtil = mock(SurrenderUtil.class);
        princess.setSurrenderUtil(surrenderUtil);
    }

    @Test
    void doesNothingWhenSurrenderIsOff() {
        behavior.setAllowSurrender(false);
        princess.checkForSurrender(1);
        verify(surrenderUtil, never()).shouldSurrender(anyInt(), any(), any());
        verify(princess, never()).sendChat(anyString());
    }

    @Test
    void offersOnceThenRemindsEveryThirdRound() {
        when(surrenderUtil.shouldSurrender(anyInt(), any(), any())).thenReturn(true);

        princess.checkForSurrender(1);
        verify(princess, times(1)).sendChat("/defeat");

        princess.checkForSurrender(2);
        princess.checkForSurrender(3);
        verify(princess, times(1)).sendChat("/defeat");
        verify(surrenderUtil, times(1)).shouldSurrender(anyInt(), any(), any());

        princess.checkForSurrender(4);
        verify(princess, times(2)).sendChat("/defeat");

        princess.checkForSurrender(7);
        verify(princess, times(3)).sendChat("/defeat");
    }

    @Test
    void aRoundIsOnlyEvaluatedOnce() {
        when(surrenderUtil.shouldSurrender(anyInt(), any(), any())).thenReturn(false);

        princess.checkForSurrender(2);
        princess.checkForSurrender(2);
        princess.checkForSurrender(1);
        verify(surrenderUtil, times(1)).shouldSurrender(anyInt(), any(), any());
        verify(princess, never()).sendChat(anyString());
    }

    @Test
    void passesTheResolveIndexThrough() {
        behavior.setResolveIndex(8);
        when(surrenderUtil.shouldSurrender(anyInt(), any(), any())).thenReturn(false);
        princess.checkForSurrender(1);
        verify(surrenderUtil).shouldSurrender(eq(8), any(), any());
    }

    @Test
    void aFailingCheckDoesNotEscape() {
        when(surrenderUtil.shouldSurrender(anyInt(), any(), any())).thenThrow(new IllegalStateException("boom"));
        princess.checkForSurrender(1);
        verify(princess, never()).sendChat(anyString());
    }
}
