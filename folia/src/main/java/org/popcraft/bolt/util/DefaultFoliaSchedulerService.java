package org.popcraft.bolt.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public final class DefaultFoliaSchedulerService implements FoliaSchedulerService {
    @Override
    public ScheduledTaskHandle global(final Plugin plugin, final Runnable runnable) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run()));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    @Override
    public ScheduledTaskHandle globalDelayed(final Plugin plugin, final Runnable runnable, final long delay) {
        if (delay <= 0) {
            return global(plugin, runnable);
        }
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), delay));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, runnable, delay));
    }

    @Override
    public ScheduledTaskHandle globalRepeating(final Plugin plugin, final Runnable runnable, final long delay, final long interval) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), delay, interval));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, interval));
    }

    @Override
    public ScheduledTaskHandle region(final Plugin plugin, final Location location, final Runnable runnable) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getRegionScheduler().run(plugin, location, task -> runnable.run()));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    @Override
    public ScheduledTaskHandle regionDelayed(final Plugin plugin, final Location location, final Runnable runnable, final long delay) {
        if (delay <= 0) {
            return region(plugin, location, runnable);
        }
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), delay));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, runnable, delay));
    }

    @Override
    public ScheduledTaskHandle regionRepeating(final Plugin plugin, final Location location, final Runnable runnable, final long delay, final long interval) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, task -> runnable.run(), delay, interval));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, interval));
    }

    @Override
    public ScheduledTaskHandle entity(final Plugin plugin, final Entity entity, final Runnable runnable) {
        if (FoliaUtil.isFolia()) {
            return wrap(entity.getScheduler().run(plugin, task -> runnable.run(), null));
        }
        return wrap(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    @Override
    public ScheduledTaskHandle entityDelayed(final Plugin plugin, final Entity entity, final Runnable runnable, final long delay) {
        if (delay <= 0) {
            return entity(plugin, entity, runnable);
        }
        if (FoliaUtil.isFolia()) {
            return wrap(entity.getScheduler().runDelayed(plugin, task -> runnable.run(), null, delay));
        }
        return wrap(Bukkit.getScheduler().runTaskLater(plugin, runnable, delay));
    }

    @Override
    public ScheduledTaskHandle entityRepeating(final Plugin plugin, final Entity entity, final Runnable runnable, final long delay, final long interval) {
        if (FoliaUtil.isFolia()) {
            return wrap(entity.getScheduler().runAtFixedRate(plugin, task -> runnable.run(), null, delay, interval));
        }
        return wrap(Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, interval));
    }

    @Override
    public ScheduledTaskHandle async(final Plugin plugin, final Runnable runnable) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run()));
        }
        return wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public ScheduledTaskHandle asyncDelayed(final Plugin plugin, final Runnable runnable, final long delay) {
        if (delay <= 0) {
            return async(plugin, runnable);
        }
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getAsyncScheduler().runDelayed(plugin, task -> runnable.run(), delay * 50, TimeUnit.MILLISECONDS));
        }
        return wrap(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delay));
    }

    @Override
    public ScheduledTaskHandle asyncRepeating(final Plugin plugin, final Runnable runnable, final long delay, final long interval) {
        if (FoliaUtil.isFolia()) {
            return wrap(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(), delay * 50, interval * 50, TimeUnit.MILLISECONDS));
        }
        return wrap(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delay, interval));
    }

    @Override
    public ScheduledTaskHandle sender(final Plugin plugin, final CommandSender sender, final Runnable runnable, final long delay) {
        if (sender instanceof final Player player) {
            return entityDelayed(plugin, player, runnable, delay);
        }
        return FoliaSchedulerService.super.sender(plugin, sender, runnable, delay);
    }

    private ScheduledTaskHandle wrap(final ScheduledTask task) {
        return task == null ? ScheduledTaskHandle.NOOP : task::cancel;
    }

    private ScheduledTaskHandle wrap(final BukkitTask task) {
        return task == null ? ScheduledTaskHandle.NOOP : task::cancel;
    }
}
