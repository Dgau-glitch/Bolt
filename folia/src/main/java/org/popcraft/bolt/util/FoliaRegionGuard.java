package org.popcraft.bolt.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class FoliaRegionGuard {
    private FoliaRegionGuard() {
    }

    public static boolean owns(final Location location) {
        return !FoliaUtil.isFolia() || Bukkit.isOwnedByCurrentRegion(location);
    }

    public static boolean owns(final Entity entity) {
        return !FoliaUtil.isFolia() || Bukkit.isOwnedByCurrentRegion(entity);
    }

    public static void requireOwned(final Location location, final String operation) {
        if (!owns(location)) {
            throw new IllegalStateException("Folia region violation while " + operation + " at " + location);
        }
    }

    public static void requireOwned(final Entity entity, final String operation) {
        if (!owns(entity)) {
            throw new IllegalStateException("Folia entity region violation while " + operation + " for " + entity.getUniqueId());
        }
    }
}
