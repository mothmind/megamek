/*
 * Copyright (C) 2024-2025 The MegaMek Team. All Rights Reserved.
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

package megamek.server.props;

import java.util.List;

import megamek.common.Player;
import megamek.common.board.Coords;

/**
 * Represents an orbital bombardment event. x and y are board positions, damageFactor is the damage at impact point
 * times 10, and radius is the blast radius of the explosion with regular/linear damage droppoff.
 *
 * @author Luana Coppio
 */
public class OrbitalBombardment {

    private final int x;
    private final int y;
    private final int damage;
    private final int radius;
    private final Coords coords;
    private final int playerId;
    private final String shipName;
    private final String bayName;
    /** Where the shot was aimed, which is not where it lands when the attack roll missed. */
    private final Coords aimPoint;
    /** Ground turns still to pass before this lands. Counted down once per firing phase. */
    private int turnsUntilImpact;

    /**
     * Represents an orbital bombardment event. x and y are board positions, damageFactor is the damage at impact point
     * times 10, and radius is the blast radius of the explosion with regular/linear damage droppoff.
     */
    private OrbitalBombardment(Builder builder) {
        this.x = builder.x;
        this.y = builder.y;
        this.damage = builder.damage;
        this.radius = builder.radius;
        this.coords = new Coords(x, y);
        this.playerId = builder.playerId;
        this.shipName = builder.shipName;
        this.bayName = builder.bayName;
        this.aimPoint = (builder.aimPoint == null) ? this.coords : builder.aimPoint;
        this.turnsUntilImpact = Math.max(0, builder.turnsUntilImpact);
    }

    public Coords getCoords() {
        return coords;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getDamage() {
        return damage;
    }

    public int getRadius() {
        return radius;
    }

    /**
     * @return The id of the player who called this bombardment, or {@link Player#PLAYER_NONE} when it was called by
     *       a game master rather than by a ship supporting one side.
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * @return The name of the vessel firing, for reports. Empty when no particular ship is credited.
     */
    public String getShipName() {
        return shipName;
    }

    /**
     * @return The naval bay that fired, for reports. Empty when no particular bay is credited, as with a game
     *       master's strike.
     */
    public String getBayName() {
        return bayName;
    }

    /**
     * @return The hex the shot was aimed at. Differs from {@link #getCoords()} when the attack roll missed and the
     *       shot scattered.
     */
    public Coords getAimPoint() {
        return aimPoint;
    }

    /** @return True when the shot drifted off its aim point. */
    public boolean hasScattered() {
        return !aimPoint.equals(coords);
    }

    /** @return Ground turns still to pass before this lands. */
    public int getTurnsUntilImpact() {
        return turnsUntilImpact;
    }

    /** @return True when this shot arrives now. */
    public boolean isDue() {
        return turnsUntilImpact <= 0;
    }

    /** Counts one ground turn off the flight time. */
    public void tickTowardsImpact() {
        if (turnsUntilImpact > 0) {
            turnsUntilImpact--;
        }
    }

    public int getXOffset() {
        return x - radius;
    }

    public int getYOffset() {
        return y - radius;
    }

    public String getImageSignature(Coords boardPosition) {
        var offsetX = boardPosition.getX() - getXOffset();
        var offsetY = boardPosition.getY() - getYOffset();
        var modifier = offsetX % 2 == 0 ? "" : "_odd";
        return String.format("col_%d_row_%d%s.png", offsetX, offsetY, modifier);
    }

    public List<Coords> getAllAffectedCoords() {
        return coords.allAtDistanceOrLess(radius);
    }


    /**
     * Builder of an orbital bombardment event. x and y are board positions, damageFactor is the damage at impact point
     * times 10, and radius is the blast radius of the explosion with regular/linear damage droppoff.
     */
    public static class Builder {
        private int x;
        private int y;
        private int damage = 10;
        private int radius = 4;
        private int playerId = Player.PLAYER_NONE;
        private String shipName = "";
        private String bayName = "";
        private Coords aimPoint = null;
        private int turnsUntilImpact = 0;

        public Builder x(int x) {
            this.x = x;
            return this;
        }

        public Builder y(int y) {
            this.y = y;
            return this;
        }

        public Builder damage(int damage) {
            this.damage = damage;
            return this;
        }

        public Builder radius(int radius) {
            this.radius = radius;
            return this;
        }

        /** Credits the bombardment to a player, so reports can name who called it. */
        public Builder playerId(int playerId) {
            this.playerId = playerId;
            return this;
        }

        /** Credits the bombardment to a named vessel, so reports can name what fired. */
        public Builder shipName(String shipName) {
            this.shipName = (shipName == null) ? "" : shipName;
            return this;
        }

        /** Credits the bombardment to a particular naval bay, so reports can name which guns fired. */
        public Builder bayName(String bayName) {
            this.bayName = (bayName == null) ? "" : bayName;
            return this;
        }

        /** Records where the shot was aimed, so a scattered hit can be reported as one. */
        public Builder aimPoint(Coords aimPoint) {
            this.aimPoint = aimPoint;
            return this;
        }

        /** Sets the flight time in ground turns; 0 lands at the end of the phase it was fired in. */
        public Builder turnsUntilImpact(int turnsUntilImpact) {
            this.turnsUntilImpact = turnsUntilImpact;
            return this;
        }

        /**
         * Builds an orbital bombardment.
         *
         * @return an immutable instance of an orbital bombardment.
         */
        public OrbitalBombardment build() {
            return new OrbitalBombardment(this);
        }
    }
}
