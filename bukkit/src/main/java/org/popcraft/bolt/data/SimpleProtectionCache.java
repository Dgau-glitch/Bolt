package org.popcraft.bolt.data;

import org.popcraft.bolt.access.AccessList;
import org.popcraft.bolt.protection.BlockProtection;
import org.popcraft.bolt.protection.EntityProtection;
import org.popcraft.bolt.util.BlockLocation;
import org.popcraft.bolt.util.Group;
import org.popcraft.bolt.util.Metrics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleProtectionCache implements Store {
    private final Map<BlockLocation, UUID> cachedBlockLocationId = new ConcurrentHashMap<>();
    private final Map<UUID, BlockLocation> cachedBlockIdLocation = new ConcurrentHashMap<>();
    private final Map<UUID, BlockProtection> cachedBlocks = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, BlockProtection>> cachedBlocksByChunk = new ConcurrentHashMap<>();
    private final Map<UUID, EntityProtection> cachedEntities = new ConcurrentHashMap<>();
    private final Map<String, Group> cachedGroups = new ConcurrentHashMap<>();
    private final Map<UUID, AccessList> cachedAccessLists = new ConcurrentHashMap<>();
    private final Store backingStore;

    public SimpleProtectionCache(final Store backingStore) {
        this.backingStore = backingStore;
        backingStore.loadBlockProtections().join().forEach(blockProtection -> {
            final UUID id = blockProtection.getId();
            final BlockLocation blockLocation = BlockLocation.fromProtection(blockProtection);
            cachedBlockLocationId.put(blockLocation, id);
            cachedBlockIdLocation.put(id, blockLocation);
            cachedBlocks.put(id, blockProtection);
            cachedBlocksByChunk.computeIfAbsent(chunkKey(blockLocation), ignored -> new ConcurrentHashMap<>()).put(id, blockProtection);
        });
        backingStore.loadEntityProtections().join().forEach(entityProtection -> cachedEntities.putIfAbsent(entityProtection.getId(), entityProtection));
        backingStore.loadGroups().join().forEach(group -> cachedGroups.putIfAbsent(group.getName(), group));
        backingStore.loadAccessLists().join().forEach(accessList -> cachedAccessLists.putIfAbsent(accessList.getOwner(), accessList));
    }

    @Override
    public CompletableFuture<BlockProtection> loadBlockProtection(BlockLocation location) {
        final UUID id = cachedBlockLocationId.get(location);
        final BlockProtection blockProtection = id == null ? null : cachedBlocks.get(id);
        Metrics.recordProtectionAccess(blockProtection != null);
        return CompletableFuture.completedFuture(blockProtection);
    }

    @Override
    public CompletableFuture<Collection<BlockProtection>> loadBlockProtections() {
        return CompletableFuture.completedFuture(cachedBlocks.values());
    }

    @Override
    public CompletableFuture<Collection<BlockProtection>> loadBlockProtections(final String world, final int minX, final int minY, final int minZ, final int maxX, final int maxY, final int maxZ) {
        final Collection<BlockProtection> protections = new ArrayList<>();
        for (int chunkX = Math.floorDiv(minX, 16); chunkX <= Math.floorDiv(maxX, 16); ++chunkX) {
            for (int chunkZ = Math.floorDiv(minZ, 16); chunkZ <= Math.floorDiv(maxZ, 16); ++chunkZ) {
                final Map<UUID, BlockProtection> chunkProtections = cachedBlocksByChunk.get(chunkKey(world, chunkX, chunkZ));
                if (chunkProtections == null) {
                    continue;
                }
                chunkProtections.values().stream()
                        .filter(protection -> protection.getX() >= minX && protection.getX() <= maxX)
                        .filter(protection -> protection.getY() >= minY && protection.getY() <= maxY)
                        .filter(protection -> protection.getZ() >= minZ && protection.getZ() <= maxZ)
                        .forEach(protections::add);
            }
        }
        return CompletableFuture.completedFuture(protections);
    }

    @Override
    public void saveBlockProtection(BlockProtection protection) {
        final UUID id = protection.getId();
        final BlockLocation oldBlockLocation = cachedBlockIdLocation.remove(id);
        if (oldBlockLocation != null) {
            cachedBlockLocationId.remove(oldBlockLocation);
            removeFromChunkIndex(id, oldBlockLocation);
        }
        final BlockLocation blockLocation = BlockLocation.fromProtection(protection);
        cachedBlockLocationId.put(blockLocation, id);
        cachedBlockIdLocation.put(id, blockLocation);
        cachedBlocks.put(id, protection);
        cachedBlocksByChunk.computeIfAbsent(chunkKey(blockLocation), ignored -> new ConcurrentHashMap<>()).put(id, protection);
        backingStore.saveBlockProtection(protection);
    }

    @Override
    public void removeBlockProtection(BlockProtection protection) {
        final UUID id = protection.getId();
        final BlockLocation blockLocation = BlockLocation.fromProtection(protection);
        cachedBlockLocationId.remove(blockLocation);
        cachedBlockIdLocation.remove(id);
        cachedBlocks.remove(id);
        removeFromChunkIndex(id, blockLocation);
        backingStore.removeBlockProtection(protection);
    }

    private void removeFromChunkIndex(final UUID id, final BlockLocation blockLocation) {
        final Map<UUID, BlockProtection> chunkProtections = cachedBlocksByChunk.get(chunkKey(blockLocation));
        if (chunkProtections == null) {
            return;
        }
        chunkProtections.remove(id);
        if (chunkProtections.isEmpty()) {
            cachedBlocksByChunk.remove(chunkKey(blockLocation), chunkProtections);
        }
    }

    private String chunkKey(final BlockLocation blockLocation) {
        return chunkKey(blockLocation.world(), Math.floorDiv(blockLocation.x(), 16), Math.floorDiv(blockLocation.z(), 16));
    }

    private String chunkKey(final String world, final int chunkX, final int chunkZ) {
        return world + ':' + chunkX + ':' + chunkZ;
    }

    @Override
    public CompletableFuture<EntityProtection> loadEntityProtection(UUID id) {
        final EntityProtection entityProtection = cachedEntities.get(id);
        Metrics.recordProtectionAccess(entityProtection != null);
        return CompletableFuture.completedFuture(entityProtection);
    }

    @Override
    public CompletableFuture<Collection<EntityProtection>> loadEntityProtections() {
        return CompletableFuture.completedFuture(cachedEntities.values());
    }

    @Override
    public void saveEntityProtection(EntityProtection protection) {
        cachedEntities.put(protection.getId(), protection);
        backingStore.saveEntityProtection(protection);
    }

    @Override
    public void removeEntityProtection(EntityProtection protection) {
        cachedEntities.remove(protection.getId());
        backingStore.removeEntityProtection(protection);
    }

    @Override
    public CompletableFuture<Group> loadGroup(String group) {
        return CompletableFuture.completedFuture(cachedGroups.get(group));
    }

    @Override
    public CompletableFuture<Collection<Group>> loadGroups() {
        return CompletableFuture.completedFuture(cachedGroups.values());
    }

    @Override
    public void saveGroup(Group group) {
        cachedGroups.put(group.getName(), group);
        backingStore.saveGroup(group);
    }

    @Override
    public void removeGroup(Group group) {
        cachedGroups.remove(group.getName());
        backingStore.removeGroup(group);
    }

    @Override
    public CompletableFuture<AccessList> loadAccessList(UUID owner) {
        return CompletableFuture.completedFuture(cachedAccessLists.get(owner));
    }

    @Override
    public CompletableFuture<Collection<AccessList>> loadAccessLists() {
        return CompletableFuture.completedFuture(cachedAccessLists.values());
    }

    @Override
    public void saveAccessList(AccessList accessList) {
        cachedAccessLists.put(accessList.getOwner(), accessList);
        backingStore.saveAccessList(accessList);
    }

    @Override
    public void removeAccessList(AccessList accessList) {
        cachedAccessLists.remove(accessList.getOwner());
        backingStore.removeAccessList(accessList);
    }

    @Override
    public long pendingSave() {
        return backingStore.pendingSave();
    }

    @Override
    public CompletableFuture<Void> flush() {
        return backingStore.flush();
    }

    @Override
    public StorageHealth health() {
        return backingStore.health();
    }

    @Override
    public CompletableFuture<Path> emergencyDump(final Path directory) {
        return backingStore.emergencyDump(directory);
    }
}
