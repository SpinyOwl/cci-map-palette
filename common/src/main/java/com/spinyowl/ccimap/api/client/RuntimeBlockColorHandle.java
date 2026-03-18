package com.spinyowl.ccimap.api.client;

public interface RuntimeBlockColorHandle extends AutoCloseable {
    void unregister();

    @Override
    default void close() {
        unregister();
    }
}
