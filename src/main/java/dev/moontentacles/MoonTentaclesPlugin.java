package dev.moontentacles;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MoonTentaclesPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, TentacleRig> rigs = new HashMap<>();
    private Settings settings;
    private NamespacedKey enabledKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());
        enabledKey = new NamespacedKey(this, "enabled");

        removeStrayDisplays();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        removeAllRigs();
        removeStrayDisplays();
    }

    private void removeAllRigs() {
        for (TentacleRig rig : rigs.values()) {
            rig.remove();
        }
        rigs.clear();
    }

    /** Clears any leftover tentacle segments, for example after a crash. */
    private void removeStrayDisplays() {
        for (World w : Bukkit.getWorlds()) {
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class)) {
                if (d.getScoreboardTags().contains("moontentacles")) {
                    d.remove();
                }
            }
        }
    }

    private boolean isEnabledFor(Player p) {
        Byte stored = p.getPersistentDataContainer().get(enabledKey, PersistentDataType.BYTE);
        if (stored == null) {
            return getConfig().getBoolean("auto-enable-on-join", true);
        }
        return stored == 1;
    }

    private boolean shouldShow(Player p) {
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        if (!isEnabledFor(p)) {
            return false;
        }
        if (p.isInvisible() && getConfig().getBoolean("hide-when-invisible", true)) {
            return false;
        }
        Pose pose = p.getPose();
        return pose == Pose.STANDING || pose == Pose.SNEAKING;
    }

    private void drop(UUID id) {
        TentacleRig rig = rigs.remove(id);
        if (rig != null) {
            rig.remove();
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!shouldShow(p)) {
                drop(id);
                continue;
            }
            TentacleRig rig = rigs.get(id);
            if (rig == null || rig.world() != p.getWorld()) {
                drop(id);
                rig = new TentacleRig(this, settings, p);
                rigs.put(id, rig);
            }
            rig.update();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        drop(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        drop(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) {
            return;
        }
        boolean farAway = e.getFrom().getWorld() != e.getTo().getWorld()
                || e.getFrom().distanceSquared(e.getTo()) > 64.0;
        if (farAway) {
            drop(e.getPlayer().getUniqueId());
        }
    }

    private void setEnabled(Player p, boolean on) {
        p.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        if (!on) {
            drop(p.getUniqueId());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("moontentacles.admin")) {
                sender.sendMessage("You don't have permission to do that.");
                return true;
            }
            reloadConfig();
            settings = Settings.load(getConfig(), getLogger());
            removeAllRigs();
            sender.sendMessage("MoonTentacles config reloaded.");
            return true;
        }

        String mode = args.length >= 1 ? args[0].toLowerCase() : "toggle";
        if (!mode.equals("on") && !mode.equals("off") && !mode.equals("toggle")) {
            sender.sendMessage("Usage: /tentacles [on|off|toggle] [player] or /tentacles reload");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("moontentacles.admin")) {
                sender.sendMessage("You don't have permission to change other players.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("That player isn't online.");
                return true;
            }
        } else if (sender instanceof Player p) {
            if (!p.hasPermission("moontentacles.use")) {
                p.sendMessage("You don't have permission to do that.");
                return true;
            }
            target = p;
        } else {
            sender.sendMessage("Specify a player: /tentacles <on|off|toggle> <player>");
            return true;
        }

        boolean now = switch (mode) {
            case "on" -> true;
            case "off" -> false;
            default -> !isEnabledFor(target);
        };
        setEnabled(target, now);
        sender.sendMessage((target == sender ? "Your" : target.getName() + "'s")
                + " tentacles are now " + (now ? "on" : "off") + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return sender.hasPermission("moontentacles.admin")
                    ? List.of("on", "off", "toggle", "reload")
                    : List.of("on", "off", "toggle");
        }
        return List.of();
    }
}
