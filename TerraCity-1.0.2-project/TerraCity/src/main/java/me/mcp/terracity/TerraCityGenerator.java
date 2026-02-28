package me.mcp.terracity;

import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class TerraCityGenerator extends ChunkGenerator {

    private final TerraCityPlugin plugin;
    private final TerrainSampler terrain;
    private final CityPlanner planner;
    private final CityStructures structures;

    private int seaLevel;
    private int snowLine;

    private int roadSpacing;
    private int roadWidth;
    private Material roadMat;
    private Material sidewalkMat;

    private int plotSize;
    private int plotMargin;
    private int maxBuildingsPerChunk;

    public TerraCityGenerator(TerraCityPlugin plugin) {
        this.plugin = plugin;
        this.terrain = new TerrainSampler(plugin);
        this.planner = new CityPlanner(plugin);
        this.structures = new CityStructures(plugin);
        reload();
    }

    public CityPlanner getPlanner() { return planner; }

    public void reload() {
        terrain.reload();
        planner.reload();
        structures.reload();

        FileConfiguration c = plugin.getConfig();
        seaLevel = c.getInt("terrain.sea-level", 63);
        snowLine = c.getInt("terrain.snow-line", 150);

        roadSpacing = Math.max(16, c.getInt("city.road.spacing", 32));
        roadWidth = Math.max(1, c.getInt("city.road.width", 5));
        roadMat = matOr(c.getString("city.road.material", "STONE_BRICKS"), Material.STONE_BRICKS);
        sidewalkMat = matOr(c.getString("city.road.sidewalk", "ANDESITE"), Material.ANDESITE);

        plotSize = Math.max(10, c.getInt("city.plots.size", 16));
        plotMargin = Math.max(0, c.getInt("city.plots.margin", 2));
        maxBuildingsPerChunk = Math.max(0, c.getInt("city.buildings.max-per-chunk", 2));
    }

    @Override
    public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return true; }

    @Override
    public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return true; }

    @Override
    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return false; }

    @Override
    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return sampleHeight(worldInfo, x, z);
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = worldInfo.getSeed();
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();

        int midX = (chunkX << 4) + 8;
        int midZ = (chunkZ << 4) + 8;
        CityPlanner.City city = planner.cityAt(seed, midX, midZ, terrain, seaLevel);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = (chunkX << 4) + lx;
                int wz = (chunkZ << 4) + lz;

                int naturalH = terrain.height(seed, wx, wz);
                int h = naturalH;

                if (city != null) {
                    double t = planner.cityBlendFactor(city, wx, wz);
                    if (t > 0.0) h = lerpInt(naturalH, city.baseHeight(), t);
                }

                h = clamp(h, minY + 1, maxY - 1);

                for (int y = minY; y <= h; y++) {
                    data.setBlock(lx, y, lz, Material.STONE);
                }

                if (h < seaLevel) {
                    for (int y = h + 1; y <= seaLevel; y++) {
                        data.setBlock(lx, y, lz, Material.WATER);
                    }
                }

                // Rivers
                double river = terrain.riverMask(seed, wx, wz);
                if (river > 0.55 && h >= seaLevel - 2) {
                    int waterTop = Math.min(maxY - 1, h + 2);
                    for (int y = h + 1; y <= waterTop; y++) {
                        data.setBlock(lx, y, lz, Material.WATER);
                    }
                }

                // Volcano crater lava
                TerrainSampler.Volcano v = terrain.volcanoAt(seed, wx, wz);
                if (v != null) {
                    double crater = v.craterFactorAt(wx, wz);
                    if (crater > 0.55) {
                        int lava = clamp(terrain.volcanoLavaLevel(), minY + 1, maxY - 1);
                        for (int y = h + 1; y <= lava; y++) {
                            data.setBlock(lx, y, lz, Material.LAVA);
                        }
                    }
                }
            }
        }
    }


    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = worldInfo.getSeed();
        int minY = worldInfo.getMinHeight();
        int maxY = worldInfo.getMaxHeight();

        int midX = (chunkX << 4) + 8;
        int midZ = (chunkZ << 4) + 8;
        CityPlanner.City city = planner.cityAt(seed, midX, midZ, terrain, seaLevel);

        double tScale = plugin.getConfig().getDouble("biomes.temperature-scale", 0.0023);
        double hScale = plugin.getConfig().getDouble("biomes.humidity-scale", 0.0020);

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = (chunkX << 4) + lx;
                int wz = (chunkZ << 4) + lz;

                int topY = findTopSolidY(data, lx, lz, minY, maxY);
                if (topY < minY) continue;

                Material top = Material.GRASS_BLOCK;
                Material under = Material.DIRT;

                double temp = terrain.temperature(seed, wx, wz, tScale);
                double hum = terrain.humidity(seed, wx, wz, hScale);

                boolean desert = temp > 0.35 && hum < 0.15;
                boolean snowy = temp < -0.35 || topY >= snowLine;

                if (topY <= seaLevel + 2) { top = Material.SAND; under = Material.SANDSTONE; }
                else if (desert) { top = Material.SAND; under = Material.SANDSTONE; }
                else if (snowy) { top = Material.SNOW_BLOCK; under = Material.DIRT; }
                else if (hum > 0.45 && temp < 0.2) { top = Material.PODZOL; under = Material.DIRT; }

                double steep = localSteepness(seed, wx, wz);
                if (steep >= terrain.cliffThreshold() && topY > seaLevel + 10) {
                    top = Material.STONE;
                    under = Material.STONE;
                }

                // Volcano surface palette
                TerrainSampler.Volcano v = terrain.volcanoAt(seed, wx, wz);
                if (v != null) {
                    double vf = v.factorAt(wx, wz);
                    if (vf > 0.15 && topY > seaLevel + 6) {
                        top = (vf > 0.65) ? Material.BASALT : Material.STONE;
                        under = Material.STONE;
                        if (vf > 0.78) top = Material.BLACKSTONE;
                    }
                }

                // Rivers: soften banks and add gravel/sand
                double river = terrain.riverMask(seed, wx, wz);
                if (river > 0.55 && topY >= seaLevel - 2) {
                    top = (topY <= seaLevel + 1) ? Material.SAND : Material.GRAVEL;
                    under = Material.DIRT;
                }

                data.setBlock(lx, topY, lz, top);
                for (int y = topY - 1; y >= topY - 3; y--) {
                    if (y < minY) break;
                    Material cur = data.getType(lx, y, lz);
                    if (cur == Material.STONE) data.setBlock(lx, y, lz, under);
                }

                if (city != null) {
                    double blend = planner.cityBlendFactor(city, wx, wz);
                    if (blend > 0.0) {
                        boolean road = isRoad(city, wx, wz);
                        Material surf = road ? roadMat : Material.GRASS_BLOCK;

                        if (!road && isNearRoad(city, wx, wz, 2)) surf = sidewalkMat;
                        if (blend >= 0.85 && !road) surf = sidewalkMat;
                        if (surf == Material.SNOW_BLOCK) surf = sidewalkMat;

                        data.setBlock(lx, topY, lz, surf);
                    }
                }
            }
        }

        if (city != null && maxBuildingsPerChunk > 0) {
            structures.generateBuildingsInChunk(worldInfo, data, chunkX, chunkZ, city, plotSize, plotMargin, roadSpacing, roadWidth, maxBuildingsPerChunk);
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new CustomTreePopulator(plugin, terrain, planner));
    }

    private int sampleHeight(WorldInfo worldInfo, int x, int z) {
        long seed = worldInfo.getSeed();
        CityPlanner.City city = planner.cityAt(seed, x, z, terrain, seaLevel);
        int natural = terrain.height(seed, x, z);
        if (city == null) return natural;
        double t = planner.cityBlendFactor(city, x, z);
        if (t <= 0.0) return natural;
        return lerpInt(natural, city.baseHeight(), t);
    }

    private boolean isRoad(CityPlanner.City city, int x, int z) {
        int rx = x - city.centerX();
        int rz = z - city.centerZ();
        int half = roadWidth / 2;

        int mx = mod(Math.abs(rx), roadSpacing);
        int mz = mod(Math.abs(rz), roadSpacing);

        return mx <= half || mz <= half;
    }

    private boolean isNearRoad(CityPlanner.City city, int x, int z, int dist) {
        int rx = x - city.centerX();
        int rz = z - city.centerZ();

        int mx = mod(Math.abs(rx), roadSpacing);
        int mz = mod(Math.abs(rz), roadSpacing);

        int half = roadWidth / 2;
        return mx <= half + dist || mz <= half + dist;
    }

    private double localSteepness(long seed, int x, int z) {
        int h = terrain.height(seed, x, z);
        int hx = terrain.height(seed, x + 6, z);
        int hz = terrain.height(seed, x, z + 6);
        int hxm = terrain.height(seed, x - 6, z);
        int hzm = terrain.height(seed, x, z - 6);

        int dx = Math.max(Math.abs(hx - h), Math.abs(hxm - h));
        int dz = Math.max(Math.abs(hz - h), Math.abs(hzm - h));

        double s = (dx + dz) / 60.0;
        return clamp01(s);
    }

    private static int findTopSolidY(ChunkData data, int lx, int lz, int minY, int maxY) {
        for (int y = maxY - 1; y >= minY; y--) {
            Material m = data.getType(lx, y, lz);
            if (m != Material.AIR && m != Material.WATER) return y;
        }
        return minY - 1;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static int lerpInt(int a, int b, double t) { return (int)Math.round(a + (b - a) * t); }

    private static int mod(int a, int m) { int r = a % m; return r < 0 ? r + m : r; }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private static Material matOr(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return (m == null) ? fallback : m;
    }
}
