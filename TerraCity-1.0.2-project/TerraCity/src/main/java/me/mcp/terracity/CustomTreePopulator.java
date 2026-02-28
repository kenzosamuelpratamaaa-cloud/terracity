package me.mcp.terracity;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

public class CustomTreePopulator extends BlockPopulator {

    private final TerraCityPlugin plugin;
    private final TerrainSampler terrain;
    private final CityPlanner planner;

    // config
    private boolean enabled;
    private int attemptsPerChunk;
    private int minY;
    private int maxY;

    public CustomTreePopulator(TerraCityPlugin plugin, TerrainSampler terrain, CityPlanner planner) {
        this.plugin = plugin;
        this.terrain = terrain;
        this.planner = planner;
        reload();
    }

    public void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("trees.enabled", true);
        attemptsPerChunk = Math.max(0, c.getInt("trees.attempts-per-chunk", 10));
        minY = c.getInt("trees.min-y", -64);
        maxY = c.getInt("trees.max-y", 320);
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {

        plugin.getLogger().info("[TreePop] populate chunk " + chunk.getX() + "," + chunk.getZ());

        if (!enabled) return;

        final long seed = world.getSeed();
        final int chunkX = chunk.getX();
        final int chunkZ = chunk.getZ();

        Random r = new Random(mix(seed, chunkX, chunkZ, 0xC0FFEE));

        for (int i = 0; i < attemptsPerChunk; i++) {
            int lx = 2 + r.nextInt(12);
            int lz = 2 + r.nextInt(12);

            int x = (chunkX << 4) + lx;
            int z = (chunkZ << 4) + lz;

            if (planner != null && planner.isEnabled()) {
                CityPlanner.City city = planner.cityAt(seed, x, z, terrain, terrain.seaLevel());
                if (city != null && planner.cityBlendFactor(city, x, z) > 0.15) continue;
            }

            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

            if (y < minY || y >= maxY) continue;

            Material ground = world.getBlockAt(x, y - 1, z).getType();
            if (!isGoodGround(ground)) continue;

            if (world.getBlockAt(x, y, z).getType() != Material.AIR) continue;

            double tScale = plugin.getConfig().getDouble("biomes.temperature-scale", 0.0023);
            double hScale = plugin.getConfig().getDouble("biomes.humidity-scale", 0.0020);

            double temp = terrain.temperature(seed, x, z, tScale);
            double hum  = terrain.humidity(seed, x, z, hScale);

            int sea = terrain.seaLevel();
            boolean nearSea = y <= sea + 3;

            TreeType type = pickTreeType(r, temp, hum, y, nearSea);

            generate(world, r, x, y, z, type);
        }
    }


    private boolean isGoodGround(Material m) {
        return m == Material.GRASS_BLOCK
                || m == Material.DIRT
                || m == Material.PODZOL
                || m == Material.MYCELIUM
                || m == Material.SAND
                || m == Material.RED_SAND
                || m == Material.COARSE_DIRT;
    }

    enum TreeType { OAK, BIRCH, PINE, PALM, SWAMP, MUSHROOM_RED, MUSHROOM_BROWN }

    private TreeType pickTreeType(Random r, double temp, double hum, int y, boolean nearSea) {
        // aturan gampang tapi hasilnya kerasa “biome-aware”
        boolean cold = temp < -0.25 || y >= plugin.getConfig().getInt("terrain.snow-line", 150) - 10;
        boolean dry  = hum < 0.10;
        boolean wet  = hum > 0.45;

        if (nearSea && temp > 0.05 && !cold) {
            // pantai = palm dominan
            return r.nextDouble() < 0.80 ? TreeType.PALM : TreeType.OAK;
        }

        if (cold) {
            // dingin = pine
            return r.nextDouble() < 0.85 ? TreeType.PINE : TreeType.BIRCH;
        }

        if (wet && temp > 0.10) {
            // rawa / lembab = swamp + oak
            return r.nextDouble() < 0.65 ? TreeType.SWAMP : TreeType.OAK;
        }

        // “mushroom biome feel” kalau hum tinggi dan agak gelap/lembab
        if (wet && r.nextDouble() < 0.10) {
            return r.nextBoolean() ? TreeType.MUSHROOM_RED : TreeType.MUSHROOM_BROWN;
        }

        // normal forest mix
        double p = r.nextDouble();
        if (p < 0.55) return TreeType.OAK;
        if (p < 0.80) return TreeType.BIRCH;
        return TreeType.PINE;
    }

