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
package megamek.client.bot.princess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import megamek.common.OrbitalBay;
import megamek.common.OrbitalSupport;
import megamek.common.Player;
import megamek.common.board.Board;
import megamek.common.board.Coords;
import megamek.common.game.Game;
import megamek.common.units.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrbitalStrikeControl}: when a limited bombardment is worth spending, and when the bot should hold
 * its fire instead. Every hex not named by a test is stubbed empty, so only the occupied hexes contribute and the
 * expected scores stay readable despite the blast covering four rings.
 */
class OrbitalStrikeControlTest {

    /**
     * A 10-point capital bay lands 100 standard damage. The blast radius is fixed at 4, and the server degrades by
     * damage/(radius + 1) = 20 per hex, so the centre takes 100 and the rings 80, 60, 40, 20 - the book's falloff.
     */
    private static final OrbitalSupport SUPPORT =
          new OrbitalSupport("Invincible", java.util.List.of(new OrbitalBay("Nose Bay", 10)));

    /** A ship whose only bay has already fired. */
    private static final OrbitalSupport EXHAUSTED =
          new OrbitalSupport("Invincible",
                java.util.List.of(new OrbitalBay("Invincible", "Nose Bay", 10, OrbitalBay.WeaponClass.BALLISTIC, 4,
                      true)));

    private static final int BOARD_ID = 0;
    private static final Coords HEX_A = new Coords(10, 10);
    private static final Coords HEX_B = new Coords(20, 20);

    private OrbitalStrikeControl control;
    private Princess owner;
    private Game game;
    private Player me;
    private Player theirs;
    private IHonorUtil honorUtil;
    private int nextEntityId = 1;

    @BeforeEach
    void setUp() {
        control = new OrbitalStrikeControl();

        me = mock(Player.class);
        when(me.getId()).thenReturn(1);

        theirs = mock(Player.class);
        when(theirs.getId()).thenReturn(2);
        when(theirs.isEnemyOf(me)).thenReturn(true);

        Board board = mock(Board.class);
        when(board.getBoardId()).thenReturn(BOARD_ID);

        game = mock(Game.class);
        when(game.getBoard()).thenReturn(board);
        // Every hex is empty unless a test says otherwise; the scorer walks a whole blast and would otherwise trip
        // over unstubbed neighbours.
        when(game.getEntitiesVector(any(Coords.class), anyInt(), anyBoolean())).thenReturn(List.of());

        honorUtil = mock(IHonorUtil.class);

        BehaviorSettings behaviorSettings = mock(BehaviorSettings.class);

        owner = mock(Princess.class);
        when(owner.getGame()).thenReturn(game);
        when(owner.getLocalPlayer()).thenReturn(me);
        when(owner.getHonorUtil()).thenReturn(honorUtil);
        when(owner.getBehaviorSettings()).thenReturn(behaviorSettings);
        when(owner.getEnemyEntities()).thenReturn(List.of());
    }

    @Test
    void noEnemiesMeansNoStrike() {
        assertTrue(control.selectTarget(owner, SUPPORT).isEmpty());
    }

    @Test
    void exhaustedSupportMeansNoStrike() {
        Entity enemy = groundUnit(theirs, 0, HEX_A);
        placeEnemies(List.of(enemy));
        occupy(HEX_A, enemy);

        assertTrue(control.selectTarget(owner, EXHAUSTED).isEmpty());
    }

    @Test
    void aStationaryEnemyIsWorthAStrike() {
        Entity enemy = groundUnit(theirs, 0, HEX_A);
        placeEnemies(List.of(enemy));
        occupy(HEX_A, enemy);

        // 100 damage against a unit that cannot run clear scores 100, well past the 25 needed.
        assertEquals(Optional.of(HEX_A), control.selectTarget(owner, SUPPORT));
    }

    @Test
    void aLoneFastScoutIsNotWorthAStrike() {
        Entity scout = groundUnit(theirs, 12, HEX_A);
        placeEnemies(List.of(scout));
        occupy(HEX_A, scout);

        // 100 * 1/13 is about 7.7, short of the 25 a strike has to be worth.
        assertTrue(control.selectTarget(owner, SUPPORT).isEmpty());
    }

