package org.popcraft.bolt.data;

import org.popcraft.bolt.access.AccessList;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.util.BlockLocation;
import org.popcraft.bolt.util.Group;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Store {
    CompletableFuture<BlockProtection> loadBlockProtection(BlockLocation location);

    CompletableFuture<Collection<BlockProtection>> loadBlockProtections();

    default CompletableFuture<Collection<BlockProtection>> loadBlockProtections(final String world, final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
        return loadBlockProtections().thenApply(protections -> protections.stream()
                .filter(protection -> world.equals(protection.getWorld()))
                .filter(protection -> protection.getX() >= minX && protection.getX() <= maxX)
                .filter(protection -> protection.getY() >= minY && protection.getY() <= maxY)
                .filter(protection -> protection.getZ() >= minZ && protection.getZ() <= maxZ)
                .toList());
    }

    void saveBlockProtection(BlockProtection protection);

    void removeBlockProtection(BlockProtection protection);

    CompletableFuture<EntityProtection> loadEntityProtection(UUID id);

    CompletableFuture<Collection<EntityProtection>> loadEntityProtections();

    void saveEntityProtection(EntityProtection protection);

    void removeEntityProtection(EntityProtection protection);

    CompletableFuture<Group> loadGroup(String group);

    CompletableFuture<Collection<Group>> loadGroups();

    void saveGroup(Group group);

    void removeGroup(Group group);

    CompletableFuture<AccessList> loadAccessList(UUID owner);

    CompletableFuture<Collection<AccessList>> loadAccessLists();

    void saveAccessList(AccessList accessList);

    void removeAccessList(AccessList accessList);

    long pendingSave();

    CompletableFuture<Void> flush();

    default StorageHealth health() {
        return StorageHealth.HEALTHY;
    }

    default CompletableFuture<Path> emergencyDump(final Path directory) {
        return CompletableFuture.completedFuture(null);
    }
}
