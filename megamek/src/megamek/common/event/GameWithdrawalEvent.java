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
 * Fired on the server's game when a bot reports one of its units changing course under forced withdrawal: a crippled
 * unit starting to withdraw, or a withdrawing unit that was shot at and may now return fire as it pulls back. Forced
 * withdrawal is a bot behaviour, not something the server tracks, so the bot announces it with
 * {@code /withdrawal}.
 */
public class GameWithdrawalEvent extends GameEvent {
    @Serial
    private static final long serialVersionUID = 1L;
    private final int entityId;
    private final boolean returningFire;

    /**
     * @param source        event source
     * @param entityId      the unit that changed course
     * @param returningFire {@code true} if a withdrawing unit may now return fire, {@code false} if the unit has just
     *                      started to withdraw
     */
    public GameWithdrawalEvent(Object source, int entityId, boolean returningFire) {
        super(source);
        this.entityId = entityId;
        this.returningFire = returningFire;
    }

    /** @return the unit that changed course */
    public int getEntityId() {
        return entityId;
    }

    /** @return whether a withdrawing unit may now return fire, rather than having just started to withdraw */
    public boolean isReturningFire() {
        return returningFire;
    }

    @Override
    public void fireEvent(GameListener gl) {
        gl.gameWithdrawal(this);
    }

    @Override
    public String getEventName() {
        return "Withdrawal";
    }
}
