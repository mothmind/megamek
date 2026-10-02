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
package megamek.common.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import megamek.common.Hex;
import megamek.common.Player;
import megamek.common.Report;
import megamek.common.SpecialHexDisplay;
import megamek.common.ToHitData;
import megamek.common.actions.ArtilleryAttackAction;
import megamek.common.board.Board;
import megamek.common.board.Coords;
import megamek.common.enums.GamePhase;
import megamek.common.equipment.AmmoMounted;
import megamek.common.equipment.EquipmentType;
import megamek.common.equipment.WeaponMounted;
import megamek.common.game.Game;
import megamek.common.options.GameOptions;
import megamek.common.options.OptionsConstants;
import megamek.common.options.PilotOptions;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Entity;
import megamek.common.units.IBuilding;
import megamek.common.units.Mek;
import megamek.common.units.Tank;
import megamek.common.weapons.handlers.artillery.ArtilleryWeaponCloseFireHandler;
import megamek.common.weapons.handlers.artillery.ArtilleryWeaponDistantFireHandler;
import megamek.server.Server;
import megamek.server.totalWarfare.TWGameManager;
import megamek.utils.ServerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives real artillery handlers through a guaranteed wide miss and measures where each round drifts. A direct-fire
 * miss scatters only 1d6 hexes whatever the margin of failure (TO:AR p.153); an indirect miss still drifts by the
 * margin under standard scatter.
 */
class ArtilleryDirectFireScatterTest {

    /** A to-hit number no 2d6 roll can reach: every shot misses by 13 to 23. */
    private static final int HOPELESS_TO_HIT = 25;
    private static final int TRIALS = 150;

    private TWGameManager gameManager;
    private Game game;
    private Server server;
    private GameOptions options;
    private Player attackerPlayer;
    private Player defenderPlayer;
    private final List<Integer> drifts = new ArrayList<>();

    @BeforeAll
    static void beforeAll() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() throws Exception {
        attackerPlayer = new Player(0, "Attacker");
        defenderPlayer = new Player(1, "Defender");
        gameManager = new TWGameManager();
        game = gameManager.getGame();
        game.createVictoryConditions();
        game.addPlayer(attackerPlayer.getId(), attackerPlayer);
        game.addPlayer(defenderPlayer.getId(), defenderPlayer);

        options = mock(GameOptions.class);
        when(options.stringOption(OptionsConstants.ALLOWED_TECH_LEVEL)).thenReturn("Experimental");
        game.setOptions(options);

        Board board = mock(Board.class);
        when(board.contains(any(Coords.class))).thenReturn(true);
        Hex hex = mock(Hex.class);
        when(hex.getLevel()).thenReturn(0);
        when(hex.containsTerrain(anyInt())).thenReturn(false);
        when(board.getHex(any(Coords.class))).thenReturn(hex);
        when(board.getBuildings()).thenReturn(new Vector<IBuilding>().elements());
        when(board.getSpecialHexDisplayTable()).thenReturn(new Hashtable<>());
        doAnswer(invocation -> {
            Coords aimedAt = invocation.getArgument(0);
            SpecialHexDisplay marker = invocation.getArgument(1);
            if (marker.getType() == SpecialHexDisplay.Type.ARTILLERY_MISS) {
                drifts.add((marker.getDriftHex() == null) ? 0 : aimedAt.distance(marker.getDriftHex()));
            }
            return null;
        }).when(board).addSpecialHexDisplay(any(Coords.class), any(SpecialHexDisplay.class));
        game.setBoard(board);

        server = ServerFactory.createServer(gameManager);
    }

    @AfterEach
    void tearDown() {
        server.die();
    }

    private Tank artilleryTank(boolean obliqueArtilleryman) {
        Tank tank = new Tank();
        tank.setGame(game);
        tank.setChassis("Arrow IV Carrier");
        tank.setModel("Test");
        Crew crew = new Crew(CrewType.CREW);
        crew.setGunnery(4, crew.getCrewType().getGunnerPos());
        crew.setPiloting(5, crew.getCrewType().getPilotPos());
        PilotOptions pilotOptions = new PilotOptions();
        if (obliqueArtilleryman) {
            pilotOptions.getOption(OptionsConstants.GUNNERY_OBLIQUE_ARTILLERY).setValue(true);
        }
        crew.setOptions(pilotOptions);
        tank.setCrew(crew);
        tank.setId(game.getNextEntityId());
        game.addEntity(tank);
        tank.setOwner(attackerPlayer);
        tank.setDeployed(true);
        tank.setPosition(new Coords(5, 20));
        return tank;
    }

