package simulation;

import java.util.Random;

/**
 * 1D Perlin noise generator for demand curves.
 * Each resource gets its own instance with a unique seed (hash of resource name),
 * so their demand cycles are independent of each other.
 */
public class PerlinNoise {

    private final int[] permutation = new int[512];

    public PerlinNoise(long seed) {
        Random rng = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        // Fisher-Yates shuffle
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        // Duplicate for overflow handling
        for (int i = 0; i < 512; i++) permutation[i] = p[i & 255];
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x) {
        return (hash & 1) == 0 ? x : -x;
    }

    /** Single-octave Perlin noise in range approximately [-1, 1]. */
    public double noise(double x) {
        int X = (int) Math.floor(x) & 255;
        x -= Math.floor(x);
        double u = fade(x);
        return lerp(u, grad(permutation[X], x), grad(permutation[X + 1], x - 1));
    }

    /**
     * Multi-octave Perlin noise — combines multiple frequencies for organic variation.
     *
     * @param x           Input value (time position in noise field)
     * @param octaves     Number of frequency layers
     * @param persistence Amplitude multiplier per octave (0.5 = each octave is half as loud)
     * @param lacunarity  Frequency multiplier per octave (2.0 = doubles each time)
     * @return Normalized value in [-1, 1]
     */
    public double octaveNoise(double x, int octaves, double persistence, double lacunarity) {
        double total = 0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue  = 0;

        for (int i = 0; i < octaves; i++) {
            total    += noise(x * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue; // Normalize to [-1, 1]
    }
}
