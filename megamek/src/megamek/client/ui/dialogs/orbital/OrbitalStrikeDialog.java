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
package megamek.client.ui.dialogs.orbital;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

import megamek.client.Client;
import megamek.client.ui.Messages;
import megamek.client.ui.clientGUI.ClientGUI;
import megamek.common.OrbitalBay;
import megamek.common.OrbitalSupport;
import megamek.common.board.Coords;

/**
 * A window for calling orbital bombardments, kept separate from the main display in the way the minimap is.
 *
 * <p>The unit displays are built around commanding one unit at a time, and an orbital strike belongs to none of them:
 * the ship is in orbit and was never deployed. So the player gets their own window listing the bays their supporting
 * vessel still has loaded, and fires one at a hex during the targeting phase, alongside artillery.</p>
 *
 * <p>The window asks and the server decides. Every rule - whether the option is on, whether a bay is still loaded,
 * whether the hex is on the board, whether it is the right phase - is enforced server-side, so nothing here can talk
 * the game into a strike the player has not got.</p>
 */
public class OrbitalStrikeDialog extends JDialog {

    private final ClientGUI clientGUI;
    private final DefaultListModel<String> bayModel = new DefaultListModel<>();
    private final JList<String> bayList = new JList<>(bayModel);
    private final JLabel lblShip = new JLabel();
    private final JSpinner spnX = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner spnY = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JButton btnFire = new JButton(Messages.getString("OrbitalStrikeDialog.fire"));
    private final JLabel lblStatus = new JLabel();

    private List<OrbitalBay> available = List.of();

    public OrbitalStrikeDialog(JFrame frame, ClientGUI clientGUI) {
        super(frame, Messages.getString("OrbitalStrikeDialog.title"), false);
        this.clientGUI = clientGUI;

        bayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bayList.setVisibleRowCount(6);

        JPanel target = new JPanel(new GridLayout(2, 2, 6, 4));
        target.add(new JLabel(Messages.getString("OrbitalStrikeDialog.hexX")));
        target.add(spnX);
        target.add(new JLabel(Messages.getString("OrbitalStrikeDialog.hexY")));
        target.add(spnY);

        JPanel south = new JPanel(new BorderLayout(6, 4));
        south.add(target, BorderLayout.CENTER);
        south.add(btnFire, BorderLayout.SOUTH);

        add(lblShip, BorderLayout.NORTH);
        add(new JScrollPane(bayList), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        add(lblStatus, BorderLayout.EAST);

        btnFire.addActionListener(e -> fire());

        setSize(340, 300);
        setLocationRelativeTo(frame);
        update();
    }

    /**
     * Rebuilds the bay list from the player's current support and enables firing only when a strike is actually
     * legal, so the button is not offered in a phase the server would refuse.
     */
    public void update() {
        Client client = clientGUI.getClient();
        if ((client == null) || (client.getLocalPlayer() == null)) {
            return;
        }

        OrbitalSupport support = client.getLocalPlayer().getOrbitalSupport();
        available = support.availableBays();

        bayModel.clear();
        for (OrbitalBay bay : available) {
            bayModel.addElement(Messages.getString("OrbitalStrikeDialog.bayEntry",
                  bay.name(),
                  bay.damage(),
                  arrivalText(bay)));
        }
        if (!available.isEmpty() && (bayList.getSelectedIndex() < 0)) {
            bayList.setSelectedIndex(0);
        }

        lblShip.setText(support.isAvailable()
              ? Messages.getString("OrbitalStrikeDialog.ship", support.shipName(), support.gunnery())
              : Messages.getString("OrbitalStrikeDialog.noShip"));

        boolean targeting = client.getGame().getPhase().isTargeting();
        btnFire.setEnabled(support.isAvailable() && targeting);
        lblStatus.setText(targeting ? "" : Messages.getString("OrbitalStrikeDialog.notTargeting"));
    }

    /** @return A short note on when this bay's fire would land, since that is the risk in calling it. */
    private String arrivalText(OrbitalBay bay) {
        return switch (bay.weaponClass()) {
            case ENERGY -> Messages.getString("OrbitalStrikeDialog.arrival.energy");
            case BALLISTIC -> Messages.getString("OrbitalStrikeDialog.arrival.ballistic");
            case CAPITAL_MISSILE -> Messages.getString("OrbitalStrikeDialog.arrival.missile");
        };
    }

    private void fire() {
        int index = bayList.getSelectedIndex();
        if ((index < 0) || (index >= available.size())) {
            return;
        }

        // Board numbers shown to players are 1-based, internal coordinates are 0-based.
        Coords target = new Coords((int) spnX.getValue() - 1, (int) spnY.getValue() - 1);
        clientGUI.getClient().sendOrbitalStrike(target, available.get(index).name());

        // The bay list refreshes when the server sends the player update back, so nothing is assumed spent here.
        btnFire.setEnabled(false);
    }
}
