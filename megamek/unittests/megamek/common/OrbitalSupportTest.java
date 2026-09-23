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
package megamek.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import megamek.common.OrbitalBay.WeaponClass;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrbitalSupport} and {@link OrbitalBay}: what counts as a usable fire mission, how a bay's Attack
 * Value becomes damage, and how firing one bay leaves the rest alone.
 */
class OrbitalSupportTest {

    private static OrbitalSupport shipWith(OrbitalBay... bays) {
        return new OrbitalSupport("Invincible", List.of(bays));
    }

    @Test
    void noneIsNeverAvailable() {
        assertFalse(OrbitalSupport.NONE.isAvailable());
        assertEquals(0, OrbitalSupport.NONE.strikesRemaining());
    }

    @Test
    void damageIsAttackValueInStandardScale() {
        // StratOps p.103: damage at the target hex is the bay's Attack Value, every capital point being ten
        // standard points. An NAC/40 bay is 40 capital, so 400 standard.
        assertEquals(400, new OrbitalBay("Nose NAC/40", 40).damage());
        assertEquals(600, new OrbitalBay("Three NAC/20s", 60).damage());
    }

    @Test
    void damageIsNotCapped() {
        // Deliberately uncapped: the book does not clamp a heavy bay and neither do we.
        assertEquals(20_000, new OrbitalBay("Absurd", 2000).damage());
    }

    @Test
    void blastRadiusIsFixedAtFour() {
        // The radius never scales with the gun; the multiplier falls 10/8/6/4/2 and stops.
        assertEquals(4, OrbitalSupport.BLAST_RADIUS);
        assertEquals(4, shipWith(new OrbitalBay("Light", 1)).radius());
        assertEquals(4, shipWith(new OrbitalBay("Heavy", 400)).radius());
    }

    @Test
    void aShipWithLoadedBaysIsAvailable() {
        OrbitalSupport support = shipWith(new OrbitalBay("Nose", 20), new OrbitalBay("Aft", 10));

        assertTrue(support.isAvailable());
        assertEquals(2, support.strikesRemaining());
    }

    @Test
    void aBayWithNoAttackValueIsNotOffered() {
        assertFalse(shipWith(new OrbitalBay("Empty", 0)).isAvailable());
    }

    @Test
    void negativeAttackValueIsClampedToZero() {
        assertEquals(0, new OrbitalBay("Broken", -50).attackValue());
        assertFalse(new OrbitalBay("Broken", -50).isAvailable());
    }

    @Test
    void nullNamesBecomeEmpty() {
        assertEquals("", new OrbitalBay(null, 10).name());
        assertEquals("", new OrbitalSupport(null, List.of()).shipName());
    }

    @Test
    void nullBayListBecomesEmpty() {
        assertFalse(new OrbitalSupport("Invincible", null).isAvailable());
    }

    @Test
    void theHeaviestBayIsFoundNotTheFirst() {
        OrbitalSupport support = shipWith(new OrbitalBay("Light", 10), new OrbitalBay("Heavy", 40),
              new OrbitalBay("Medium", 20));

        assertEquals("Heavy", support.heaviestAvailableBay().orElseThrow().name());
    }

    @Test
    void baysAreFoundByNameIgnoringCase() {
        OrbitalSupport support = shipWith(new OrbitalBay("Nose NAC/20", 20));

        assertTrue(support.findBay("nose nac/20").isPresent());
        assertTrue(support.findBay("Broadside").isEmpty());
        assertTrue(support.findBay(null).isEmpty());
    }

    @Test
    void firingOneBayLeavesTheOthersLoaded() {
        OrbitalSupport after = shipWith(new OrbitalBay("Nose", 40), new OrbitalBay("Aft", 10)).bayFired("Nose");

        assertEquals(1, after.strikesRemaining());
        assertEquals("Aft", after.heaviestAvailableBay().orElseThrow().name());
        assertTrue(after.findBay("Nose").isEmpty());
    }