    @Test
    void aFriendlyInTheBlastVetoesTheHex() {
        Entity enemy = groundUnit(theirs, 0, HEX_A);
        Entity friendly = groundUnit(me, 0, HEX_A);
        placeEnemies(List.of(enemy));
        occupy(HEX_A, enemy, friendly);

        // The enemy is worth +100 and the friendly -300, so the hex scores well below zero.
        assertTrue(control.selectTarget(owner, SUPPORT).isEmpty());
    }

    @Test
    void picksTheDenserClusterOverTheNearerSingleton() {
        Entity loner = groundUnit(theirs, 0, HEX_A);
        Entity first = groundUnit(theirs, 0, HEX_B);
        Entity second = groundUnit(theirs, 0, HEX_B);
        Entity third = groundUnit(theirs, 0, HEX_B);

        placeEnemies(List.of(loner, first, second, third));
        occupy(HEX_A, loner);
        occupy(HEX_B, first, second, third);

        assertEquals(Optional.of(HEX_B), control.selectTarget(owner, SUPPORT));
    }

    @Test
    void aBrokenEnemyIsNotWorthAStrike() {
        Entity enemy = groundUnit(theirs, 0, HEX_A);
        int enemyId = enemy.getId();
        when(honorUtil.isEnemyBroken(eq(enemyId), anyInt(), anyBoolean())).thenReturn(true);
        when(honorUtil.isEnemyDishonored(anyInt())).thenReturn(false);

        placeEnemies(List.of(enemy));
        occupy(HEX_A, enemy);

        assertTrue(control.selectTarget(owner, SUPPORT).isEmpty());
    }

    @Test
    void aBrokenButDishonorableEnemyIsStillWorthAStrike() {
        Entity enemy = groundUnit(theirs, 0, HEX_A);
        int enemyId = enemy.getId();
        when(honorUtil.isEnemyBroken(eq(enemyId), anyInt(), anyBoolean())).thenReturn(true);
        when(honorUtil.isEnemyDishonored(anyInt())).thenReturn(true);

        placeEnemies(List.of(enemy));
        occupy(HEX_A, enemy);

        assertEquals(Optional.of(HEX_A), control.selectTarget(owner, SUPPORT));
    }

    @Test
    void airborneEnemiesAreIgnored() {
        Entity flyer = groundUnit(theirs, 0, HEX_A);
        when(flyer.isAirborne()).thenReturn(true);

        placeEnemies(List.of(flyer));
        occupy(HEX_A, flyer);

        assertTrue(control.selectTarget(owner, SUPPORT).isEmpty());
    }

    private void placeEnemies(List<Entity> enemies) {
        when(owner.getEnemyEntities()).thenReturn(enemies);
    }

    private void occupy(Coords coords, Entity... occupants) {
        when(game.getEntitiesVector(eq(coords), anyInt(), anyBoolean())).thenReturn(List.of(occupants));
    }

    private Entity groundUnit(Player unitOwner, int runMP, Coords position) {
        // Read the owner's id up front: calling one mock inside another's thenReturn() leaves Mockito with an
        // unfinished stubbing and blows up the whole test.
        int ownerId = unitOwner.getId();

        Entity entity = mock(Entity.class);
        when(entity.isAirborne()).thenReturn(false);
        when(entity.isAirborneVTOLorWIGE()).thenReturn(false);
        when(entity.getTransportId()).thenReturn(Entity.NONE);
        when(entity.isOnBoard(anyInt())).thenReturn(true);
        when(entity.getPosition()).thenReturn(position);
        when(entity.getOwner()).thenReturn(unitOwner);
        when(entity.getOwnerId()).thenReturn(ownerId);
        when(entity.getRunMP()).thenReturn(runMP);
        when(entity.getId()).thenReturn(nextEntityId++);
        return entity;
    }
}
