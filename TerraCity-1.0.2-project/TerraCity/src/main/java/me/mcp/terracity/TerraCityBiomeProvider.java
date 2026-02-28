package me.mcp.terracity;

import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

public class TerraCityBiomeProvider extends BiomeProvider {

    private final TerraCityPlugin plugin;
    private final CityPlanner planner;
    private final TerrainSampler terrain;

    private double tScale;
    private double hScale;

    public TerraCityBiomeProvider(
            TerraCityPlugin plugin,
            CityPlanner planner,
            TerrainSampler terrain
    ) {
        this.plugin = plugin;
        this.planner = planner;
        this.terrain = terrain;
        reload();
    }


    public void reload() {
        terrain.reload();
        FileConfiguration c = plugin.getConfig();
        tScale = c.getDouble("biomes.temperature-scale", 0.0023);
        hScale = c.getDouble("biomes.humidity-scale", 0.0020);
    }

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
        long seed = worldInfo.getSeed();
        int sea = plugin.getConfig().getInt("terrain.sea-level", 63);

        if (planner.isEnabled()) {
            CityPlanner.City city = planner.cityAt(seed, x, z, terrain, sea);
            if (city != null && planner.cityBlendFactor(city, x, z) > 0.0) {
                return (planner.cityBlendFactor(city, x, z) > 0.85) ? Biome.PLAINS : Biome.FOREST;
            }
        }

        int h = terrain.height(seed, x, z);
        double temp = terrain.temperature(seed, x, z, tScale);
        double hum = terrain.humidity(seed, x, z, hScale);

        boolean hot = temp > 0.35;
        boolean cold = temp < -0.35;
        boolean wet = hum > 0.35;

        if (h >= 185) {
            if (cold) return Biome.JAGGED_PEAKS;
            return Biome.STONY_PEAKS;
        }
        if (h >= terrain.snowLine()) return Biome.SNOWY_SLOPES;

        if (h <= sea - 8) return Biome.DEEP_OCEAN;
        if (h <= sea + 2) return Biome.BEACH;

        if (hot && !wet) {
            if (hum < -0.15) return Biome.BADLANDS;
            return Biome.DESERT;
        }
        if (cold) return wet ? Biome.SNOWY_TAIGA : Biome.SNOWY_PLAINS;

        if (wet && hot) {
            if (hum > 0.65) return Biome.JUNGLE;
            return Biome.SWAMP;
        }
        if (wet) return Biome.FOREST;
        if (hum < -0.20) return Biome.WINDSWEPT_HILLS;

        return Biome.PLAINS;
    }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) {
        return List.of(
                Biome.PLAINS, Biome.FOREST, Biome.TAIGA, Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA,
                Biome.DESERT, Biome.BADLANDS, Biome.JUNGLE, Biome.SWAMP,
                Biome.STONY_PEAKS, Biome.JAGGED_PEAKS, Biome.SNOWY_SLOPES,
                Biome.DEEP_OCEAN, Biome.BEACH, Biome.WINDSWEPT_HILLS
        );
    }
}
