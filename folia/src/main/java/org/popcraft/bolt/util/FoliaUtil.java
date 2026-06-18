package org.popcraft.bolt.util;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

public class FoliaUtil {
    private static final boolean CONFIG_EXISTS = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaUtil() {
    }

    public static boolean isFolia() {
        return CONFIG_EXISTS;
    }

    public static Collection<Entity> getNearbyEntities(final Block block, final BoundingBox boundingBox, final Predicate<Entity> filter) {
        if (isFolia()) {
            return Collections.emptyList();
        }
        final World world = block.getWorld();
        return world.getNearbyEntities(boundingBox, filter);
    }

    private static boolean classExists(final String clazz) {
        try {
            Class.forName(clazz);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
