package me.mcp.terracity;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.WorldInfo;

import java.util.Locale;
import java.util.Random;

public class CityStructures {

    private final TerraCityPlugin plugin;

    private Material wall, floor, roof, trim, window, stone;

    public CityStructures(TerraCityPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection p = c.getConfigurationSection("city.buildings.palette");
        wall = matOr(p, "wall", Material.SPRUCE_PLANKS);
        floor = matOr(p, "floor", Material.OAK_PLANKS);
        roof = matOr(p, "roof", Material.DARK_OAK_PLANKS);
        trim = matOr(p, "trim", Material.STRIPPED_SPRUCE_LOG);
        window = matOr(p, "window", Material.GLASS);
        stone = matOr(p, "stone", Material.STONE_BRICKS);
    }

    public void generateBuildingsInChunk(WorldInfo worldInfo,
                                         org.bukkit.generator.ChunkGenerator.ChunkData data,
                                         int chunkX, int chunkZ,
                                         CityPlanner.City city,
                                         int plotSize, int plotMargin,
                                         int roadSpacing, int roadWidth,
                                         int maxBuildingsPerChunk) {

        long seed = worldInfo.getSeed();

        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;

        int built = 0;

        for (int wx = chunkMinX; wx < chunkMinX + 16; wx++) {
            for (int wz = chunkMinZ; wz < chunkMinZ + 16; wz++) {

                if (!isPlotOrigin(city, wx, wz, plotSize)) continue;
                if (!insideCityCore(city, wx, wz)) continue;
                if (nearRoad(city, wx, wz, roadSpacing, roadWidth, plotMargin)) continue;

                Random r = new Random(hash(seed, wx, wz));
                if (r.nextDouble() > 0.55) continue;

                int localX = wx - chunkMinX;
                int localZ = wz - chunkMinZ;

                int groundY = findTopSolidY(data, localX, localZ, worldInfo.getMinHeight(), worldInfo.getMaxHeight());
                if (groundY < worldInfo.getMinHeight() + 2) continue;

                int size = Math.max(9, Math.min(plotSize - 2, 13));
                int x0 = localX + 1;
                int z0 = localZ + 1;

                if (x0 + size >= 15 || z0 + size >= 15) continue;

                int type = r.nextInt(100);
                if (type < 60) {
                    buildHouse(data, x0, groundY + 1, z0, size, r);
                } else if (type < 85) {
                    buildTower(data, x0 + size/2, groundY + 1, z0 + size/2, 4 + r.nextInt(2), 14 + r.nextInt(10), r);
                } else {
                    buildHall(data, x0, groundY + 1, z0, size, r);
                }

                built++;
                if (built >= maxBuildingsPerChunk) return;
            }
        }
    }

    private boolean insideCityCore(CityPlanner.City city, int x, int z) {
        int dx = x - city.centerX();
        int dz = z - city.centerZ();
        double dist = Math.sqrt((double)dx*dx + (double)dz*dz);
        return dist <= (city.radius() - Math.max(8, city.blend()/2));
    }

    private boolean isPlotOrigin(CityPlanner.City city, int x, int z, int plotSize) {
        int rx = x - city.centerX();
        int rz = z - city.centerZ();
        return mod(rx, plotSize) == 0 && mod(rz, plotSize) == 0;
    }

    private boolean nearRoad(CityPlanner.City city, int x, int z, int roadSpacing, int roadWidth, int margin) {
        int rx = x - city.centerX();
        int rz = z - city.centerZ();

        int half = roadWidth / 2;

        int mx = mod(Math.abs(rx), roadSpacing);
        int mz = mod(Math.abs(rz), roadSpacing);

        return mx <= half + margin || mz <= half + margin;
    }

    private void buildHouse(org.bukkit.generator.ChunkGenerator.ChunkData data, int x0, int y0, int z0, int size, Random r) {
        int h = 4 + r.nextInt(3);

        fill(data, x0, y0 - 1, z0, x0 + size - 1, y0 - 1, z0 + size - 1, floor);
        hollowBox(data, x0, y0, z0, x0 + size - 1, y0 + h, z0 + size - 1, wall);

        for (int dy = 0; dy <= h; dy++) {
            data.setBlock(x0, y0 + dy, z0, trim);
            data.setBlock(x0 + size - 1, y0 + dy, z0, trim);
            data.setBlock(x0, y0 + dy, z0 + size - 1, trim);
            data.setBlock(x0 + size - 1, y0 + dy, z0 + size - 1, trim);
        }

        int layers = Math.max(2, size / 3);
        for (int i = 0; i < layers; i++) {
            fill(data,
                    x0 + i, y0 + h + i, z0 + i,
                    x0 + size - 1 - i, y0 + h + i, z0 + size - 1 - i,
                    roof);
        }

        int doorX = x0 + size / 2;
        data.setBlock(doorX, y0, z0, Material.AIR);
        data.setBlock(doorX, y0 + 1, z0, Material.AIR);

        for (int dx = 2; dx < size - 2; dx += 3) {
            data.setBlock(x0 + dx, y0 + 2, z0, window);
            data.setBlock(x0 + dx, y0 + 2, z0 + size - 1, window);
        }
        for (int dz = 2; dz < size - 2; dz += 3) {
            data.setBlock(x0, y0 + 2, z0 + dz, window);
            data.setBlock(x0 + size - 1, y0 + 2, z0 + dz, window);
        }

        data.setBlock(x0 + 1, y0 + 3, z0 + 1, Material.LANTERN);
        data.setBlock(x0 + size - 2, y0 + 3, z0 + 1, Material.LANTERN);
        data.setBlock(x0 + 1, y0 + 3, z0 + size - 2, Material.LANTERN);
        data.setBlock(x0 + size - 2, y0 + 3, z0 + size - 2, Material.LANTERN);
    }

