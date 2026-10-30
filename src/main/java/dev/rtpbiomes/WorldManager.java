package dev.rtpbiomes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class WorldManager {

    private static final class State {
        World world;
        boolean dirty;
        boolean resetting;
        long emptySince = -1;
    }

    private final RTPPlugin plugin;
    private final Map<BiomeType, State> states = new EnumMap<>(BiomeType.class);

    public WorldManager(RTPPlugin plugin) {
        this.plugin = plugin;
    }

    private String prefix() { return plugin.getConfig().getString("world-prefix", "rtp_"); }

    public String worldName(BiomeType t) { return prefix() + t.id; }

    public boolean isRtpWorld(World w) {
        if (w == null) return false;
        for (BiomeType t : BiomeType.values()) if (w.getName().equals(worldName(t))) return true;
        return false;
    }

    public int playerCount(BiomeType t) {
        State s = states.get(t);
        return (s == null || s.world == null) ? 0 : s.world.getPlayers().size();
    }

    public void start() {
        for (BiomeType t : BiomeType.values()) {
            State s = new State();
            // if the folder already exists (server restart), it may contain old player changes -> wipe when empty
            s.dirty = new File(Bukkit.getWorldContainer(), worldName(t)).exists();
            s.world = createWorld(t);
            states.put(t, s);
        }
        // light check every 5 seconds, does nothing most of the time
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    private TerrainGenerator makeGenerator(BiomeType t) {
        var cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("biomes." + t.id);
        double chance = sec == null ? 0.1 : sec.getDouble("tree-chance", 0.1);
        List<TreeType> trees = new ArrayList<>();
        if (sec != null) {
            for (String n : sec.getStringList("trees")) {
                try { trees.add(TreeType.valueOf(n.toUpperCase())); }
                catch (IllegalArgumentException e) { plugin.getLogger().warning("Unknown tree type: " + n); }
            }
        }
        return new TerrainGenerator(t, cfg.getInt("base-height", 72), cfg.getDouble("hill-amplitude", 12.0), chance, trees);
    }

    private World createWorld(BiomeType t) {
        long seed = plugin.getConfig().getLong("seed-base", 20240601L) + t.ordinal() * 1000L;
        WorldCreator wc = new WorldCreator(worldName(t));
        wc.environment(World.Environment.NORMAL)
          .generator(makeGenerator(t))
          .biomeProvider(new TerrainGenerator.SingleBiomeProvider(t.biome))
          .seed(seed)
          .generateStructures(false);
        World w = wc.createWorld();
        if (w == null) {
            plugin.getLogger().severe("Could not create world " + worldName(t));
            return null;
        }
        w.setSpawnFlags(false, false); // no monsters, no animals
        int range = plugin.getConfig().getInt("teleport-range", 4000);
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(range * 2.0 + 2000);
        return w;
    }

    // ------------------------------------------------------------ reset (no server restart, no lag)

    private void tick() {
        long delay = plugin.getConfig().getLong("reset-delay-seconds", 30) * 1000L;
        long now = System.currentTimeMillis();
        for (Map.Entry<BiomeType, State> e : states.entrySet()) {
            State s = e.getValue();
            if (s.world == null || s.resetting || !s.dirty) continue;
            if (!s.world.getPlayers().isEmpty()) { s.emptySince = -1; continue; }
            if (s.emptySince < 0) { s.emptySince = now; continue; }
            if (now - s.emptySince >= delay) reset(e.getKey(), s);
        }
    }

    private void reset(BiomeType t, State s) {
        s.resetting = true;
        World w = s.world;
        if (!Bukkit.unloadWorld(w, false)) { // false = do not save
            s.resetting = false;
            s.emptySince = -1;
            return;
        }
        s.world = null;
        final File dir = new File(Bukkit.getWorldContainer(), worldName(t));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            deleteRecursive(dir.toPath());
            Bukkit.getScheduler().runTask(plugin, () -> {
                s.world = createWorld(t);
                s.dirty = false;
                s.emptySince = -1;
                s.resetting = false;
                plugin.getLogger().info("Regenerated world " + worldName(t));
            });
        });
    }

    private void deleteRecursive(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(Comparator.reverseOrder()).forEach(x -> {
                try { Files.deleteIfExists(x); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("Could not delete " + p + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ teleport

    public void teleport(Player p, BiomeType t) {
        State s = states.get(t);
        if (s == null || s.world == null || s.resetting) {
            p.sendMessage(Component.text("That biome is refreshing, try again in a few seconds.", NamedTextColor.RED));
            return;
        }
        s.dirty = true;
        s.emptySince = -1;
        p.sendMessage(Component.text("Finding a spot in the ", NamedTextColor.GRAY)
                .append(Component.text(t.display, t.color)).append(Component.text(" biome...", NamedTextColor.GRAY)));
        attempt(p, t, s.world, 0);
    }

    private void attempt(Player p, BiomeType t, World w, int tries) {
        if (!p.isOnline()) return;
        if (tries >= 12) {
            p.sendMessage(Component.text("Couldn't find a safe spot, please try again.", NamedTextColor.RED));
            return;
        }
        int range = plugin.getConfig().getInt("teleport-range", 4000);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        final int x = r.nextInt(-range, range + 1);
        final int z = r.nextInt(-range, range + 1);

        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            State s = states.get(t);
            if (!p.isOnline() || s == null || s.world != w) return;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = w.getBlockAt(x, y, z);
            Material m = ground.getType();
            boolean bad = !ground.isSolid() || Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m)
                    || m == Material.MUSHROOM_STEM || m == Material.RED_MUSHROOM_BLOCK || m == Material.BROWN_MUSHROOM_BLOCK;
            Block feet = ground.getRelative(BlockFace.UP);
            Block head = feet.getRelative(BlockFace.UP);
            boolean feetOk = feet.isPassable() || feet.getType() == Material.SNOW;
            if (bad || !feetOk || !head.isPassable()) {
                attempt(p, t, w, tries + 1);
                return;
            }
            Location dest = new Location(w, x + 0.5, y + 1, z + 0.5, r.nextFloat() * 360f - 180f, 0f);
            p.teleportAsync(dest).thenAccept(ok -> {
                if (ok) p.sendMessage(Component.text("Teleported to ", NamedTextColor.GRAY)
                        .append(Component.text(t.display, t.color)).append(Component.text("!", NamedTextColor.GRAY)));
            });
        });
    }
}
