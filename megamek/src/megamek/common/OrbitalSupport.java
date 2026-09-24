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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The orbital fire support a single player has available for a scenario: the JumpShips and WarShips in orbit
 * assisting the battle with their naval weapon bays rather than taking part in it directly.
 *
 * <p>No ship is ever placed on the board. What the player gets is a pooled list of naval bays, each of which can be
 * fired once and each of which carries the vessel and crew it belongs to. Canon resolves orbit-to-surface fire per
 * bay, not per ship — "the attack from each bay is targeted and resolved separately", with "the base Damage Value of
 * each attack" being "the Attack Value of each bay" (Strategic Operations, p. 103) — so a flotilla is simply a
 * longer list of bays, and the player picks which guns to spend rather than emptying a broadside at once.</p>
 *
 * <p>The blast radius is fixed at {@value #BLAST_RADIUS} hexes for every bay, whatever its Attack Value. The book
 * does not scale it: damage falls off by two multiplier steps per hex, 10/8/6/4/2, and "units more than 4 hexes away
 * from the target hex suffer no damage". The server's own degradation of damage/(radius + 1) per hex reproduces that
 * exactly at this radius.</p>
 *
 * <p>Instances are immutable. Firing a bay produces a new instance, which keeps the value safe to share between the
 * server's copy of a {@link Player} and the redacted copies sent to clients.</p>
 *
 * @param bays every supporting vessel's naval bays, in the order they are offered to the player
 */
public record OrbitalSupport(List<OrbitalBay> bays) implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    /**
     * The blast radius of every orbital bombardment, in hexes. Fixed by the rules rather than derived from the
     * weapon: StratOps p.103 degrades the damage multiplier by 2 per hex out to 4 and no further.
     */
    public static final int BLAST_RADIUS = 4;

    /**
     * The Gunnery skill assumed when none is supplied - Regular, matching the default skill a unit is built with.
     */
    public static final int DEFAULT_GUNNERY = OrbitalBay.DEFAULT_GUNNERY;

    /** No ship is supporting this player. */
    public static final OrbitalSupport NONE = new OrbitalSupport(List.of());

    public OrbitalSupport {
        // SkyEye: an ArrayList, not List.copyOf. A save game writes this record through XStream 1.4, which cannot
        // read back Java's immutable list implementations - it writes them as java.util.CollSer and then refuses
        // its own output ("Cannot deserialize object with new readObject()/writeObject() methods"), taking the
        // whole save with it. The accessor below hands out an unmodifiable view, so the record is still immutable
        // to everyone outside it.
        bays = (bays == null) ? new ArrayList<>() : new ArrayList<>(bays);
    }

    /** @return The bays, as an unmodifiable view: the backing list is mutable only so that a save game can load. */
    @Override
    public List<OrbitalBay> bays() {
        return Collections.unmodifiableList(bays);
    }

    /**
     * Convenience for a single supporting vessel, which is what one ship's worth of bays amounts to.
     *
     * @param shipName The vessel the bays belong to, stamped onto any bay that does not already name one
     * @param bays     Its naval bays
     * @param gunnery  Its crew's Gunnery skill, applied to any bay that does not already carry one
     */
    public OrbitalSupport(String shipName, List<OrbitalBay> bays, int gunnery) {
        this(stamp(shipName, bays, gunnery));
    }

    /** Convenience for callers with no crew skill to hand; assumes a Regular gunner. */
    public OrbitalSupport(String shipName, List<OrbitalBay> bays) {
        this(shipName, bays, DEFAULT_GUNNERY);
    }

    /**
     * Stamps a ship and crew onto bays built without one, so the single-vessel constructors still produce bays that
     * know where they came from.
     */
    private static List<OrbitalBay> stamp(String shipName, List<OrbitalBay> bays, int gunnery) {
        if (bays == null) {
            return List.of();
        }
        return bays.stream()
                     .map(bay -> bay.shipName().isBlank()
                           ? new OrbitalBay(shipName, bay.name(), bay.attackValue(), bay.weaponClass(), gunnery,
                                 bay.spent())
                           : bay)
                     .toList();
    }

    /**
     * @return The vessels with at least one bay still loaded, in the order their bays are offered and without
     *       repeats, for callers that want to name who is on station
     */
    public List<String> shipNames() {
        return availableBays().stream().map(OrbitalBay::shipName).filter(name -> !name.isBlank()).distinct().toList();
    }

    /** @return The fixed blast radius, for callers that would otherwise hard-code it. */
    public int radius() {
        return BLAST_RADIUS;
    }

    /** @return True when at least one bay still has a shot. */
    public boolean isAvailable() {
        return bays.stream().anyMatch(OrbitalBay::isAvailable);
    }

    /** @return The bays that can still fire, in offer order. */
    public List<OrbitalBay> availableBays() {
        return bays.stream().filter(OrbitalBay::isAvailable).toList();
    }

    /** @return How many bays can still fire. */
    public int strikesRemaining() {
        return availableBays().size();
    }

    /**
     * Finds an available bay by name, matched case-insensitively so a player can type it without fighting the
     * capitalisation a unit file happens to use.
     *
     * @param bayName The bay to look for
     *
     * @return The matching bay, or empty when no available bay has that name
     */
    public Optional<OrbitalBay> findBay(String bayName) {
        if (bayName == null) {
            return Optional.empty();
        }
        return availableBays().stream().filter(bay -> bay.name().equalsIgnoreCase(bayName)).findFirst();
    }

    /**
     * @return The heaviest bay still available, used when a caller has not named one — a bot choosing for itself, or
     *       a player who simply wants the guns pointed at a hex.
     */
    public Optional<OrbitalBay> heaviestAvailableBay() {
        return availableBays().stream().max(Comparator.comparingInt(OrbitalBay::attackValue));
    }

    /**
     * Marks the first available bay of the given name as having fired.
     *
     * @param bayName The bay that fired
     *
     * @return A copy with that bay spent, or this instance unchanged when no available bay matched
     */
    public OrbitalSupport bayFired(String bayName) {
        if (findBay(bayName).isEmpty()) {
            return this;
        }

        boolean done = false;
        OrbitalBay[] updated = bays.toArray(new OrbitalBay[0]);
        for (int i = 0; i < updated.length; i++) {
            if (!done && updated[i].isAvailable() && updated[i].name().equalsIgnoreCase(bayName)) {
                updated[i] = updated[i].fired();
                done = true;
            }
        }
        return new OrbitalSupport(Arrays.asList(updated));
    }
}
