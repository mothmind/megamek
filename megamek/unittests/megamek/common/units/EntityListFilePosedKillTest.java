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
package megamek.common.units;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Hashtable;
import java.util.Set;

import megamek.common.loaders.MULParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests that a kill made while posing survives the trip through a MUL file, which is how MekHQ learns of it when a
 * battle is resolved from a saved file.
 */
class EntityListFilePosedKillTest {

    @Test
    @DisplayName("posed kills are written to the MUL and read back, ordinary kills are not marked")
    void posedKillsRoundTrip() throws Exception {
        Hashtable<String, String> kills = new Hashtable<>();
        kills.put("Atlas AS7-D", "posing-killer");
        kills.put("Locust LCT-1V", "ordinary-killer");
        kills.put("Commando COM-2D", MULParser.VALUE_NONE);

        StringWriter output = new StringWriter();
        output.write("<record>\n<" + MULParser.ELE_KILLS + ">\n");
        EntityListFile.writeKills(output, kills, Set.of("Atlas AS7-D"));
        output.write("</" + MULParser.ELE_KILLS + ">\n</record>\n");

        MULParser parser = new MULParser(new ByteArrayInputStream(output.toString().getBytes(UTF_8)), null);

        assertEquals(kills, parser.getKills(), "the kills themselves are unchanged");
        assertEquals(Set.of("Atlas AS7-D"), parser.getPosedKills());
    }

    @Test
    @DisplayName("a MUL written before poses existed reads as having no posed kills")
    void oldMulHasNoPosedKills() throws Exception {
        String mul = """
              <record>
              <kills>
                <kill killed="Atlas AS7-D" killer="some-killer"/>
              </kills>
              </record>
              """;

        MULParser parser = new MULParser(new ByteArrayInputStream(mul.getBytes(UTF_8)), null);

        assertEquals("some-killer", parser.getKills().get("Atlas AS7-D"));
        assertEquals(Set.of(), parser.getPosedKills());
    }
}
