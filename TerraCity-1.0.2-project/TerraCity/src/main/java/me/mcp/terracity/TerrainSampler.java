package me.mcp.terracity;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Random;

public class TerrainSampler {

    private final TerraCityPlugin plugin;

    private int seaLevel;
    private int baseHeight;
    private int amp;
    private double scale;
    private double ridgeWeight;
    private double cliffThreshold;
    private int snowLine;
    private int seedSalt;

    private double oceanScale;
    private double oceanThreshold;
    private int oceanDepth;

    private double riverScale;
    private double riverWarpScale;
    private int riverDepth;
    private double riverWidth;

    private int volcanoRegionSize;
    private double volcanoChance;
    private int volcanoRadius;
    private int volcanoHeight;
    private int volcanoCraterRadius;
    private int volcanoLavaLevel;

    public TerrainSampler(TerraCityPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        seedSalt = c.getInt("seed-salt", 1337);

        seaLevel = c.getInt("terrain.sea-level", 63);
        baseHeight = c.getInt("terrain.base-height", 70);
        amp = c.getInt("terrain.height-amplitude", 72);
        scale = c.getDouble("terrain.terrain-scale", 0.0046);
        ridgeWeight = clamp01(c.getDouble("terrain.ridge-weight", 0.55));
        cliffThreshold = clamp01(c.getDouble("terrain.cliff-threshold", 0.78));
        snowLine = c.getInt("terrain.snow-line", 150);

        // Oceans / seas
        oceanScale = c.getDouble("ocean.scale", 0.00085);
        oceanThreshold = clamp01(c.getDouble("ocean.threshold", 0.52)); // lower => more land, higher => more ocean
        oceanDepth = Math.max(8, c.getInt("ocean.depth", 28));

        // Rivers (smooth & connected via domain-warped ridged noise)
        riverScale = c.getDouble("rivers.scale", 0.0021);
        riverWarpScale = c.getDouble("rivers.warp-scale", 0.0009);
        riverDepth = Math.max(2, c.getInt("rivers.depth", 10));
        riverWidth = clamp01(c.getDouble("rivers.width", 0.10)); // ~0.07 thin, ~0.14 thick

        // Volcanoes
        volcanoRegionSize = Math.max(512, c.getInt("volcano.region-size", 1536));
        volcanoChance = clamp01(c.getDouble("volcano.chance", 0.08));
        volcanoRadius = Math.max(96, c.getInt("volcano.radius", 220));
        volcanoHeight = Math.max(40, c.getInt("volcano.height", 110));
        volcanoCraterRadius = Math.max(10, c.getInt("volcano.crater-radius", 28));
        volcanoLavaLevel = c.getInt("volcano.lava-level", seaLevel + 18);
    }

    public int seaLevel() { return seaLevel; }
    public double cliffThreshold() { return cliffThreshold; }
    public int snowLine() { return snowLine; }

    public int height(long seed, int x, int z) {
        long s = seed + seedSalt;

        // Base terrain (mountains + ridges)
        double n = Noise2D.fbm(s, x * scale, z * scale, 4, 2.0, 0.5);

        double ridged = 1.0 - Math.abs(Noise2D.fbm(s + 7777L, x * (scale * 0.75), z * (scale * 0.75), 3, 2.0, 0.5));
        ridged = ridged * ridged;

        double mix = (1.0 - ridgeWeight) * n + ridgeWeight * (ridged * 2.0 - 1.0);

        // Large-scale continentalness controls oceans/landmasses
        double cont = continent01(s, x, z);
        mix = mix * 0.80 + (cont * 2.0 - 1.0) * 0.20;

        int h = baseHeight + (int)Math.round(mix * amp);

        // Oceans: push height down when continentalness is low (big connected seas)
        if (cont < oceanThreshold) {
            double t = (oceanThreshold - cont) / Math.max(1e-6, oceanThreshold); // 0..1
            t = t * t; // smoother shoreline
            h -= (int)Math.round(t * oceanDepth);
        }

        // Rivers: carve valleys along warped ridged noise lines (connected & smooth)
        double river = riverMask01(s, x, z);
        if (river > 0.0) {
            // carve more strongly above sea-level, less underwater
            int depth = (int)Math.round(river * riverDepth);
            if (h > seaLevel - 2) h -= depth;
        }

        // Volcanoes: rare cones + crater
        Volcano v = volcanoAt(seed, x, z);
        if (v != null) {
            double t = v.factorAt(x, z);
            if (t > 0.0) {
                // cone
                h += (int)Math.round((t * t) * volcanoHeight);

                // crater bowl
                double c = v.craterFactorAt(x, z);
                if (c > 0.0) {
                    h -= (int)Math.round(c * (volcanoCraterRadius * 1.35));
                }
            }
        }

        // Mild flattening deep below sea so oceans aren't too noisy
        if (h < seaLevel - 12) h = (seaLevel - 12) + (h - (seaLevel - 12)) / 2;

        return h;
    }

