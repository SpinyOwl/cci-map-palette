package com.spinyowl.ccimap.api.client;

/**
 * Handle returned by a runtime fluid color registration.
 */
public interface RuntimeFluidColorHandle {
    /**
     * Unregister this runtime fluid color rule.
     */
    void unregister();
}
