package org.popcraft.bolt.util;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

import java.util.Collection;

public final class NearbyEntityLookup {
    private NearbyEntityLookup() {
    }

    /**
     * Performs a bounded nearby-entity lookup for matchers that need attached entities.
     * <p>
     * Folia callers must already be running in the region that owns {@code origin}. The lookup is intentionally
     * constrained to the supplied bounding box and delegates to the server API instead of manually loading chunks or
     * scanning {@code Chunk#getEntities()}.
     */
    public static <T extends Entity> Collection<Entity> find(final Block origin, final BoundingBox boundingBox, final Class<T> entityClass) {
        return FoliaUtil.getNearbyEntities(origin, boundingBox, entityClass::isInstance).stream()
                .filter(entityClass::isInstance)
                .toList();
    }
}