    public double temperature(long seed, int x, int z, double tScale) {
        return Noise2D.fbm(seed + seedSalt * 101L, x * tScale, z * tScale, 3, 2.0, 0.5);
    }

    public double humidity(long seed, int x, int z, double hScale) {
        return Noise2D.fbm(seed + seedSalt * 131L, x * hScale, z * hScale, 3, 2.0, 0.5);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // 0..1
    private double continent01(long s, int x, int z) {
        double c = Noise2D.fbm(s + 99991L, x * oceanScale, z * oceanScale, 3, 2.0, 0.5);
        return clamp01((c + 1.0) * 0.5);
    }

    /**
     * Returns 0..1 where 1 is the river center line.
     * Domain warp makes lines flow & connect instead of looking like static noise.
     */
    public double riverMask(long seed, int x, int z) {
        return riverMask01(seed + seedSalt, x, z);
    }

    public double riverMask01(long s, int x, int z) {
        double wx = Noise2D.fbm(s + 31001L, x * riverWarpScale, z * riverWarpScale, 2, 2.0, 0.5);
        double wz = Noise2D.fbm(s + 31002L, (x + 1000) * riverWarpScale, (z - 1000) * riverWarpScale, 2, 2.0, 0.5);

        double xw = x + wx * 220.0;
        double zw = z + wz * 220.0;

        double r = Noise2D.fbm(s + 424242L, xw * riverScale, zw * riverScale, 3, 2.0, 0.5);
        r = Math.abs(r);                  // 0..1-ish
        double line = 1.0 - clamp01(r);   // high near 0-crossings

        // Width shaping: only keep the center band
        double w = riverWidth;
        double t = (line - (1.0 - w)) / Math.max(1e-6, w);
        t = clamp01(t);

        // smoothstep
        return t * t * (3.0 - 2.0 * t);
    }

    public int volcanoLavaLevel() { return volcanoLavaLevel; }

    public record Volcano(int cx, int cz, int radius, int craterRadius) {
        double factorAt(int x, int z) {
            double dx = x - cx;
            double dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double t = 1.0 - (d / Math.max(1.0, (double) radius));
            return clamp01(t);
        }

        double craterFactorAt(int x, int z) {
            double dx = x - cx;
            double dz = z - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            double t = 1.0 - (d / Math.max(1.0, (double) craterRadius));
            t = clamp01(t);
            return t * t;
        }
    }

    public Volcano volcanoAt(long seed, int x, int z) {
        long s = seed + seedSalt;

        int rx = floorDiv(x, volcanoRegionSize);
        int rz = floorDiv(z, volcanoRegionSize);

        long h = mix(seed, rx, rz, seedSalt ^ 0xBADC0FF);
        Random r = new Random(h);

        if (r.nextDouble() > volcanoChance) return null;

        int ox = rx * volcanoRegionSize;
        int oz = rz * volcanoRegionSize;

        int cx = ox + volcanoRegionSize / 2 + r.nextInt(Math.max(1, volcanoRegionSize / 3)) - (volcanoRegionSize / 6);
        int cz = oz + volcanoRegionSize / 2 + r.nextInt(Math.max(1, volcanoRegionSize / 3)) - (volcanoRegionSize / 6);

        return new Volcano(cx, cz, volcanoRadius, volcanoCraterRadius);
    }

    private static int floorDiv(int a, int b) {
        int r = a / b;
        if ((a ^ b) < 0 && (r * b != a)) r--;
        return r;
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
