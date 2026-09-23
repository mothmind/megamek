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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import megamek.common.OrbitalBay;
import megamek.common.OrbitalSupport;
import megamek.common.Player;
import megamek.common.board.Coords;
import megamek.common.units.Entity;
import megamek.logging.MMLogger;

/**
 * Chooses where Princess aims the orbital bombardment offered by a JumpShip or WarShip supporting her force.
 *
 * <p>A strike is a scarce resource: the ship will only fire a handful of times in a battle, and the blast is
 * indiscriminate. So rather than firing at the nearest enemy, every hex an enemy currently occupies is scored by what
 * the whole blast would land on, friendly units included, and the strike is only called when the best hex is worth a
 * meaningful fraction of the shot. Otherwise the ship holds its fire for a better cluster later.</p>
 *
 * <p>The scoring follows the same shape as {@link ArtilleryTargetingControl}: each unit contributes the damage that
 * reaches its hex, weighted down by how fast it could run clear, and signed by whose side it is on. Unlike artillery,
 * the blast radius and falloff come from the ship rather than from the damage value, so they are read off the
 * {@link OrbitalSupport} and matched to the server's own linear degradation.</p>
 *
 * @author mothmind
 */
public class OrbitalStrikeControl {

    private static final MMLogger LOGGER = MMLogger.create(OrbitalStrikeControl.class);

    /**
     * A strike is only called when the best hex scores at least this fraction of the bombardment's own damage. It
     * keeps Princess from spending a limited shot on a lone scout, while staying independent of how hard any
     * particular ship hits.
     */
    private static final double MINIMUM_VALUE_FRACTION = 0.25;

    /**
     * How heavily a friendly unit inside the blast counts against a hex. Deliberately larger in magnitude than the
     * value of an enemy: the bot should rather waste the shot than drop it on its own lance.
     */
    private static final double FRIENDLY_VALUE_MULTIPLIER = -3.0;

    /**
     * Picks the hex worth bombarding, if any is.
     *
     * @param owner   The bot choosing a target
     * @param support The orbital support available to it, which supplies the damage and blast radius
     *
     * @return The chosen hex, or empty when nothing on the board is worth a strike right now
     */
    public Optional<Coords> selectTarget(Princess owner, OrbitalSupport support) {
        if ((owner == null) || (support == null) || !support.isAvailable()) {
            return Optional.empty();
        }

        // The bot spends its heaviest bay first. A lighter bay held back is worth less later, since the blast radius
        // is the same either way and only the damage differs.
        Optional<OrbitalBay> bay = support.heaviestAvailableBay();
        if (bay.isEmpty()) {
            return Optional.empty();
        }
        int damage = bay.get().damage();
        // 1D6 averages 3.5; 4 is the conservative read, discounting a slow shot a little harder than the mean.
        int turnsToImpact = bay.get().weaponClass().arrivalDelay(4);

        int boardId = owner.getGame().getBoard().getBoardId();
        Set<Coords> candidates = candidateHexes(owner, boardId);
        if (candidates.isEmpty()) {
            LOGGER.info("{}: holding orbital fire - no enemy on the ground to aim at.",
                  owner.getLocalPlayer().getName());
            return Optional.empty();
        }

        double threshold = damage * MINIMUM_VALUE_FRACTION;
        Coords best = null;
        double bestValue = threshold;
        double highestSeen = Double.NEGATIVE_INFINITY;

        for (Coords candidate : candidates) {
            double value = scoreHex(candidate, owner, damage, turnsToImpact, boardId);
            highestSeen = Math.max(highestSeen, value);
            if (value > bestValue) {
                bestValue = value;
                best = candidate;
            }
        }

        // Logged either way: "the bot did not fire" is otherwise indistinguishable from "the bot never looked", and
        // the numbers say which of the two it was.
        if (best == null) {
            LOGGER.info("{}: holding orbital fire - best of {} candidate hex(es) scored {}, needs {} "
                        + "(bay {} at {} damage)",
                  owner.getLocalPlayer().getName(), candidates.size(), String.format("%.1f", highestSeen),
                  String.format("%.1f", threshold), bay.get().name(), damage);
        } else {
            LOGGER.info("{}: calling orbital strike on {} - scored {}, needed {} (bay {} at {} damage)",
                  owner.getLocalPlayer().getName(), best.getBoardNum(), String.format("%.1f", bestValue),
                  String.format("%.1f", threshold), bay.get().name(), damage);
        }

        return Optional.ofNullable(best);
    }

