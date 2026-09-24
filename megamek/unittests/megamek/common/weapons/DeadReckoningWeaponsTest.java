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
package megamek.common.weapons;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import megamek.common.Player;
import megamek.common.RangeType;
import megamek.common.board.Coords;
import megamek.common.equipment.AmmoType;
import megamek.common.equipment.EquipmentType;
import megamek.common.equipment.Mounted;
import megamek.common.equipment.WeaponMounted;
import megamek.common.equipment.WeaponType;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import megamek.common.units.BipedMek;
import megamek.common.units.Crew;
import megamek.common.units.CrewType;
import megamek.common.units.Mek;
import megamek.server.totalWarfare.TWGameManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Dead Reckoning weapon rebalance, driving the real weapon classes on a real entity in a real game so
 * the option gate is exercised end to end.
 */
class DeadReckoningWeaponsTest {

    private Game game;
    private BipedMek carrier;

    @BeforeAll
    static void initializeEquipment() {
        EquipmentType.initializeTypes();
    }

    @BeforeEach
    void setUp() {
        game = new TWGameManager().getGame();
        game.addPlayer(0, new Player(0, "Gunner"));

        carrier = new BipedMek();
        carrier.setGame(game);
        carrier.setId(1);
        carrier.setChassis("Test Bed");
        carrier.setCrew(new Crew(CrewType.SINGLE));
        carrier.setOwner(game.getPlayer(0));
        carrier.setWeight(50.0);
        carrier.setPosition(new Coords(1, 1));
    }

    private void enableRebalance(boolean enabled) {
        game.getOptions().getOption(OptionsConstants.ADVANCED_DEAD_RECKONING_WEAPONS).setValue(enabled);
    }

    private WeaponMounted mount(String weaponName) {
        try {
            WeaponType weaponType = (WeaponType) EquipmentType.get(weaponName);
            return (WeaponMounted) carrier.addEquipment(weaponType, Mek.LOC_RIGHT_TORSO);
        } catch (Exception ex) {
            throw new IllegalStateException("could not mount " + weaponName, ex);
        }
    }

    private WeaponMounted mountWithAmmo(String weaponName, String ammoName) {
        try {
            WeaponMounted weapon = mount(weaponName);
            Mounted<?> ammo = carrier.addEquipment(EquipmentType.get(ammoName), Mek.LOC_LEFT_TORSO);
            weapon.setLinked(ammo);
            return weapon;
        } catch (Exception ex) {
            throw new IllegalStateException("could not mount " + weaponName + " with " + ammoName, ex);
        }
    }

    private int[] bandModifiers(WeaponMounted weapon) {
        WeaponType weaponType = weapon.getType();
        return new int[] {
              weaponType.getToHitModifierAtRange(weapon, RangeType.RANGE_SHORT),
              weaponType.getToHitModifierAtRange(weapon, RangeType.RANGE_MEDIUM),
              weaponType.getToHitModifierAtRange(weapon, RangeType.RANGE_LONG),
              weaponType.getToHitModifierAtRange(weapon, RangeType.RANGE_EXTREME),
        };
    }

    @Test
    @DisplayName("an AC/2 gains accuracy with range only while the rebalance is on")
    void ac2GainsAccuracyWithRange() {
        WeaponMounted ac2 = mount("ISAC2");

        enableRebalance(false);
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, bandModifiers(ac2), "stock AC/2 must have no accuracy bonus");

