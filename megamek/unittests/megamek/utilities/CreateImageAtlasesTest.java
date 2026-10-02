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
package megamek.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import megamek.client.ui.tileset.HexTileset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The atlas packer used to place sprites in whatever order the file system listed them, which reshuffled every atlas
 * and the atlas map on every build. These create files out of name order and check they are packed in name order.
 */
class CreateImageAtlasesTest {
    private static final int IMAGES_PER_ROW = 5;

    private static final List<String> CREATION_ORDER = List.of("Kraken.png", "jenner.png", "Zeus.png", "atlas.png",
          "Bane.png", "zephyr.png", "Atlas.png", "marauder.png", "Hunchback.png", "catapult.png", "Jenner.png",
          "awesome.png");

    @Test
    void packsImagesInNameOrderWhateverOrderTheyWereCreatedIn(@TempDir Path tempDir) throws IOException {
        File dir = tempDir.resolve("meks").toFile();
        assertTrue(dir.mkdir());
        for (String name : CREATION_ORDER) {
            writeSolidSprite(new File(dir, name), colourOf(name));
        }

        CreateImageAtlases atlasCreator = new CreateImageAtlases(IMAGES_PER_ROW);
        atlasCreator.processDirectory(dir);

        List<String> byName = CREATION_ORDER.stream().sorted().toList();
        assertEquals(byName, atlasCreator.imagesStored.stream().map(path -> new File(path).getName()).toList());

        BufferedImage atlas = ImageIO.read(new File(dir, "meks_atlas.png"));
        for (int slot = 0; slot < byName.size(); slot++) {
            int x = (slot % IMAGES_PER_ROW) * HexTileset.HEX_W;
            int y = (slot / IMAGES_PER_ROW) * HexTileset.HEX_H;
            String name = byName.get(slot);
            assertEquals(colourOf(name), atlas.getRGB(x + HexTileset.HEX_W / 2, y + HexTileset.HEX_H / 2), name);
            assertTrue(atlasCreator.imgFileToAtlasMap.get(new File(dir, name).toPath())
                  .endsWith("(" + x + "," + y + "-" + HexTileset.HEX_W + "," + HexTileset.HEX_H + ")"), name);
        }
    }

    @Test
    void scansSubdirectoriesInNameOrder(@TempDir Path tempDir) throws IOException {
        List<String> creationOrder = List.of("tanks", "Meks", "aero", "Infantry", "meks", "dropships");
        for (String name : creationOrder) {
            File subDir = tempDir.resolve(name).toFile();
            assertTrue(subDir.mkdir());
            writeSolidSprite(new File(subDir, "unit.png"), colourOf(name));
        }

        CreateImageAtlases atlasCreator = new CreateImageAtlases(IMAGES_PER_ROW);
        atlasCreator.scanDirectory(tempDir.toFile());

        assertEquals(creationOrder.stream().sorted().toList(),
              atlasCreator.imagesStored.stream().map(path -> new File(path).getParentFile().getName()).toList());
    }

    @Test
    void sortsByExactNameRatherThanThePlatformsFileOrdering() {
        File[] files = { new File("b.png"), new File("a.png"), new File("B.png"), new File("A.png") };

        assertEquals(List.of("A.png", "B.png", "a.png", "b.png"),
              Arrays.stream(CreateImageAtlases.byName(files)).map(File::getName).toList());
    }

    private static int colourOf(String name) {
        return 0xFF000000 | ((Math.abs(name.hashCode()) % 0xFFFFFF) + 1);
    }

    private static void writeSolidSprite(File file, int argb) throws IOException {
        BufferedImage sprite = new BufferedImage(HexTileset.HEX_W, HexTileset.HEX_H, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < HexTileset.HEX_W; x++) {
            for (int y = 0; y < HexTileset.HEX_H; y++) {
                sprite.setRGB(x, y, argb);
            }
        }
        ImageIO.write(sprite, "png", file);
    }
}
