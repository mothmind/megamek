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
 */
package megamek.server.totalWarfare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import megamek.common.Player;
import megamek.common.board.Board;
import megamek.common.enums.GamePhase;
import megamek.common.equipment.EquipmentType;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Entity;
import megamek.server.Server;
import megamek.utils.ServerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for bringing units into a running battle from the host, which is how a secret ambush springs.
 */
class SecretReinforcementsTest {

    private TWGameManager gameManager;
    private Server server;
    private Game game;
    private Player bot;

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() throws IOException {
        gameManager = new TWGameManager();
        game = gameManager.getGame();
        server = ServerFactory.createServer(gameManager);
        Player human = new Player(0, "Player");
        human.setTeam(1);
        bot = new Player(1, "OpFor");
        bot.setTeam(2);
        game.addPlayer(0, human);
        game.addPlayer(1, bot);
        game.setCurrentRound(6);
        game.setPhase(GamePhase.INITIATIVE_REPORT);
    }

    @AfterEach
    void tearDown() {
        server.die();
    }

    private Entity mek(String model) {
        BipedMek mek = new BipedMek();
        mek.setChassis("Ambusher");
        mek.setModel(model);
        mek.setWeight(50.0);
        mek.setOriginalWalkMP(5);
        mek.autoSetInternal();
        mek.setCrew(new Crew(CrewType.SINGLE));
        return mek;
    }

    @Test
    @DisplayName("reinforcements join the owner and deploy in the round given, from the zone given")
    void reinforcementsJoinAndDeployThisRound() {
        Entity first = mek("A");
        Entity second = mek("B");
        int countBefore = bot.getInitialEntityCount();

        gameManager.addSecretReinforcements(bot, List.of(first, second), 6, Board.START_S);

        for (Entity entity : List.of(first, second)) {
            assertSame(entity, game.getEntity(entity.getId()), "the unit is in the game");
            assertEquals(bot.getId(), entity.getOwnerId());
            assertEquals(6, entity.getDeployRound());
            assertEquals(Board.START_S, entity.getStartingPos());
            assertFalse(entity.isDeployed());
        }
        assertEquals(countBefore + 2, bot.getInitialEntityCount(), "the owner's starting strength includes them");

        game.setupDeployment();
        assertTrue(game.shouldDeployThisRound(), "setting up deployment at the end of the phase picks them up");
    }

    @Test
    @DisplayName("works under double-blind, where a unit with no position yet is visible to no one")
    void worksUnderDoubleBlind() {
        game.getOptions().getOption(OptionsConstants.ADVANCED_DOUBLE_BLIND).setValue(true);
        Entity ambusher = mek("C");

        gameManager.addSecretReinforcements(bot, List.of(ambusher), 6, Board.START_E);

        assertSame(ambusher, game.getEntity(ambusher.getId()));
        game.setupDeployment();
        assertTrue(game.shouldDeployThisRound());
    }
}