        enableRebalance(true);
        assertArrayEquals(new int[] { 0, -1, -2, -3 }, bandModifiers(ac2),
              "rebalanced AC/2 must be flat up close and progressively more accurate further out");
    }

    @Test
    @DisplayName("the bonus reaches every AC/2 family, not just the one under ACWeapon")
    void everyAc2FamilyIsWired() {
        // LB 2-X and Ultra/Rotary hang off AmmoWeapon rather than ACWeapon, so each parent needs its own override.
        String[] ac2s = { "ISAC2", "Improved Autocannon/2", "ISLAC2", "ISHVAC2",
                          "ISLBXAC2", "ISUltraAC2", "ISRotaryAC2" };
        for (String name : ac2s) {
            // A fresh carrier each time, since these together need more slots than one location has.
            setUp();
            enableRebalance(true);
            WeaponMounted weapon = mount(name);
            assertArrayEquals(new int[] { 0, -1, -2, -3 }, bandModifiers(weapon), name + " must get the bonus");
        }
    }

    @Test
    @DisplayName("a larger autocannon gets no accuracy bonus")
    void largerAutocannonsAreUntouched() {
        WeaponMounted ac20 = mount("ISAC20");
        enableRebalance(true);
        assertArrayEquals(new int[] { 0, 0, 0, 0 }, bandModifiers(ac20), "only rack size 2 is buffed");
    }

    @Test
    @DisplayName("an MML firing LRM ammo gets the shorter dead zone and LRM heat")
    void mmlWithLrmAmmo() {
        WeaponMounted mml9 = mountWithAmmo("ISMML9", "IS Ammo MML-9 LRM");

        enableRebalance(false);
        assertArrayEquals(new int[] { 6, 7, 14, 21, 28 }, mml9.getType().getRanges(mml9, mml9.getLinked()));
        assertEquals(5, mml9.getType().getHeat(mml9), "stock MML-9 runs at SRM heat even with LRM ammo");

        enableRebalance(true);
        assertArrayEquals(new int[] { 3, 6, 13, 20, 27 }, mml9.getType().getRanges(mml9, mml9.getLinked()),
              "rebalanced MML LRM mode trades reach for a much smaller minimum range");
        assertEquals(4, mml9.getType().getHeat(mml9), "rebalanced MML-9 runs at LRM heat with LRM ammo");
    }

    @Test
    @DisplayName("an MML firing SRM ammo is left exactly as it was")
    void mmlWithSrmAmmoIsUnchanged() {
        WeaponMounted mml9 = mountWithAmmo("ISMML9", "IS Ammo MML-9 SRM");
        int[] stockRanges = { 0, 3, 6, 9, 12 };

        enableRebalance(false);
        assertArrayEquals(stockRanges, mml9.getType().getRanges(mml9, mml9.getLinked()));
        assertEquals(5, mml9.getType().getHeat(mml9));

        enableRebalance(true);
        assertArrayEquals(stockRanges, mml9.getType().getRanges(mml9, mml9.getLinked()),
              "SRM mode ranges must not move");
        assertEquals(5, mml9.getType().getHeat(mml9), "SRM heat is already correct and must not move");
    }

    @Test
    @DisplayName("LRM-mode heat drops by one across every MML rack size")
    void mmlLrmHeatAcrossRackSizes() {
        String[][] mmls = {
              { "ISMML3", "IS Ammo MML-3 LRM", "2", "1" },
              { "ISMML5", "IS Ammo MML-5 LRM", "3", "2" },
              { "ISMML7", "IS Ammo MML-7 LRM", "4", "3" },
              { "ISMML9", "IS Ammo MML-9 LRM", "5", "4" },
        };
        for (String[] row : mmls) {
            setUp();
            WeaponMounted mml = mountWithAmmo(row[0], row[1]);
            enableRebalance(false);
            assertEquals(Integer.parseInt(row[2]), mml.getType().getHeat(mml), row[0] + " stock heat");
            enableRebalance(true);
            assertEquals(Integer.parseInt(row[3]), mml.getType().getHeat(mml), row[0] + " rebalanced LRM heat");
        }
    }

    @Test
    @DisplayName("Thunderbolts trade a hex of minimum range for a hex of reach everywhere else")
    void thunderboltRanges() {
        WeaponMounted thunderbolt = mount("ISThunderbolt10");

        enableRebalance(false);
        assertArrayEquals(new int[] { 5, 6, 12, 18, 24 }, thunderbolt.getType().getRanges(thunderbolt));

        enableRebalance(true);
        assertArrayEquals(new int[] { 4, 7, 13, 19, 25 }, thunderbolt.getType().getRanges(thunderbolt));
    }

    @Test
    @DisplayName("the Blazer runs cooler, and the Light Blazer is untouched")
    void blazerHeat() {
        WeaponMounted blazer = mount("ISBinaryLaserCannon");
        WeaponMounted lightBlazer = mount("Light Blazer");

        enableRebalance(false);
        assertEquals(16, blazer.getType().getHeat(blazer));

        enableRebalance(true);
        assertEquals(12, blazer.getType().getHeat(blazer));
        assertEquals(6, lightBlazer.getType().getHeat(lightBlazer), "the Light Blazer is not part of the rebalance");
    }

    @Test
    @DisplayName("equipment asked about outside a game reports stock values")
    void unmountedEquipmentIsStock() {
        WeaponType ac2 = (WeaponType) EquipmentType.get("ISAC2");
        WeaponType blazer = (WeaponType) EquipmentType.get("ISBinaryLaserCannon");
        enableRebalance(true);

        // A null mount is what Alpha Strike conversion and the unit browser pass; they must not see the house rule.
        assertEquals(0, ac2.getToHitModifierAtRange(null, RangeType.RANGE_LONG));
        assertEquals(16, blazer.getHeat(null));
        assertEquals(16, blazer.getHeat());
    }

    @Test
    @DisplayName("battle value is identical with the rebalance on and off")
    void battleValueIsUnaffected() {
        mountWithAmmo("ISMML9", "IS Ammo MML-9 LRM");
        mount("ISBinaryLaserCannon");
        carrier.setOriginalWalkMP(4);

        enableRebalance(false);
        int stockBv = carrier.calculateBattleValue(true, true);

        enableRebalance(true);
        assertEquals(stockBv, carrier.calculateBattleValue(true, true),
              "the rebalance must not move BV, so saved campaigns and units.cache stay valid");
    }
}
