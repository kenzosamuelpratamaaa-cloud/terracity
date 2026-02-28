package me.mcp.terracity;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

/**
 * Lightweight custom flora: places simple custom trees (palm, pine, jungle) based on temperature/humidity
 * and proximity to water. This runs after chunk generation.
 */
public class CustomFloraPopulator extends BlockPopulator {

    private final TerraCityPlugin plugin;
    private final TerrainSampler terrain;

    public CustomFloraPopulator(TerraCityPlugin plugin, TerrainSampler terrain) {
        this.plugin = plugin;
        this.terrain = terrain;
    }

    @Override
    public void populate(World world, Random random, org.bukkit.Chunk chunk) {
        long seed = world.getSeed();

        double tScale = plugin.getConfig().getDouble("biomes.temperature-scale", 0.0023);
        double hScale = plugin.getConfig().getDouble("biomes.humidity-scale", 0.0020);

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        int attempts = plugin.getConfig().getInt("flora.attempts-per-chunk", 4);
        for (int i = 0; i < attempts; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);

            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() + 2 || y >= world.getMaxHeight() - 10) continue;

            Block ground = world.getBlockAt(x, y - 1, z);
            Material g = ground.getType();

            double temp = terrain.temperature(seed, x, z, tScale);
            double hum = terrain.humidity(seed, x, z, hScale);

            // Beaches: palm trees on sand near water
            if ((g == Material.SAND || g == Material.SANDSTONE) && isNearWater(world, x, y, z, 6)) {
                if (random.nextDouble() < plugin.getConfig().getDouble("flora.palm.chance", 0.45)) {
                    placePalm(world, random, x, y, z);
                    continue;
                }
            }

            // Cold: pine/spruce
            if (temp < -0.25 && g == Material.GRASS_BLOCK) {
                if (random.nextDouble() < plugin.getConfig().getDouble("flora.pine.chance", 0.30)) {
                    placePine(world, random, x, y, z);
                    continue;
                }
            }

            // Humid warm: jungle tree
            if (temp > 0.20 && hum > 0.35 && g == Material.GRASS_BLOCK) {
                if (random.nextDouble() < plugin.getConfig().getDouble("flora.jungle.chance", 0.25)) {
                    placeJungle(world, random, x, y, z);
                }
            }
        }
    }

    private boolean isNearWater(World world, int x, int y, int z, int r) {
        int minY = Math.max(world.getMinHeight(), y - 2);
        int maxY = Math.min(world.getMaxHeight() - 1, y + 2);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = minY; dy <= maxY; dy++) {
                    Material m = world.getBlockAt(x + dx, dy, z + dz).getType();
                    if (m == Material.WATER) return true;
                }
            }
        }
        return false;
    }

    private void placePalm(World world, Random random, int x, int y, int z) {
        int h = 5 + random.nextInt(4);
        int lean = random.nextInt(3) - 1; // -1..1
        int lx = x;
        int lz = z;

        for (int i = 0; i < h; i++) {
            Block b = world.getBlockAt(lx, y + i, lz);
            if (!b.getType().isAir()) return;
            b.setType(Material.JUNGLE_LOG, false);
            if (i > 2) { lx += lean; lz += (lean == 0 ? (random.nextBoolean() ? 1 : -1) : 0); }
        }

        // leaves crown
        int topY = y + h;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 4) continue;
                Block b = world.getBlockAt(lx + dx, topY, lz + dz);
                if (b.getType().isAir()) b.setType(Material.JUNGLE_LEAVES, false);
            }
        }
        world.getBlockAt(lx, topY + 1, lz).setType(Material.JUNGLE_LEAVES, false);
    }

    private void placePine(World world, Random random, int x, int y, int z) {
        int h = 8 + random.nextInt(6);
        for (int i = 0; i < h; i++) {
            Block b = world.getBlockAt(x, y + i, z);
            if (!b.getType().isAir()) return;
            b.setType(Material.SPRUCE_LOG, false);
        }
        int top = y + h - 1;
        int radius = 3;
        for (int yy = top; yy >= y + 3; yy--) {
            int r = radius - (top - yy) / 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > r + 1) continue;
                    Block b = world.getBlockAt(x + dx, yy, z + dz);
                    if (b.getType().isAir()) b.setType(Material.SPRUCE_LEAVES, false);
                }
            }
        }
        world.getBlockAt(x, top + 1, z).setType(Material.SPRUCE_LEAVES, false);
    }

    private void placeJungle(World world, Random random, int x, int y, int z) {
        int h = 9 + random.nextInt(5);
        for (int i = 0; i < h; i++) {
            Block b = world.getBlockAt(x, y + i, z);
            if (!b.getType().isAir()) return;
            b.setType(Material.JUNGLE_LOG, false);
        }
        int top = y + h;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    if (dx*dx + dz*dz + dy*dy > 12) continue;
                    Block b = world.getBlockAt(x + dx, top + dy, z + dz);
                    if (b.getType().isAir()) b.setType(Material.JUNGLE_LEAVES, false);
                }
            }
        }
    }
}
