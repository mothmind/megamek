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
package megamek.common.event;

import java.io.Serial;

/**
 * Fired on the server's game when a player declares defeat with {@code /defeat}: either offering to surrender, or
 * admitting defeat once an opponent has declared victory. A player who repeats the command fires it again; listeners
 * that only care about the first surrender keep track themselves.
 */
public class GameSurrenderEvent extends GameEvent {
    @Serial
    private static final long serialVersionUID = 1L;
    private final int playerId;
    private final boolean admitted;

    /**
     * @param source   event source
     * @param playerId the player who declared defeat
     * @param admitted whether an opponent had already declared victory, so this admits defeat rather than offers it
     */
    public GameSurrenderEvent(Object source, int playerId, boolean admitted) {
        super(source);
        this.playerId = playerId;
        this.admitted = admitted;
    }

    /** @return the player who declared defeat */
    public int getPlayerId() {
        return playerId;
    }

    /** @return whether this admits defeat to a declared victory, rather than offers to surrender */
    public boolean isAdmitted() {
        return admitted;
    }

    @Override
    public void fireEvent(GameListener gl) {
        gl.gameSurrender(this);
    }

    @Override
    public String getEventName() {
        return "Surrender";
    }
}
