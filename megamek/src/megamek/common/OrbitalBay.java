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

import java.io.Serial;
import java.io.Serializable;

/**
 * A single naval weapon bay aboard a ship supporting a surface battle from orbit.
 *
 * <p>Canon resolves an orbit-to-surface attack per bay rather than per ship: "The attack from each bay is targeted
 * and resolved separately", and "the base Damage Value of each attack is the Attack Value of each bay" (Strategic
 * Operations, Orbit-to-Surface Fire, p. 103). Damage at the target hex is that Attack Value converted to standard
 * scale, every capital point being ten standard points.</p>
 *
 * <p>A bay fires once per scenario. That is a house limit rather than a rule — canon caps the rate instead, allowing
 * a bay to fire every six ground turns — but it keeps a player choosing which guns to spend rather than emptying the
 * whole broadside into the first target of opportunity.</p>
 *
 * @param name         the bay's name, as reports and the bay list show it
 * @param attackValue  the bay's Attack Value in capital scale
 * @param weaponClass  what kind of guns the bay holds, which decides how long the shot takes to arrive
 * @param spent        whether this bay has already fired this scenario
 */
public record OrbitalBay(String name, int attackValue, WeaponClass weaponClass, boolean spent)
      implements Serializable {

    /**
     * How long a bay's fire takes to reach the surface, which StratOps p.103 sets by weapon type rather than by
     * range. The delay is the whole risk of calling a strike: the hex is chosen now and struck later, by which time
     * whatever was standing in it has had the chance to move.
     */
    public enum WeaponClass {
        /** "Direct-Fire Energy Weapon attacks arrive on the ground mapsheet in the same turn they were fired." */
        ENERGY(0),
        /** "Direct-Fire Ballistic Weapon attacks arrive ... the turn after they were fired." */
        BALLISTIC(1),
        /** "Capital Missile Weapon attacks arrive ... 1D6 ground turns after they were fired." */
        CAPITAL_MISSILE(-1);

        private final int fixedDelay;

        WeaponClass(int fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        /**
         * @param roll A 1D6 result, used only by capital missiles; ignored by the direct-fire classes
         *
         * @return How many ground turns pass before the shot lands
         */
        public int arrivalDelay(int roll) {
            return (fixedDelay >= 0) ? fixedDelay : Math.max(1, roll);
        }

        /** @return True when this class needs a die rolled to know its delay. */
        public boolean isVariableDelay() {
            return fixedDelay < 0;
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    /** Capital scale to standard scale: "every point of which equates to 10 points of standard-scale damage". */
    public static final int CAPITAL_TO_STANDARD = 10;

    public OrbitalBay {
        name = (name == null) ? "" : name;
        attackValue = Math.max(0, attackValue);
        weaponClass = (weaponClass == null) ? WeaponClass.BALLISTIC : weaponClass;
    }

    /** A bay of unspecified guns; treated as ballistic, the commonest naval armament. */
    public OrbitalBay(String name, int attackValue) {
        this(name, attackValue, WeaponClass.BALLISTIC, false);
    }

    public OrbitalBay(String name, int attackValue, WeaponClass weaponClass) {
        this(name, attackValue, weaponClass, false);
    }

    /**
     * @return The damage this bay delivers at the target hex, in standard scale. Deliberately uncapped: a heavy naval
     *       bay is supposed to be devastating, and clamping it would be a departure from the book rather than a
     *       reading of it.
     */
    public int damage() {
        return attackValue * CAPITAL_TO_STANDARD;
    }

    /** @return True when this bay still has a shot and is worth firing. */
    public boolean isAvailable() {
        return !spent && (attackValue > 0);
    }

    /** @return A copy marked as having fired. */
    public OrbitalBay fired() {
        return spent ? this : new OrbitalBay(name, attackValue, weaponClass, true);
    }
}
