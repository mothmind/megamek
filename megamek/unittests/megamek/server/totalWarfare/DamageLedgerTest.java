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
package megamek.server.totalWarfare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;

import java.util.Map;

import megamek.common.HitData;
import megamek.common.Player;
import megamek.common.board.Board;
import megamek.common.enums.GamePhase;
import megamek.common.equipment.EquipmentType;
import megamek.common.game.Game;
import megamek.common.net.enums.PacketCommand;
import megamek.common.net.marshalling.PacketMarshaller;
import megamek.common.net.marshalling.PacketMarshallerFactory;
import megamek.common.net.packets.Packet;
import megamek.common.options.OptionsConstants;
import megamek.common.units.BipedMek;
import megamek.common.units.Entity;
import megamek.common.units.Mek;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every unit keeps a ledger of the damage each attacker did it, so a kill nobody landed the last hit on - an ammo
 * explosion, an ejection, a unit left stranded - can still be credited to whoever did it the most damage, ties going
 * to the attacker who hit it most recently.
 */
class DamageLedgerTest {

    private TWGameManager gameManager;
    private Game game;
    private BipedMek target;
    private BipedMek alpha;
    private BipedMek bravo;

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() {
        gameManager = spy(new TWGameManager());
        doNothing().when(gameManager).send(any(Packet.class));
        doNothing().when(gameManager).sendServerChat(anyString());
        game = gameManager.getGame();
        game.initializeRulesManager(OptionsConstants.RULES_CORE);
        game.addPlayer(0, new Player(0, "Players"));
        game.addPlayer(1, new Player(1, "OpFor"));
        game.setPhase(GamePhase.FIRING);
        game.setBoard(new Board(3, 3));
        target = mek(1, 1);
        alpha = mek(2, 0);
        bravo = mek(3, 0);
    }

    private BipedMek mek(int id, int owner) {
        BipedMek mek = new BipedMek();
        mek.setId(id);
        mek.setOwner(game.getPlayer(owner));
        mek.setWeight(50);
        for (int loc = 0; loc < mek.locations(); loc++) {
            mek.initializeInternal(15, loc);
            mek.initializeArmor(40, loc);
            if (mek.hasRearArmor(loc)) {
                mek.initializeRearArmor(40, loc);
            }
        }
        game.addEntity(mek);
        mek.setDeployed(true);
        return mek;
    }

    private HitData hitFrom(Entity attacker) {
        HitData hit = new HitData(Mek.LOC_CENTER_TORSO);
        if (attacker != null) {
            hit.setAttackerId(attacker.getId());
        }
        return hit;
    }

    @Test
    void theLedgerAddsUpEachAttackersDamage() {
        target.recordDamageFrom(alpha.getId(), 5);
        target.recordDamageFrom(alpha.getId(), 7);
        target.recordDamageFrom(bravo.getId(), 10);

        assertEquals(Map.of(alpha.getId(), 12, bravo.getId(), 10), target.getDamageTakenFrom());
        assertEquals(alpha.getId(), target.getMostDamagingAttackerId());
    }

    @Test
    void aTieGoesToWhoeverHitMostRecently() {
        target.recordDamageFrom(alpha.getId(), 10);
        target.recordDamageFrom(bravo.getId(), 10);
        assertEquals(bravo.getId(), target.getMostDamagingAttackerId(), "bravo hit last");

        target.recordDamageFrom(alpha.getId(), 5);
        target.recordDamageFrom(bravo.getId(), 5);
        target.recordDamageFrom(alpha.getId(), 0);
        assertEquals(bravo.getId(), target.getMostDamagingAttackerId(), "a hit for no damage does not count as a hit");
    }

    @Test
    void unknownAttackersSelfDamageAndNothingAreIgnored() {
        target.recordDamageFrom(Entity.NONE, 20);
        target.recordDamageFrom(target.getId(), 20);
        target.recordDamageFrom(alpha.getId(), 0);

        assertTrue(target.getDamageTakenFrom().isEmpty());
        assertEquals(Entity.NONE, target.getMostDamagingAttackerId());
    }

    @Test
    void weaponDamageIsCreditedToTheAttackerOnTheHit() {
        gameManager.damageEntity(target, hitFrom(alpha), 6);

        assertEquals(Map.of(alpha.getId(), 6), target.getDamageTakenFrom());
    }

    @Test
    void damageWithNoAttackerIsNotCredited() {
        gameManager.damageEntity(target, hitFrom(null), 6);

        assertTrue(target.getDamageTakenFrom().isEmpty());
    }

    @Test
    void physicalAndArtilleryDamageIsCreditedThroughTheAttribution() {
        gameManager.attributeDamageTo(bravo.getId(), () -> gameManager.damageEntity(target, hitFrom(null), 8));
        gameManager.damageEntity(target, hitFrom(null), 4);

        assertEquals(Map.of(bravo.getId(), 8), target.getDamageTakenFrom(),
              "attributed while it lasts, and only while it lasts");
    }

    @Test
    void anAttackerNamedOnTheHitBeatsTheAttribution() {
        gameManager.attributeDamageTo(bravo.getId(), () -> gameManager.damageEntity(target, hitFrom(alpha), 8));

        assertEquals(Map.of(alpha.getId(), 8), target.getDamageTakenFrom());
    }

    @Test
    void attributionIsRestoredEvenWhenTheAttackThrows() {
        try {
            gameManager.attributeDamageTo(bravo.getId(), () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the attack failed; the attribution must not leak into later damage
        }
        gameManager.damageEntity(target, hitFrom(null), 4);

        assertTrue(target.getDamageTakenFrom().isEmpty());
    }

    @Test
    void theLedgerSurvivesTheTripToTheClients() throws Exception {
        target.recordDamageFrom(alpha.getId(), 9);
        target.recordDamageFrom(bravo.getId(), 9);
        PacketMarshaller marshaller = PacketMarshallerFactory.getInstance()
              .getMarshaller(PacketMarshaller.NATIVE_SERIALIZATION_MARSHALING);

        Packet received = marshaller.unmarshall(marshaller.marshall(new Packet(PacketCommand.ENTITY_UPDATE, target)));

        assertNotNull(received, "the unit must pass the network's deserialization filter");
        Entity copy = (Entity) received.getObject(0);
        assertEquals(Map.of(alpha.getId(), 9, bravo.getId(), 9), copy.getDamageTakenFrom());
        assertEquals(bravo.getId(), copy.getMostDamagingAttackerId(), "the hit order travels too");
    }

    @Test
    void aUnitWithNoLedgerIsSafeToAsk() {
        BipedMek fresh = new BipedMek();

        assertTrue(fresh.getDamageTakenFrom().isEmpty());
        assertEquals(Entity.NONE, fresh.getMostDamagingAttackerId());
    }
}
