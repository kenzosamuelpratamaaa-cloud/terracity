package me.mcp.terracity;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

public class CityPlanner {

    public record City(int centerX, int centerZ, int radius, int blend, int baseHeight) {}

    private final TerraCityPlugin plugin;

    private boolean enabled;
    private int regionSize;
    private double chance;
    private int radius;
    private int blend;
    private int baseHeightMin;

    public CityPlanner(TerraCityPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("city.enabled", true);
        regionSize = Math.max(128, c.getInt("city.region-size", 512));
        chance = clamp01(c.getDouble("city.chance", 0.35));
        radius = Math.max(32, c.getInt("city.radius", 120));
        blend = Math.max(0, c.getInt("city.blend", 40));
        baseHeightMin = Math.max(60, c.getInt("city.base-height-min", 80));
    }

    public boolean isEnabled() { return enabled; }

    public City cityAt(long seed, int worldX, int worldZ, TerrainSampler sampler, int seaLevel) {
        if (!enabled) return null;

        int rx = floorDiv(worldX, regionSize);
        int rz = floorDiv(worldZ, regionSize);

        long h = mix(seed, rx, rz, plugin.getConfig().getInt("seed-salt", 1337));
        Random r = new Random(h);

        if (r.nextDouble() > chance) return null;

        int regionOriginX = rx * regionSize;
        int regionOriginZ = rz * regionSize;

        int cx = regionOriginX + regionSize / 2 + r.nextInt(Math.max(1, regionSize / 3)) - (regionSize / 6);
        int cz = regionOriginZ + regionSize / 2 + r.nextInt(Math.max(1, regionSize / 3)) - (regionSize / 6);

        int natural = sampler.height(seed, cx, cz);
        int base = Math.max(baseHeightMin, Math.max(seaLevel + 10, natural));
        base = Math.min(base, baseHeightMin + 60);

        return new City(cx, cz, radius, blend, base);
    }

    public double cityBlendFactor(City city, int x, int z) {
        if (city == null) return 0.0;
        int dx = x - city.centerX();
        int dz = z - city.centerZ();
        double dist = Math.sqrt((double)dx*dx + (double)dz*dz);

        int r = city.radius();
        int b = city.blend();

        if (dist <= r) return 1.0;
        if (b <= 0) return 0.0;
        if (dist >= r + b) return 0.0;

        double t = 1.0 - ((dist - r) / (double)b);
        return clamp01(t);
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static long mix(long seed, int x, int z, int salt) {
        long h = seed ^ (salt * 0x9E3779B97F4A7C15L);
        h ^= (long)x * 0xC2B2AE3D27D4EB4FL;
        h ^= (long)z * 0x165667B19E3779F9L;
        h *= 0xD6E8FEB86659FD93L;
        h ^= (h >>> 33);
        h *= 0xD6E8FEB86659FD93L;
        h ^= (h >>> 33);
        return h;
    }
}
