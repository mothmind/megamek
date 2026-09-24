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
package megamek.server.totalWarfare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.List;

import megamek.common.OrbitalBay;
import megamek.common.OrbitalBay.WeaponClass;
import megamek.common.OrbitalSupport;
import megamek.common.Player;
import megamek.common.board.Board;
import megamek.common.board.Coords;
import megamek.common.compute.Compute;
import megamek.common.game.Game;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.server.Server;
import megamek.server.props.OrbitalBombardment;
import megamek.utils.BoardLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Tests for the server side of an orbital bombardment: which bay fires, what damage and radius it carries, how the
 * attack roll and scatter behave, and how long the shot stays in flight.
 *
 * <p>The rules being checked are Strategic Operations, Orbit-to-Surface Fire (p. 103): damage is the bay's Attack
 * Value in standard scale, the blast radius is a flat four hexes, the attack resolves under the artillery rules with
 * a -4 for the immobile target hex, and arrival depends on weapon type.</p>
 */
class OrbitalStrikeResolutionTest {

    private static final String BOARD_DATA = """
          size 16 16
          end""";

    private static final Coords TARGET = new Coords(5, 5);

    private TWGameManager gameManager;
    private Game game;
    private Player player;
    private MockedStatic<Server> mockedServer;

    @BeforeEach
    void beforeEach() {
        mockedServer = mockStatic(Server.class);
        Server serverMock = mock(Server.class);
        mockedServer.when(Server::getServerInstance).thenReturn(serverMock);

        gameManager = Mockito.spy(new TWGameManager());
        // transmitPlayerUpdate routes through Server.getServerInstance(), which is mocked above, so it needs no
        // stub of its own - and it is protected in another package, so it could not take one here anyway.
        Mockito.doNothing().when(gameManager).send(any(Packet.class));

        game = gameManager.getGame();
        // Compute's dice reach back through Server.getGame() for the options; without this the real scatter roll
        // inside a missed attack throws rather than scattering.
        Mockito.when(serverMock.getGame()).thenReturn(game);
        Board board = BoardLoader.initializeBoard(BOARD_DATA);
        game.setBoard(board);
        game.getOptions().getOption(OptionsConstants.ADVANCED_ORBITAL_BOMBARDMENT_SUPPORT).setValue(true);

        player = new Player(1, "Tester");
        game.addPlayer(1, player);
    }

    @AfterEach
    void afterEach() {
        mockedServer.close();
    }

    private void give(OrbitalBay... bays) {
        player.setOrbitalSupport(new OrbitalSupport("Invincible", List.of(bays), 4));
    }

    private List<OrbitalBombardment> scheduled() {
        return Collections.list(game.getOrbitalBombardmentAttacks());
    }

    /**
     * Forces the attack roll and the flight-time die, so hit, miss and delay are deterministic. Every other Compute
     * call runs for real, which matters for scatter: it rolls its own dice and must be allowed to.
     */
    private MockedStatic<Compute> withRoll(int twoDiceResult, int oneDieResult) {
        MockedStatic<Compute> compute = mockStatic(Compute.class, Mockito.CALLS_REAL_METHODS);
        compute.when(() -> Compute.d6(2)).thenReturn(twoDiceResult);
        compute.when(Compute::d6).thenReturn(oneDieResult);
        return compute;
    }

    // --- refusals -------------------------------------------------------------------------------------------------

    @Test
    void noStrikeWhenTheRuleIsOff() {
        game.getOptions().getOption(OptionsConstants.ADVANCED_ORBITAL_BOMBARDMENT_SUPPORT).setValue(false);
        give(new OrbitalBay("Nose", 20));

        assertNull(gameManager.callOrbitalSupportStrike(player, TARGET, ""));
        assertTrue(scheduled().isEmpty());
    }

    @Test
    void noStrikeWithoutSupport() {
        assertNull(gameManager.callOrbitalSupportStrike(player, TARGET, ""));
    }

    @Test
    void noStrikeForANullPlayerOrHex() {
        give(new OrbitalBay("Nose", 20));

        assertNull(gameManager.callOrbitalSupportStrike(null, TARGET, ""));
        assertNull(gameManager.callOrbitalSupportStrike(player, null, ""));
    }

    @Test
    void noStrikeAtAHexOffTheBoard() {
        give(new OrbitalBay("Nose", 20));

        assertNull(gameManager.callOrbitalSupportStrike(player, new Coords(99, 99), ""));
        assertTrue(scheduled().isEmpty());
    }

    @Test
    void noStrikeFromABayThatHasAlreadyFired() {
        give(new OrbitalBay("Invincible", "Nose", 20, WeaponClass.BALLISTIC, 4, true));

        assertNull(gameManager.callOrbitalSupportStrike(player, TARGET, "Nose"));
    }

    @Test
    void noStrikeFromABayTheShipDoesNotHave() {
        give(new OrbitalBay("Nose", 20));

        assertNull(gameManager.callOrbitalSupportStrike(player, TARGET, "Broadside"));
        assertTrue(scheduled().isEmpty());
    }

    // --- bay selection --------------------------------------------------------------------------------------------