    private void buildTower(org.bukkit.generator.ChunkGenerator.ChunkData data, int cx, int y0, int cz, int radius, int height, Random r) {
        for (int y = 0; y <= height; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int d2 = dx*dx + dz*dz;
                    if (d2 > radius*radius) continue;

                    int x = cx + dx;
                    int z = cz + dz;

                    boolean edge = d2 >= (radius-1)*(radius-1);
                    if (edge) data.setBlock(x, y0 + y, z, stone);
                    else if (y == 0) data.setBlock(x, y0 + y, z, floor);
                    else if (y < height - 1) data.setBlock(x, y0 + y, z, Material.AIR);
                    else data.setBlock(x, y0 + y, z, roof);
                }
            }
        }

        for (int y = y0 + 3; y < y0 + height - 2; y += 4) {
            data.setBlock(cx + radius, y, cz, window);
            data.setBlock(cx - radius, y, cz, window);
            data.setBlock(cx, y, cz + radius, window);
            data.setBlock(cx, y, cz - radius, window);
        }

        data.setBlock(cx, y0 + height + 1, cz, Material.LANTERN);
    }

    private void buildHall(org.bukkit.generator.ChunkGenerator.ChunkData data, int x0, int y0, int z0, int size, Random r) {
        int w = Math.max(11, size);
        int d = Math.max(9, size - 2);
        int h = 5 + r.nextInt(3);

        fill(data, x0, y0 - 1, z0, x0 + w - 1, y0 - 1, z0 + d - 1, floor);
        hollowBox(data, x0, y0, z0, x0 + w - 1, y0 + h, z0 + d - 1, stone);

        fill(data, x0, y0 + h + 1, z0, x0 + w - 1, y0 + h + 1, z0 + d - 1, roof);
        for (int x = x0; x < x0 + w; x++) {
            data.setBlock(x, y0 + h + 2, z0, trim);
            data.setBlock(x, y0 + h + 2, z0 + d - 1, trim);
        }
        for (int z = z0; z < z0 + d; z++) {
            data.setBlock(x0, y0 + h + 2, z, trim);
            data.setBlock(x0 + w - 1, y0 + h + 2, z, trim);
        }

        for (int x = x0 + 2; x < x0 + w - 2; x += 3) {
            data.setBlock(x, y0 + 2, z0, window);
            data.setBlock(x, y0 + 2, z0 + d - 1, window);
        }

        int doorX = x0 + w / 2;
        data.setBlock(doorX, y0, z0, Material.AIR);
        data.setBlock(doorX, y0 + 1, z0, Material.AIR);

        data.setBlock(x0 + w/2, y0 + h, z0 + d/2, Material.LANTERN);
    }

    private static void fill(org.bukkit.generator.ChunkGenerator.ChunkData data,
                             int x1, int y1, int z1,
                             int x2, int y2, int z2,
                             Material m) {
        int xa = Math.min(x1, x2), xb = Math.max(x1, x2);
        int ya = Math.min(y1, y2), yb = Math.max(y1, y2);
        int za = Math.min(z1, z2), zb = Math.max(z1, z2);

        for (int x = xa; x <= xb; x++)
            for (int y = ya; y <= yb; y++)
                for (int z = za; z <= zb; z++)
                    data.setBlock(x, y, z, m);
    }

    private static void hollowBox(org.bukkit.generator.ChunkGenerator.ChunkData data,
                                  int x1, int y1, int z1,
                                  int x2, int y2, int z2,
                                  Material wall) {
        int xa = Math.min(x1, x2), xb = Math.max(x1, x2);
        int ya = Math.min(y1, y2), yb = Math.max(y1, y2);
        int za = Math.min(z1, z2), zb = Math.max(z1, z2);

        for (int x = xa; x <= xb; x++) {
            for (int y = ya; y <= yb; y++) {
                for (int z = za; z <= zb; z++) {
                    boolean edge = (x == xa || x == xb || y == ya || y == yb || z == za || z == zb);
                    data.setBlock(x, y, z, edge ? wall : Material.AIR);
                }
            }
        }
    }

    private static int findTopSolidY(org.bukkit.generator.ChunkGenerator.ChunkData data, int lx, int lz, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            Material m = data.getType(lx, y, lz);
            if (m != Material.AIR && m != Material.WATER) return y;
        }
        return minY - 1;
    }

    private static long hash(long seed, int x, int z) {
        long h = seed;
        h ^= (long)x * 0x9E3779B97F4A7C15L;
        h ^= (long)z * 0xC2B2AE3D27D4EB4FL;
        h *= 0x165667B19E3779F9L;
        h ^= (h >>> 32);
        return h;
    }

    private static int mod(int a, int m) {
        int r = a % m;
        return r < 0 ? r + m : r;
    }

    private static Material matOr(ConfigurationSection p, String key, Material fallback) {
        if (p == null) return fallback;
        String name = p.getString(key);
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return (m == null) ? fallback : m;
    }
}
