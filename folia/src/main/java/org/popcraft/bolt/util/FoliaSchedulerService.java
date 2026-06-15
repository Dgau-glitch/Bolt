package org.popcraft.bolt.util;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface FoliaSchedulerService {
    ScheduledTaskHandle global(Plugin plugin, Runnable runnable);

    ScheduledTaskHandle globalDelayed(Plugin plugin, Runnable runnable, long delay);

    ScheduledTaskHandle globalRepeating(Plugin plugin, Runnable runnable, long delay, long interval);

    ScheduledTaskHandle region(Plugin plugin, Location location, Runnable runnable);

    ScheduledTaskHandle regionDelayed(Plugin plugin, Location location, Runnable runnable, long delay);

    ScheduledTaskHandle regionRepeating(Plugin plugin, Location location, Runnable runnable, long delay, long interval);

    ScheduledTaskHandle entity(Plugin plugin, Entity entity, Runnable runnable);

    ScheduledTaskHandle entityDelayed(Plugin plugin, Entity entity, Runnable runnable, long delay);

    ScheduledTaskHandle entityRepeating(Plugin plugin, Entity entity, Runnable runnable, long delay, long interval);

    ScheduledTaskHandle async(Plugin plugin, Runnable runnable);

    ScheduledTaskHandle asyncDelayed(Plugin plugin, Runnable runnable, long delay);

    ScheduledTaskHandle asyncRepeating(Plugin plugin, Runnable runnable, long delay, long interval);

    default ScheduledTaskHandle sender(final Plugin plugin, final CommandSender sender, final Runnable runnable) {
        return sender(plugin, sender, runnable, 0);
    }

    default ScheduledTaskHandle sender(final Plugin plugin, final CommandSender sender, final Runnable runnable, final long delay) {
        if (sender instanceof final Player player) {
            return entityDelayed(plugin, player, runnable, delay);
        }
        if (delay <= 0) {
            return global(plugin, runnable);
        }
        return globalDelayed(plugin, runnable, delay);
    }

    default Executor senderExecutor(final Plugin plugin, final CommandSender sender) {
        return command -> sender(plugin, sender, command);
    }

    default <T> CompletableFuture<T> thenAcceptSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final Consumer<T> consumer) {
        return future.whenCompleteAsync((value, throwable) -> {
            if (throwable == null) {
                consumer.accept(value);
            }
        }, senderExecutor(plugin, sender));
    }

    default <T> CompletableFuture<T> whenCompleteSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final BiConsumer<T, Throwable> consumer) {
        return future.whenCompleteAsync(consumer, senderExecutor(plugin, sender));
    }

    default <T> CompletableFuture<T> thenAcceptEntity(final CompletableFuture<T> future, final Plugin plugin, final Entity entity, final Consumer<T> consumer) {
        return future.whenCompleteAsync((value, throwable) -> {
            if (throwable == null) {
                consumer.accept(value);
            }
        }, command -> entity(plugin, entity, command));
    }

    default <T> CompletableFuture<T> thenAcceptRegion(final CompletableFuture<T> future, final Plugin plugin, final Location location, final Consumer<T> consumer) {
        return future.whenCompleteAsync((value, throwable) -> {
            if (throwable == null) {
                consumer.accept(value);
            }
        }, command -> region(plugin, location, command));
    }

    default <T> CompletableFuture<T> exceptionallyToSender(final CompletableFuture<T> future, final Plugin plugin, final CommandSender sender, final Consumer<Throwable> consumer) {
        return future.whenCompleteAsync((value, throwable) -> {
            if (throwable != null) {
                consumer.accept(throwable);
            }
        }, senderExecutor(plugin, sender));
    }
}