    @Test
    void anUnnamedRequestFiresTheHeaviestBay() {
        give(new OrbitalBay("Light", 10), new OrbitalBay("Heavy", 40));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            OrbitalBay fired = gameManager.callOrbitalSupportStrike(player, TARGET, "");
            assertEquals("Heavy", fired.name());
        }
    }

    @Test
    void aNamedRequestFiresThatBayAndLeavesTheBigGunsLoaded() {
        give(new OrbitalBay("Light", 10), new OrbitalBay("Heavy", 40));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            assertEquals("Light", gameManager.callOrbitalSupportStrike(player, TARGET, "Light").name());
        }
        assertTrue(player.getOrbitalSupport().findBay("Heavy").isPresent());
        assertTrue(player.getOrbitalSupport().findBay("Light").isEmpty());
    }

    @Test
    void firingSpendsOnlyThatBay() {
        give(new OrbitalBay("Nose", 20), new OrbitalBay("Aft", 20));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "Nose");
        }
        assertEquals(1, player.getOrbitalSupport().strikesRemaining());
    }

    // --- damage and radius ----------------------------------------------------------------------------------------

    @Test
    void damageIsAttackValueTimesTenAndRadiusIsAlwaysFour() {
        give(new OrbitalBay("Nose NAC/40", 40));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        OrbitalBombardment shot = scheduled().get(0);
        assertEquals(400, shot.getDamage());
        assertEquals(OrbitalSupport.BLAST_RADIUS, shot.getRadius());
        assertEquals(4, shot.getRadius());
    }

    @Test
    void theShotIsCreditedToTheShipTheBayAndThePlayer() {
        give(new OrbitalBay("Nose NAC/40", 40));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        OrbitalBombardment shot = scheduled().get(0);
        assertEquals("Invincible", shot.getShipName());
        assertEquals("Nose NAC/40", shot.getBayName());
        assertEquals(player.getId(), shot.getPlayerId());
    }

    // --- attack roll and scatter ----------------------------------------------------------------------------------

    @Test
    void aHitLandsOnTheAimPoint() {
        give(new OrbitalBay("Nose", 20, WeaponClass.ENERGY));

        // Gunnery 4, -4 for the immobile hex, so a 12 comfortably hits.
        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        OrbitalBombardment shot = scheduled().get(0);
        assertEquals(TARGET, shot.getCoords());
        assertEquals(TARGET, shot.getAimPoint());
        assertFalse(shot.hasScattered());
    }

    @Test
    void aMissScattersButStillLands() {
        // A very poor gunner against a roll of 2 cannot make the number, so the shot drifts.
        player.setOrbitalSupport(new OrbitalSupport("Invincible", List.of(new OrbitalBay("Nose", 20)), 8));

        try (MockedStatic<Compute> ignored = withRoll(2, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        OrbitalBombardment shot = scheduled().get(0);
        // The book is explicit that a missed orbit-to-surface attack still hits somewhere.
        assertEquals(1, scheduled().size());
        assertEquals(TARGET, shot.getAimPoint());
        assertNotEquals(TARGET, shot.getCoords());
        assertTrue(shot.hasScattered());
    }

    // --- flight time ----------------------------------------------------------------------------------------------

    @Test
    void energyBaysLandTheSameTurn() {
        give(new OrbitalBay("Laser", 20, WeaponClass.ENERGY));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        assertEquals(0, scheduled().get(0).getTurnsUntilImpact());
        assertTrue(scheduled().get(0).isDue());
    }

    @Test
    void ballisticBaysLandTheTurnAfter() {
        give(new OrbitalBay("NAC", 20, WeaponClass.BALLISTIC));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        assertEquals(1, scheduled().get(0).getTurnsUntilImpact());
        assertFalse(scheduled().get(0).isDue());
    }

    @Test
    void capitalMissilesTakeTheRolledNumberOfTurns() {
        give(new OrbitalBay("Barracuda", 20, WeaponClass.CAPITAL_MISSILE));

        try (MockedStatic<Compute> ignored = withRoll(12, 5)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        assertEquals(5, scheduled().get(0).getTurnsUntilImpact());
    }

    // --- the countdown --------------------------------------------------------------------------------------------

    @Test
    void aDelayedShotDoesNotResolveInThePhaseItWasFired() {
        give(new OrbitalBay("NAC", 20, WeaponClass.BALLISTIC));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        gameManager.resolveScheduledOrbitalBombardments();

        // Still in flight, and one turn closer.
        assertEquals(1, scheduled().size());
        assertEquals(0, scheduled().get(0).getTurnsUntilImpact());
    }

    @Test
    void aShotArrivesOnceItsFlightTimeHasElapsed() {
        give(new OrbitalBay("NAC", 20, WeaponClass.BALLISTIC));

        try (MockedStatic<Compute> ignored = withRoll(12, 3)) {
            gameManager.callOrbitalSupportStrike(player, TARGET, "");
        }

        gameManager.resolveScheduledOrbitalBombardments();   // ticks to 0, still in flight
        gameManager.resolveScheduledOrbitalBombardments();   // now due, lands

        assertTrue(scheduled().isEmpty());
    }
}
