package org.popcraft.bolt.listeners;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.popcraft.bolt.BoltPlugin;
import org.popcraft.bolt.util.BoltPlayer;
import org.popcraft.bolt.util.Mode;
import org.popcraft.bolt.util.SchedulerUtil;

import java.util.Set;
import java.util.UUID;

public final class PlayerListener implements Listener {
    private final BoltPlugin plugin;

    public PlayerListener(final BoltPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent e) {
        final Player player = e.getPlayer();
        final UUID uuid = player.getUniqueId();
        final Set<Mode> defaultModes = plugin.defaultModes();
        plugin.getProfileCache().add(uuid, player.getName());
        SchedulerUtil.async(plugin, () -> {
            final FileConfiguration playerConfig = YamlConfiguration.loadConfiguration(plugin.getDataPath().resolve("players/%s.yml".formatted(uuid)).toFile());
            final boolean hasPersist = playerConfig.getBoolean(Mode.PERSIST.name().toLowerCase(), defaultModes.contains(Mode.PERSIST));
            final boolean hasNoLock = playerConfig.getBoolean(Mode.NOLOCK.name().toLowerCase(), defaultModes.contains(Mode.NOLOCK));
            final boolean hasNoSpam = playerConfig.getBoolean(Mode.NOSPAM.name().toLowerCase(), defaultModes.contains(Mode.NOSPAM));
            SchedulerUtil.schedule(plugin, player, () -> {
                final BoltPlayer boltPlayer = plugin.getBolt().getBoltPlayer(uuid);
                if (!boltPlayer.hasMode(Mode.PERSIST) && hasPersist) {
                    boltPlayer.toggleMode(Mode.PERSIST);
                }
                if (!boltPlayer.hasMode(Mode.NOLOCK) && hasNoLock) {
                    boltPlayer.toggleMode(Mode.NOLOCK);
                }
                if (!boltPlayer.hasMode(Mode.NOSPAM) && hasNoSpam) {
                    boltPlayer.toggleMode(Mode.NOSPAM);
                }
            });
        });

    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent e) {
        plugin.getBolt().removeBoltPlayer(e.getPlayer().getUniqueId());
    }
}