    private void generate(World w, Random r, int x, int y, int z, TreeType type) {
        switch (type) {
            case OAK -> genOak(w, r, x, y, z);
            case BIRCH -> genBirch(w, r, x, y, z);
            case PINE -> genPine(w, r, x, y, z);
            case PALM -> genPalm(w, r, x, y, z);
            case SWAMP -> genSwamp(w, r, x, y, z);
            case MUSHROOM_RED -> genMushroom(w, r, x, y, z, true);
            case MUSHROOM_BROWN -> genMushroom(w, r, x, y, z, false);
        }
    }

    // ===== TREE SHAPES =====

    private void genOak(World w, Random r, int x, int y, int z) {
        int h = 4 + r.nextInt(4);
        if (!canPlace(w, x, y, z, 2, h + 3)) return;

        setColumn(w, x, y, z, Material.OAK_LOG, h);

        int top = y + h;
        blobLeaves(w, x, top, z, 2, Material.OAK_LEAVES);
        blobLeaves(w, x, top - 1, z, 3, Material.OAK_LEAVES);
        blobLeaves(w, x, top + 1, z, 1, Material.OAK_LEAVES);
    }

    private void genBirch(World w, Random r, int x, int y, int z) {
        int h = 5 + r.nextInt(4);
        if (!canPlace(w, x, y, z, 2, h + 3)) return;

        setColumn(w, x, y, z, Material.BIRCH_LOG, h);

        int top = y + h;
        blobLeaves(w, x, top, z, 2, Material.BIRCH_LEAVES);
        blobLeaves(w, x, top - 1, z, 2, Material.BIRCH_LEAVES);
        blobLeaves(w, x, top + 1, z, 1, Material.BIRCH_LEAVES);
    }

    private void genPine(World w, Random r, int x, int y, int z) {
        int h = 7 + r.nextInt(6);
        if (!canPlace(w, x, y, z, 3, h + 4)) return;

        setColumn(w, x, y, z, Material.SPRUCE_LOG, h);

        int top = y + h;
        int layers = 5 + r.nextInt(3);
        for (int i = 0; i < layers; i++) {
            int ry = top - i;
            int rad = Math.max(1, 3 - (i / 2));
            discLeaves(w, x, ry, z, rad, Material.SPRUCE_LEAVES);
        }
        discLeaves(w, x, top + 1, z, 1, Material.SPRUCE_LEAVES);
    }

    private void genPalm(World w, Random r, int x, int y, int z) {
        int h = 6 + r.nextInt(5);
        if (!canPlace(w, x, y, z, 4, h + 4)) return;

        // batang agak miring
        int dx = r.nextInt(3) - 1;
        int dz = r.nextInt(3) - 1;

        int cx = x, cz = z;
        for (int i = 0; i < h; i++) {
            setIfAirOrLeaves(w, cx, y + i, cz, Material.JUNGLE_LOG);
            if (i > 2 && r.nextDouble() < 0.35) { cx += dx; cz += dz; }
        }

        int topY = y + h;

        // daun “kipas”
        for (int a = 0; a < 6; a++) {
            int fx = cx + (a % 3) - 1;
            int fz = cz + (a / 3) - 1;
            setIfAirOrLeaves(w, fx, topY, fz, Material.JUNGLE_LEAVES);
        }
        // fronds
        lineLeaves(w, cx, topY, cz,  3, 0, Material.JUNGLE_LEAVES);
        lineLeaves(w, cx, topY, cz, -3, 0, Material.JUNGLE_LEAVES);
        lineLeaves(w, cx, topY, cz,  0, 3, Material.JUNGLE_LEAVES);
        lineLeaves(w, cx, topY, cz,  0,-3, Material.JUNGLE_LEAVES);

        // “kelapa” simple (cocoa) optional
        // (hapus kelapa dulu, nanti kita bikin bener pake BlockData)
    }

