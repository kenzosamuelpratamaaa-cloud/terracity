package me.mcp.terracity;

public final class Noise2D {
    private Noise2D(){}

    public static double noise(long seed, double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);

        double xf = x - x0;
        double zf = z - z0;

        double u = fade(xf);
        double v = fade(zf);

        double n00 = grad(hash(seed, x0, z0), xf, zf);
        double n10 = grad(hash(seed, x0 + 1, z0), xf - 1, zf);
        double n01 = grad(hash(seed, x0, z0 + 1), xf, zf - 1);
        double n11 = grad(hash(seed, x0 + 1, z0 + 1), xf - 1, zf - 1);

        double nx0 = lerp(n00, n10, u);
        double nx1 = lerp(n01, n11, u);
        return lerp(nx0, nx1, v);
    }

    public static double fbm(long seed, double x, double z, int octaves, double lacunarity, double gain) {
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += noise(seed + i * 1013L, x * freq, z * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return (norm == 0.0) ? 0.0 : (sum / norm);
    }

    private static int fastFloor(double d) {
        int i = (int)d;
        return d < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static long hash(long seed, int x, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h *= 0x165667B19E3779F9L;
        h ^= (h >>> 32);
        return h;
    }

    private static double grad(long h, double x, double z) {
        int b = (int)(h & 7);
        return switch (b) {
            case 0 ->  x + z;
            case 1 ->  x - z;
            case 2 -> -x + z;
            case 3 -> -x - z;
            case 4 ->  x;
            case 5 -> -x;
            case 6 ->  z;
            default -> -z;
        };
    }
}
