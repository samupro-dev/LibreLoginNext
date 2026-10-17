/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom chunk generator creating an empty void world with a single barrier block at the spawn
 * location.
 */
public class VoidWorldGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(
            @NotNull org.bukkit.generator.WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData) {
        if (chunkX == 0 && chunkZ == 0) {
            chunkData.setBlock(0, 69, 0, Material.BARRIER);
        }
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ChunkData generateChunkData(
            @NotNull World world,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull BiomeGrid biome) {
        ChunkData chunkData = createChunkData(world);
        if (chunkX == 0 && chunkZ == 0) {
            chunkData.setBlock(0, 69, 0, Material.BARRIER);
        }
        return chunkData;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 70, 0.5);
    }
}
