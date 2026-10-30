package dev.rtpbiomes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RTPPlugin extends JavaPlugin {

    private WorldManager worlds;
    private RtpListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        worlds = new WorldManager(this);
        listener = new RtpListener(this, worlds);
        Bukkit.getPluginManager().registerEvents(listener, this);
        worlds.start();
        getLogger().info("RTPBiomes enabled.");
    }

    private void msg(CommandSender s, String text, NamedTextColor c) {
        s.sendMessage(Component.text(text, c));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("rtp")) {
            if (!(sender instanceof Player p)) { msg(sender, "Players only.", NamedTextColor.RED); return true; }
            if (args.length == 0) { listener.openMenu(p); return true; }
            if (args[0].equalsIgnoreCase("leave")) {
                p.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
                return true;
            }
            BiomeType t = BiomeType.byId(args[0]);
            if (t == null) { msg(p, "Unknown biome. Use: plains, desert, badlands, snow, mushroom, cherry", NamedTextColor.RED); return true; }
            worlds.teleport(p, t);
            return true;
        }

        if (name.equals("rtpbot")) {
            if (!(sender instanceof Player p)) { msg(sender, "Players only.", NamedTextColor.RED); return true; }
            if (args.length >= 2 && args[0].equalsIgnoreCase("spawn")) {
                BiomeType t = BiomeType.byId(args[1]);
                if (t == null) { msg(p, "Unknown biome. Use: plains, desert, badlands, snow, mushroom, cherry", NamedTextColor.RED); return true; }
                listener.spawnBot(p, t);
                msg(p, "Spawned the " + t.display + " bot.", NamedTextColor.GREEN);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("remove")) {
                int n = listener.removeNearestBot(p);
                msg(p, n > 0 ? "Bot removed." : "No bot within 4 blocks.", n > 0 ? NamedTextColor.GREEN : NamedTextColor.RED);
                return true;
            }
            msg(p, "Usage: /rtpbot spawn <plains|desert|badlands|snow|mushroom|cherry>  |  /rtpbot remove", NamedTextColor.YELLOW);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> biomes = new ArrayList<>();
        for (BiomeType t : BiomeType.values()) biomes.add(t.id);
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (n.equals("rtp") && args.length == 1) { out.addAll(biomes); out.add("leave"); }
        else if (n.equals("rtpbot")) {
            if (args.length == 1) { out.add("spawn"); out.add("remove"); }
            else if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) out.addAll(biomes);
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.startsWith(last));
        return out;
    }
}
