package io.github.pigerzhu.onelab.navigation;

/** Selects page animation direction independently from the current window layout. */
public final class PageNavigationPolicy {
    private PageNavigationPolicy() {}

    public static int direction(boolean returningToParent, boolean largeScreenLayout) {
        if (returningToParent) return -1;
        return largeScreenLayout ? 0 : 1;
    }
}
