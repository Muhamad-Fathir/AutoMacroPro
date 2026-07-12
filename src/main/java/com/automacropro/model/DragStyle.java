package com.automacropro.model;

/**
 * How a {@link ClickMode#DRAG} gesture moves from its start point to its end point.
 * This is configurable per-action (confirmed design decision), since some target
 * applications/games only recognize a drag/swipe when the pointer moves through a
 * continuous path rather than teleporting directly to the destination.
 */
public enum DragStyle {
    /** Press, jump straight to the destination, release. Fastest, smallest footprint. */
    INSTANT,
    /** Press, then move through N interpolated intermediate points before releasing.
     *  Slower but mimics a real human drag/swipe gesture. */
    SMOOTH
}
