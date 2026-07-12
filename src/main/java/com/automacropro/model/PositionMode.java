package com.automacropro.model;

/**
 * How the X/Y position for a mouse action is resolved at execution time.
 */
public enum PositionMode {
    /** Use wherever the OS cursor happens to be at the moment the action runs. */
    CURRENT_CURSOR,
    /** Use an explicit, previously recorded X/Y coordinate. */
    FIXED_COORDINATE
}
