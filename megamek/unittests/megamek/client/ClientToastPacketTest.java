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
package megamek.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import megamek.common.event.GameListenerAdapter;
import megamek.common.event.GameToastEvent;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.packets.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A server-sent toast may carry how long it should stay up. A packet from a sender that does not say, which is every
 * packet that existed before this, must still come out as a toast that leaves the time to the player's own setting.
 */
class ClientToastPacketTest {
    private Client client;
    private final List<GameToastEvent> toasts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        client = new Client("Test Player", "localhost", 1234);
        client.getGame().addGameListener(new GameListenerAdapter() {
            @Override
            public void gameToast(GameToastEvent event) {
                toasts.add(event);
            }
        });
    }

    @Test
    @DisplayName("a toast packet with no duration leaves the time to the player's own setting")
    void noDurationMeansThePlayersSetting() {
        assertTrue(client.handleGameIndependentPacket(
              new Packet(PacketCommand.SEND_TOAST, GameToastEvent.Level.INFO, "Hello", 5)));

        assertEquals(1, toasts.size());
        assertEquals("Hello", toasts.getFirst().message());
        assertEquals(5, toasts.getFirst().entityId());
        assertEquals(0, toasts.getFirst().durationMs());
    }

    @Test
    @DisplayName("a toast packet can carry its own duration, which is passed on untouched")
    void aPacketCanCarryItsOwnDuration() {
        assertTrue(client.handleGameIndependentPacket(
              new Packet(PacketCommand.SEND_TOAST, GameToastEvent.Level.INFO, "Hello", 5, 20_000)));

        assertEquals(1, toasts.size());
        assertEquals("Hello", toasts.getFirst().message());
        assertEquals(5, toasts.getFirst().entityId());
        assertEquals(20_000, toasts.getFirst().durationMs());
    }
}