    private void genSwamp(World w, Random r, int x, int y, int z) {
        int h = 5 + r.nextInt(4);
        if (!canPlace(w, x, y, z, 3, h + 4)) return;

        setColumn(w, x, y, z, Material.OAK_LOG, h);

        int top = y + h;
        blobLeaves(w, x, top, z, 3, Material.OAK_LEAVES);
        blobLeaves(w, x, top - 1, z, 3, Material.OAK_LEAVES);

        // vines simple
        if (r.nextDouble() < 0.60) {
            for (int i = 0; i < 6; i++) {
                int vx = x + r.nextInt(5) - 2;
                int vz = z + r.nextInt(5) - 2;
                int vy = top - r.nextInt(2);
                for (int d = 0; d < 4; d++) {
                    if (w.getBlockAt(vx, vy - d, vz).getType() == Material.AIR)
                        w.getBlockAt(vx, vy - d, vz).setType(Material.VINE, false);
                }
            }
        }
    }

    private void genMushroom(World w, Random r, int x, int y, int z, boolean red) {
        int h = 4 + r.nextInt(3);
        int capR = 3 + r.nextInt(2);
        if (!canPlace(w, x, y, z, capR + 1, h + 6)) return;

        setColumn(w, x, y, z, Material.MUSHROOM_STEM, h);

        int top = y + h;
        Material cap = red ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK;

        for (int dx = -capR; dx <= capR; dx++) {
            for (int dz = -capR; dz <= capR; dz++) {
                int ad = Math.abs(dx) + Math.abs(dz);
                if (ad > capR + 1) continue;
                setIfAirOrLeaves(w, x + dx, top, z + dz, cap);
                if (ad <= capR - 1) setIfAirOrLeaves(w, x + dx, top + 1, z + dz, cap);
            }
        }
    }

    // ===== helpers =====

    private boolean canPlace(World w, int x, int y, int z, int radius, int height) {
        for (int dy = 0; dy <= height; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Material m = w.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (m != Material.AIR && !m.name().endsWith("_LEAVES") && m != Material.VINE) return false;
                }
            }
        }
        return true;
    }

    private void setColumn(World w, int x, int y, int z, Material mat, int h) {
        for (int i = 0; i < h; i++) w.getBlockAt(x, y + i, z).setType(mat, false);
    }

    private void blobLeaves(World w, int x, int y, int z, int r, Material leaves) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    double d = Math.sqrt(dx*dx + dz*dz) + Math.abs(dy)*0.6;
                    if (d <= r + 0.15) setIfAirOrLeaves(w, x + dx, y + dy, z + dz, leaves);
                }
            }
        }
    }

    private void discLeaves(World w, int x, int y, int z, int r, Material leaves) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx*dx + dz*dz <= r*r) setIfAirOrLeaves(w, x + dx, y, z + dz, leaves);
            }
        }
    }

    private void lineLeaves(World w, int x, int y, int z, int dx, int dz, Material leaves) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int sx = Integer.compare(dx, 0);
        int sz = Integer.compare(dz, 0);
        int cx = x, cz = z;
        for (int i = 0; i < steps; i++) {
            cx += sx;
            cz += sz;
            setIfAirOrLeaves(w, cx, y, cz, leaves);
            if (i > 1) setIfAirOrLeaves(w, cx, y - 1, cz, leaves);
        }
    }

    private void setIfAirOrLeaves(World w, int x, int y, int z, Material m) {
        Material cur = w.getBlockAt(x, y, z).getType();
        if (cur == Material.AIR || cur.name().endsWith("_LEAVES") || cur == Material.VINE)
            w.getBlockAt(x, y, z).setType(m, false);
    }

    private static long mix(long seed, int x, int z, int salt) {
        long h = seed ^ (long)salt;
        h ^= (long)x * 0x9E3779B97F4A7C15L;
        h ^= (long)z * 0xC2B2AE3D27D4EB4FL;
        h *= 0x165667B19E3779F9L;
        h ^= (h >>> 32);
        return h;
    }
}
