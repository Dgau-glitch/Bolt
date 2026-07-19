package org.popcraft.bolt.data;

/**
 * Snapshot of a Store's durable backing state.
 */
public record StorageHealth(
        long failedFlushes,
        long successfulFlushes,
        String lastError,
        long lastFailureTime,
        long lastSuccessTime,
        boolean degraded
) {
    public static final StorageHealth HEALTHY = new StorageHealth(0, 0, "", 0, 0, false);
}
