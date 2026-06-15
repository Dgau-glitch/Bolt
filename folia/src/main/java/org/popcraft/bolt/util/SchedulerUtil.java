package org.popcraft.bolt.util;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SchedulerUtil {
    private static final FoliaSchedulerService SCHEDULER = new DefaultFoliaSchedulerService();

    private SchedulerUtil() {
    }

    public static FoliaSchedulerService service() {
        return SCHEDULER;
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final CommandSender sender, final Runnable runnable) {
        return schedule(plugin, sender, runnable, 0);
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final CommandSender sender, final Runnable runnable, final long delay) {
        return SCHEDULER.sender(plugin, sender, runnable, delay);
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final Location location, final Runnable runnable) {
        return SCHEDULER.region(plugin, location, runnable);
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final Location location, final Runnable runnable, final long delay) {
        return SCHEDULER.regionDelayed(plugin, location, runnable, delay);
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final Entity entity, final Runnable runnable) {
        return SCHEDULER.entity(plugin, entity, runnable);
    }

    public static ScheduledTaskHandle schedule(final Plugin plugin, final Runnable runnable, final long delay, final long interval) {
        return SCHEDULER.globalRepeating(plugin, runnable, delay, interval);
    }

    public static ScheduledTaskHandle async(final Plugin plugin, final Runnable runnable) {
        return SCHEDULER.async(plugin, runnable);
    }

    public static Executor executor(final Plugin plugin, final CommandSender sender) {
        return SCHEDULER.senderExecutor(plugin, sender);
    }

    public static <T> CompletableFuture<T> thenAcceptSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final Consumer<T> consumer) {
        return SCHEDULER.thenAcceptSender(future, plugin, sender, consumer);
    }

    public static <T> CompletableFuture<T> whenCompleteSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final BiConsumer<T, Throwable> consumer) {
        return SCHEDULER.whenCompleteSender(future, plugin, sender, consumer);
    }

    public static <T> CompletableFuture<T> thenAcceptEntity(final CompletableFuture<T> future, final Plugin plugin, final Entity entity, final Consumer<T> consumer) {
        return SCHEDULER.thenAcceptEntity(future, plugin, entity, consumer);
    }

    public static <T> CompletableFuture<T> thenAcceptRegion(final CompletableFuture<T> future, final Plugin plugin, final Location location, final Consumer<T> consumer) {
        return SCHEDULER.thenAcceptRegion(future, plugin, location, consumer);
    }

    public static <T> CompletableFuture<T> exceptionallyToSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final Consumer<Throwable> consumer) {
        return SCHEDULER.exceptionallyToSender(future, plugin, sender, consumer);
    }
}
