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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The orbital fire support a single player has available for a scenario: a JumpShip or WarShip in orbit assisting the
 * battle with its naval weapon bays rather than taking part in it directly.
 *
 * <p>The ship is never placed on the board. What the player gets is its list of naval bays, each of which can be
 * fired once. Canon resolves orbit-to-surface fire per bay, not per ship — "the attack from each bay is targeted and
 * resolved separately", with "the base Damage Value of each attack" being "the Attack Value of each bay" (Strategic
 * Operations, p. 103) — so the player picks which guns to spend rather than emptying the broadside at once.</p>
 *
 * <p>The blast radius is fixed at {@value #BLAST_RADIUS} hexes for every bay, whatever its Attack Value. The book
 * does not scale it: damage falls off by two multiplier steps per hex, 10/8/6/4/2, and "units more than 4 hexes away
 * from the target hex suffer no damage". The server's own degradation of damage/(radius + 1) per hex reproduces that
 * exactly at this radius.</p>
 *
 * <p>Instances are immutable. Firing a bay produces a new instance, which keeps the value safe to share between the
 * server's copy of a {@link Player} and the redacted copies sent to clients.</p>
 *
 * @param shipName the vessel providing support, named in reports so the crew knows who is firing
 * @param bays     its naval bays, in the order they are offered to the player
 * @param gunnery  the firing crew's Gunnery skill, which sets the base to-hit number for every strike
 */
public record OrbitalSupport(String shipName, List<OrbitalBay> bays, int gunnery) implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    /**
     * The blast radius of every orbital bombardment, in hexes. Fixed by the rules rather than derived from the
     * weapon: StratOps p.103 degrades the damage multiplier by 2 per hex out to 4 and no further.
     */
    public static final int BLAST_RADIUS = 4;

    /**
     * The Gunnery skill assumed when none is supplied - Regular, matching the default skill a unit is built with.
     */
    public static final int DEFAULT_GUNNERY = 4;

    /** No ship is supporting this player. */
    public static final OrbitalSupport NONE = new OrbitalSupport("", List.of(), DEFAULT_GUNNERY);

    public OrbitalSupport {
        shipName = (shipName == null) ? "" : shipName;
        bays = (bays == null) ? List.of() : List.copyOf(bays);
        // Clamped rather than rejected: a hostile or corrupt value should degrade to an unusually poor gunner, not
        // throw during a game.
        gunnery = Math.clamp(gunnery, 0, 8);
    }

    /** Convenience for callers with no crew skill to hand; assumes a Regular gunner. */
    public OrbitalSupport(String shipName, List<OrbitalBay> bays) {
        this(shipName, bays, DEFAULT_GUNNERY);
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
        return new OrbitalSupport(shipName, List.of(updated), gunnery);
    }
}
