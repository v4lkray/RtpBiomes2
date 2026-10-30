package dev.rtpbiomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexOctaveGenerator;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom terrain: gentle hills, one surface type per biome, no water, no caves,
 * no ores, no structures, no flowers, no cacti, no mobs. Only a few vanilla trees.
 */
public final class TerrainGenerator extends ChunkGenerator {

    private static final Map<Long, SimplexOctaveGenerator> NOISE = new ConcurrentHashMap<>();
    private static final Material[] BANDS = {
            Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.RED_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA
    };

    private final BiomeType type;
    private final int baseHeight;
    private final double amplitude;
    private final double treeChance;
    private final List<TreeType> trees;

    public TerrainGenerator(BiomeType type, int baseHeight, double amplitude, double treeChance, List<TreeType> trees) {
        this.type = type;
        this.baseHeight = baseHeight;
        this.amplitude = amplitude;
        this.treeChance = treeChance;
        this.trees = trees;
    }

    private static SimplexOctaveGenerator noise(long seed) {
        return NOISE.computeIfAbsent(seed, s -> {
            SimplexOctaveGenerator g = new SimplexOctaveGenerator(new Random(s), 3);
            g.setScale(1.0 / 110.0);
            return g;
        });
    }

    public int heightAt(long seed, int x, int z) {
        double n = noise(seed).noise(x, z, 2.0, 0.5, true); // -1..1
        return baseHeight + (int) Math.round(n * amplitude);
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
        long seed = info.getSeed();
        int minY = info.getMinHeight();
        int low = baseHeight - (int) Math.ceil(amplitude) - 4;
        data.setRegion(0, minY + 1, 0, 16, low, 16, Material.STONE);
        BlockData snowyGrass = Bukkit.createBlockData("minecraft:grass_block[snowy=true]");

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int h = heightAt(seed, chunkX * 16 + x, chunkZ * 16 + z);
                data.setBlock(x, minY, z, Material.BEDROCK);
                if (h - 3 > low) data.setRegion(x, low, z, x + 1, h - 3, z + 1, Material.STONE);

                switch (type) {
                    case DESERT -> {
                        data.setRegion(x, h - 8, z, x + 1, h - 3, z + 1, Material.SANDSTONE);
                        data.setRegion(x, h - 3, z, x + 1, h + 1, z + 1, Material.SAND);
                    }
                    case BADLANDS -> {
                        for (int y = h - 12; y < h; y++) {
                            data.setBlock(x, y, z, BANDS[Math.floorMod(y, BANDS.length)]);
                        }
                        // contour lines of coloured terracotta, otherwise red sand
                        data.setBlock(x, h, z, (h % 5 == 0) ? BANDS[Math.floorMod(h, BANDS.length)] : Material.RED_SAND);
                    }
                    case MUSHROOM -> {
                        data.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
                        data.setBlock(x, h, z, Material.MYCELIUM);
                    }
                    case SNOW -> {
                        data.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
                        data.setBlock(x, h, z, snowyGrass);
                        data.setBlock(x, h + 1, z, Material.SNOW);
                    }
                    default -> { // plains, cherry
                        data.setRegion(x, h - 3, z, x + 1, h, z + 1, Material.DIRT);
                        data.setBlock(x, h, z, Material.GRASS_BLOCK);
                    }
                }
            }
        }
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, heightAt(world.getSeed(), 0, 0) + 1, 0.5);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        if (treeChance <= 0 || trees.isEmpty()) return List.of();
        return List.of(new TreePopulator(world));
    }

    private final class TreePopulator extends BlockPopulator {
        private final World world;

        TreePopulator(World world) { this.world = world; }

        @Override
        public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
            if (random.nextDouble() >= treeChance) return;
            int x = chunkX * 16 + random.nextInt(16);
            int z = chunkZ * 16 + random.nextInt(16);
            int h = heightAt(info.getSeed(), x, z);
            TreeType tt = trees.get(random.nextInt(trees.size()));
            try {
                if (type == BiomeType.SNOW) region.setType(x, h + 1, z, Material.AIR); // remove snow layer under the trunk
                region.generateTree(new Location(world, x, h + 1, z), random, tt);
            } catch (Exception ignored) {
                // tree would have left the generation area; skip it
            }
        }
    }

    public static final class SingleBiomeProvider extends BiomeProvider {
        private final Biome biome;

        public SingleBiomeProvider(Biome biome) { this.biome = biome; }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) { return biome; }

        @Override
        public List<Biome> getBiomes(WorldInfo info) { return List.of(biome); }
    }
}