    private Mek target() {
        Mek mek = new BipedMek();
        mek.setGame(game);
        mek.setChassis("Target");
        mek.setModel("Test");
        Crew crew = new Crew(CrewType.SINGLE);
        crew.setOptions(new PilotOptions());
        mek.setCrew(crew);
        mek.setId(game.getNextEntityId());
        game.addEntity(mek);
        mek.setOwner(defenderPlayer);
        mek.setDeployed(true);
        mek.setPosition(new Coords(5, 10));
        return mek;
    }

    /**
     * Fires one hopeless Arrow IV shot per trial in the given phase, through the handler that phase uses.
     */
    private void fire(GamePhase phase, boolean advancedScatter, boolean obliqueArtilleryman) throws Exception {
        when(options.booleanOption(OptionsConstants.ADVANCED_COMBAT_ADVANCED_SCATTER)).thenReturn(advancedScatter);
        Tank tank = artilleryTank(obliqueArtilleryman);
        WeaponMounted arrow = (WeaponMounted) tank.addEquipment(EquipmentType.get("ISArrowIV"), Tank.LOC_BODY);
        AmmoMounted ammo = (AmmoMounted) tank.addEquipment(EquipmentType.get("ISArrowIVAmmo"), Tank.LOC_BODY);
        arrow.setLinked(ammo);
        Entity target = target();
        game.setPhase(phase);

        for (int trial = 0; trial < TRIALS; trial++) {
            ammo.setShotsLeft(10);
            ArtilleryAttackAction attack = new ArtilleryAttackAction(tank.getId(), target.getTargetType(),
                  target.getId(), tank.getEquipmentNum(arrow), game);
            ToHitData toHit = new ToHitData();
            toHit.addModifier(HOPELESS_TO_HIT, "hopeless");
            ArtilleryWeaponDistantFireHandler handler = phase.isFiring() ?
                  new ArtilleryWeaponCloseFireHandler(toHit, attack, game, gameManager) :
                  new ArtilleryWeaponDistantFireHandler(toHit, attack, game, gameManager);
            handler.handle(phase, new Vector<Report>());
        }
        assertEquals(TRIALS, drifts.size(), "every hopeless shot must miss and leave a miss marker");
    }

    @Test
    void directFireMissScattersOnlyOneD6UnderStandardScatter() throws Exception {
        fire(GamePhase.FIRING, false, false);

        for (int drift : drifts) {
            assertTrue((drift >= 1) && (drift <= 6), "a direct-fire miss must drift 1-6 hexes, got " + drift);
        }
        assertTrue(drifts.contains(1) && drifts.contains(6), "the drift must be rolled on 1d6, not fixed");
    }

    @Test
    void directFireMissStaysWithinSixUnderAdvancedScatter() throws Exception {
        fire(GamePhase.FIRING, true, false);

        for (int drift : drifts) {
            assertTrue((drift >= 1) && (drift <= 6),
                  "a direct-fire miss under Advanced Scatter must stay within 6 hexes, got " + drift);
        }
    }

    @Test
    void obliqueArtillerymanTakesAFlatTwoOffADirectFireMiss() throws Exception {
        fire(GamePhase.FIRING, false, true);

        int onTarget = 0;
        for (int drift : drifts) {
            assertTrue((drift >= 0) && (drift <= 4), "1d6 less 2 must drift 0-4 hexes, got " + drift);
            if (drift == 0) {
                onTarget++;
            }
        }
        assertTrue(drifts.contains(4), "a roll of 6 less 2 must still drift 4");
        // Rolls of 1 or 2 are cancelled entirely (CamOps p.78: minimum 0), so about 1 shot in 3 lands on target.
        assertTrue((onTarget > 30) && (onTarget < 70), "about 1 in 3 should land on target, got " + onTarget);
    }

    @Test
    void indirectMissStillDriftsByTheMarginUnderStandardScatter() throws Exception {
        fire(GamePhase.OFFBOARD, false, false);

        for (int drift : drifts) {
            assertTrue((drift >= 13) && (drift <= 23),
                  "an indirect miss by 13-23 must drift 13-23 hexes under standard scatter, got " + drift);
        }
    }
}
