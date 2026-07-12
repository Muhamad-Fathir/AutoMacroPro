package com.automacropro.model;

/**
 * The gesture performed at the resolved position.
 */
public enum ClickMode {
    SINGLE,
    DOUBLE,
    DRAG,
    /** Press the button down, hold for a configured duration, then release. */
    HOLD
}
