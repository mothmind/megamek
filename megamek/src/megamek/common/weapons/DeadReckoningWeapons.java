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

import megamek.common.RangeType;
import megamek.common.annotations.Nullable;
import megamek.common.equipment.Mounted;
import megamek.common.game.Game;
import megamek.common.options.OptionsConstants;
import megamek.common.units.Entity;

/**
 * The Dead Reckoning weapon rebalance, gathered in one place so no weapon class repeats the option check or the stat
 * tables.
 *
 * <p>Every buff here is gated on {@link OptionsConstants#ADVANCED_DEAD_RECKONING_WEAPONS} and resolved at firing time
 * from the firing {@link Mounted}, never from a static game or server reference. Two things follow from that. Battle
 * value is untouched, because it reads the static weapon stats rather than these; and equipment asked about outside a
 * game - an Alpha Strike conversion, the unit browser - passes a {@code null} mount and gets stock values back.</p>
 */
public final class DeadReckoningWeapons {

    /** Rack size that marks an AC/2-class weapon, whatever its autocannon family. */
    public static final int AC2_RACK_SIZE = 2;

    /** AC/2 accuracy bonus by range band: none up close, better the further out the shot is. */
    private static final int AC2_MEDIUM_MODIFIER = -1;
    private static final int AC2_LONG_MODIFIER = -2;
    private static final int AC2_EXTREME_MODIFIER = -3;

    /** Blazer heat with the rebalance on; stock is 16. */
    public static final int BLAZER_HEAT = 12;

    /** Thunderbolt ranges with the rebalance on: a hex off the minimum, a hex onto every other band. */
    private static final int[] THUNDERBOLT_RANGES = { 4, 7, 13, 19, 25 };

    /** MML ranges with LRM ammo loaded: shorter reach than a true LRM, but a fraction of its dead zone. */
    private static final int[] MML_LRM_RANGES = { 3, 6, 13, 20, 27 };

    private DeadReckoningWeapons() {}

    /**
     * @param mounted the mounted equipment being asked about, which may be {@code null}
     *
     * @return whether the Dead Reckoning weapon rebalance applies to this mount
     */
    public static boolean isEnabled(@Nullable Mounted<?> mounted) {
        if (mounted == null) {
            return false;
        }
        Entity entity = mounted.getEntity();
        Game game = (entity != null) ? entity.getGame() : null;
        return (game != null) && game.getOptions().booleanOption(OptionsConstants.ADVANCED_DEAD_RECKONING_WEAPONS);
    }

    /**
     * The AC/2 accuracy bonus. The shot is no easier up close, where an AC/2 was never the problem, and progressively
     * easier the further out it is fired.
     *
     * @param range a {@link RangeType} range band
     *
     * @return the to-hit modifier for that band
     */
    public static int ac2ToHitModifierAtRange(int range) {
        return switch (range) {
            case RangeType.RANGE_MEDIUM -> AC2_MEDIUM_MODIFIER;
            case RangeType.RANGE_LONG -> AC2_LONG_MODIFIER;
            case RangeType.RANGE_EXTREME, RangeType.RANGE_LOS -> AC2_EXTREME_MODIFIER;
            default -> 0;
        };
    }

    /** @return the rebalanced Thunderbolt ranges, as {@code {minimum, short, medium, long, extreme}} */
    public static int[] thunderboltRanges() {
        return THUNDERBOLT_RANGES.clone();
    }

    /** @return the rebalanced MML ranges with LRM ammo loaded, as {@code {minimum, short, medium, long, extreme}} */
    public static int[] mmlLrmRanges() {
        return MML_LRM_RANGES.clone();
    }

    /**
     * MML heat with LRM ammo loaded. Stock MML heat sits on the SRM curve of roughly 0.7 heat per missile, so an MML
     * pays SRM prices even when throwing LRMs. The Inner Sphere LRM line runs at roughly 0.4 heat per missile, which
     * across the MML rack sizes works out to exactly one point less. SRM ammo keeps the stock heat, which is already
     * correct for it.
     *
     * @param stockHeat the weapon's normal heat
     *
     * @return the heat to apply when firing LRM ammo
     */
    public static int mmlLrmHeat(int stockHeat) {
        return Math.max(0, stockHeat - 1);
    }
}