    @Test
    void firingOnlyOneOfTwoIdenticalBaysLeavesTheOther() {
        // A ship with matching bays in two arcs must not lose both to a single call.
        OrbitalSupport after = shipWith(new OrbitalBay("NAC/20 Bay", 20), new OrbitalBay("NAC/20 Bay", 20))
              .bayFired("NAC/20 Bay");

        assertEquals(1, after.strikesRemaining());
    }

    @Test
    void firingTheLastBayExhaustsTheShip() {
        OrbitalSupport after = shipWith(new OrbitalBay("Nose", 40)).bayFired("Nose");

        assertFalse(after.isAvailable());
        assertEquals(0, after.strikesRemaining());
    }

    @Test
    void firingAnUnknownBayChangesNothing() {
        OrbitalSupport support = shipWith(new OrbitalBay("Nose", 40));

        assertSame(support, support.bayFired("Broadside"));
    }

    @Test
    void arrivalDelayFollowsWeaponClass() {
        // StratOps p.103: energy lands the same turn, ballistic the next, capital missiles 1D6 turns later.
        assertEquals(0, WeaponClass.ENERGY.arrivalDelay(6));
        assertEquals(1, WeaponClass.BALLISTIC.arrivalDelay(6));
        assertEquals(5, WeaponClass.CAPITAL_MISSILE.arrivalDelay(5));
    }

    @Test
    void onlyCapitalMissilesNeedADieRolled() {
        assertFalse(WeaponClass.ENERGY.isVariableDelay());
        assertFalse(WeaponClass.BALLISTIC.isVariableDelay());
        assertTrue(WeaponClass.CAPITAL_MISSILE.isVariableDelay());
    }

    @Test
    void aMissileDelayIsNeverZero() {
        // A 1D6 that somehow arrives as 0 must not turn a missile into a same-turn weapon.
        assertEquals(1, WeaponClass.CAPITAL_MISSILE.arrivalDelay(0));
    }

    @Test
    void anUnspecifiedBayIsTreatedAsBallistic() {
        assertEquals(WeaponClass.BALLISTIC, new OrbitalBay("Nose", 20).weaponClass());
        assertEquals(WeaponClass.BALLISTIC, new OrbitalBay("Nose", 20, null, false).weaponClass());
    }

    @Test
    void firingABayKeepsItsWeaponClass() {
        OrbitalBay fired = new OrbitalBay("Nose", 20, WeaponClass.CAPITAL_MISSILE).fired();

        assertEquals(WeaponClass.CAPITAL_MISSILE, fired.weaponClass());
        assertTrue(fired.spent());
    }

    @Test
    void gunneryDefaultsToRegularWhenNotSupplied() {
        assertEquals(4, OrbitalSupport.DEFAULT_GUNNERY);
        assertEquals(4, shipWith(new OrbitalBay("Nose", 20)).gunnery());
        assertEquals(4, OrbitalSupport.NONE.gunnery());
    }

    @Test
    void gunneryIsCarriedAndClamped() {
        assertEquals(2, new OrbitalSupport("Elite", List.of(new OrbitalBay("Nose", 20)), 2).gunnery());
        // Clamped rather than rejected, so a bad value degrades to a poor gunner instead of throwing mid-game.
        assertEquals(0, new OrbitalSupport("Impossible", List.of(), -5).gunnery());
        assertEquals(8, new OrbitalSupport("Hopeless", List.of(), 99).gunnery());
    }

    @Test
    void firingABayDoesNotChangeTheGunner() {
        OrbitalSupport after = new OrbitalSupport("Elite",
              List.of(new OrbitalBay("Nose", 40), new OrbitalBay("Aft", 10)), 1).bayFired("Nose");

        assertEquals(1, after.gunnery());
        assertEquals("Elite", after.shipName());
    }

    @Test
    void availableBaysExcludeSpentOnes() {
        OrbitalSupport support = new OrbitalSupport("Invincible",
              List.of(new OrbitalBay("Nose", 40, WeaponClass.BALLISTIC, true),
                    new OrbitalBay("Aft", 10, WeaponClass.BALLISTIC, false)));

        assertEquals(1, support.availableBays().size());
        assertEquals("Aft", support.availableBays().get(0).name());
    }
}