    /**
     * Every hex an enemy is standing in is a candidate aim point. Airborne units are skipped because the bombardment
     * resolves against the ground, and carried units are hit through their transport rather than on their own.
     */
    private Set<Coords> candidateHexes(Princess owner, int boardId) {
        Set<Coords> candidates = new LinkedHashSet<>();

        for (Entity enemy : owner.getEnemyEntities()) {
            if (enemy.isAirborne() || enemy.isAirborneVTOLorWIGE() || (enemy.getTransportId() != Entity.NONE)) {
                continue;
            }
            if (!enemy.isOnBoard(boardId) || (enemy.getPosition() == null)) {
                continue;
            }
            candidates.add(enemy.getPosition());
        }

        return candidates;
    }

    /**
     * Scores a whole blast centred on the given hex, walking outwards ring by ring with the same linear falloff the
     * server applies when the bombardment lands.
     */
    private double scoreHex(Coords center, Princess owner, int damage, int turnsToImpact, int boardId) {
        // The server degrades damage by damage/(radius + 1) per hex of distance. At the fixed radius of 4 that
        // reproduces the book's 10/8/6/4/2 falloff exactly, so mirroring it here keeps the bot honest about how
        // little reaches the edge of the blast.
        int degradation = Math.max(1, damage / (OrbitalSupport.BLAST_RADIUS + 1));
        double value = 0;

        for (int distance = 0; distance <= OrbitalSupport.BLAST_RADIUS; distance++) {
            int damageAtDistance = damage - (distance * degradation);
            if (damageAtDistance <= 0) {
                break;
            }

            List<Coords> ring = (distance == 0) ? List.of(center) : center.allAtDistance(distance);
            for (Coords coords : ring) {
                value += scoreSingleHex(damageAtDistance, coords, owner, turnsToImpact, boardId);
            }
        }

        return value;
    }

    /**
     * Scores one hex of the blast: each unit in it contributes the incoming damage, discounted by how easily it could
     * run clear, and signed by whose side it is on. Enemies already broken and withdrawing are worth nothing, so a
     * strike is not spent finishing off a force that has given up, unless that enemy has itself been dishonorable.
     */
    private double scoreSingleHex(int damage, Coords coords, Princess owner, int turnsToImpact, int boardId) {
        Player me = owner.getLocalPlayer();
        double value = 0;

        for (Entity entity : owner.getGame().getEntitiesVector(coords, boardId, true)) {
            if (entity.isAirborne() || entity.isAirborneVTOLorWIGE() || (entity.getTransportId() != Entity.NONE)) {
                continue;
            }

            double sideMultiplier;
            if (entity.getOwner().isEnemyOf(me)) {
                boolean broken = owner.getHonorUtil().isEnemyBroken(entity.getId(),
                      me.getId(),
                      owner.getBehaviorSettings().isForcedWithdrawal());
                boolean dishonored = owner.getHonorUtil().isEnemyDishonored(entity.getOwnerId());
                sideMultiplier = (!broken || dishonored) ? 1.0 : 0.0;
            } else {
                sideMultiplier = FRIENDLY_VALUE_MULTIPLIER;
            }

            // Movement only saves a unit if it can get clear of the blast, and the blast is four hexes across the
            // radius. Discounting by raw speed treats a unit that runs three hexes as having escaped a nine-hex-wide
            // explosion, which it plainly has not. What matters is how far it can travel before the shot lands,
            // minus the radius it has to clear - so an energy bay arriving this turn is not discounted at all,
            // because nothing moves between the call and the impact.
            int reach = entity.getRunMP() * turnsToImpact;
            int escape = Math.max(0, reach - OrbitalSupport.BLAST_RADIUS);
            double speedMultiplier = 1.0 / (1.0 + escape);
            value += damage * speedMultiplier * sideMultiplier;
        }

        return value;
    }
}
