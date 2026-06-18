package org.popcraft.bolt.util;

@FunctionalInterface
public interface ScheduledTaskHandle {
    ScheduledTaskHandle NOOP = () -> {
    };

    void cancel();
}
