package com.pdp;

/**
 * Called when a DSM variable changes.
 * Must be delivered in the same total order (for the messages received by that process).
 */
@FunctionalInterface
public interface DsmChangeListener {
    void onChange(long seq, String varName, int newValue);
}
