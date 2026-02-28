package me.mcp.terracity;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class TerraCityPlugin extends JavaPlugin implements TabExecutor {

    private TerraCityGenerator generator;
    private TerraCityBiomeProvider biomeProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();

        Objects.requireNonNull(getCommand("terracity")).setExecutor(this);
        Objects.requireNonNull(getCommand("terracity")).setTabCompleter(this);

        getLogger().info("TerraCity enabled (Paper/Spigot 1.21.1).");
    }

    private void reloadLocal() {
        this.generator = new TerraCityGenerator(this);
        this.biomeProvider = new TerraCityBiomeProvider(
                this,
                generator.getPlanner(),
                generator.getTerrain()   // ← INI YANG KURANG
        );
    }


    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return generator;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return biomeProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("terracity.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§aTerraCity commands:");
            sender.sendMessage("§7/terracity create <worldName>");
            sender.sendMessage("§7/terracity reload");
            sender.sendMessage("§7/terracity info <worldName>");
            sender.sendMessage("§7Tip: Multiverse -> /mv create <world> normal -g TerraCity");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                reloadConfig();
                reloadLocal();
                sender.sendMessage("§aTerraCity config reloaded.");
                return true;
            }
            case "create" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /terracity create <worldName>");
                    return true;
                }
                String worldName = args[1];

                World already = Bukkit.getWorld(worldName);
                if (already != null) {
                    sender.sendMessage("§eWorld already loaded: " + already.getName());
                    return true;
                }

                WorldCreator wc = new WorldCreator(worldName);
                wc.generator(generator);
                wc.biomeProvider(biomeProvider);
                World w = wc.createWorld();

                if (w == null) {
                    sender.sendMessage("§cFailed to create world. Check console.");
                    return true;
                }

                sender.sendMessage("§aCreated TerraCity world: §f" + w.getName());
                sender.sendMessage("§7If you use Multiverse: §f/mv tp " + w.getName());
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /terracity info <worldName>");
                    return true;
                }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) {
                    sender.sendMessage("§cWorld not loaded.");
                    return true;
                }
                sender.sendMessage("§aTerraCity info for §f" + w.getName());
                sender.sendMessage("§7Seed: §f" + w.getSeed());
                sender.sendMessage("§7City enabled: §f" + getConfig().getBoolean("city.enabled", true));
                sender.sendMessage("§7Region size: §f" + getConfig().getInt("city.region-size", 512));
                sender.sendMessage("§7City chance: §f" + getConfig().getDouble("city.chance", 0.35));
                sender.sendMessage("§7City radius: §f" + getConfig().getInt("city.radius", 120));
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown subcommand. Use /terracity");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(args[0], List.of("create", "reload", "info"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) worlds.add(w.getName());
            return prefix(args[1], worlds);
        }
        return Collections.emptyList();
    }

    private static List<String> prefix(String token, List<String> options) {
        String t = token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        return out;
    }
}
