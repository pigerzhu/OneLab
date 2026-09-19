package io.github.pigerzhu.onelab.hook.applications;

/** Pure state transition policy for per-display Samsung recent-app layouts. */
public final class SamsungRecentsLayoutPolicy {
    public static final int LAYOUT_LIST = 0;
    public static final int LAYOUT_GRID = 1;
    public static final int LAYOUT_STACK = 2;
    public static final int LAYOUT_VERTICAL = 3;
    public static final int LAYOUT_SLIM = 4;
    public static final int LAYOUT_TILT_STACK = 5;
    public static final int DISPLAY_TYPE_COVER = 5;

    private SamsungRecentsLayoutPolicy() {
    }

    public static boolean isSupportedLayout(int layout) {
        return layout >= LAYOUT_LIST && layout <= LAYOUT_TILT_STACK;
    }

    public static boolean isCoverDisplay(int displayType) {
        return displayType == DISPLAY_TYPE_COVER;
    }

    public static Integer selectSavedLayout(
            boolean enabled,
            boolean samsungForced,
            int displayType,
            int mainLayout,
            int coverLayout) {
        if (!enabled || samsungForced) return null;
        int selected = isCoverDisplay(displayType) ? coverLayout : mainLayout;
        return isSupportedLayout(selected) ? selected : null;
    }

    public static UpdateResult resolve(UpdateInput input) {
        Integer observedHomeUp = isSupportedLayout(input.homeUpLayout)
                ? input.homeUpLayout : null;
        if (!input.enabled) {
            return new UpdateResult(null, null, false, null, observedHomeUp);
        }

        Integer mainWrite = null;
        Integer coverWrite = null;
        boolean markInitialized = false;
        int mainLayout = input.savedMainLayout;
        int coverLayout = input.savedCoverLayout;

        if (!input.initialized && observedHomeUp != null) {
            mainWrite = observedHomeUp;
            coverWrite = observedHomeUp;
            mainLayout = observedHomeUp;
            coverLayout = observedHomeUp;
            markInitialized = true;
        } else if (input.initialized
                && observedHomeUp != null
                && input.lastObservedHomeUpLayout != null
                && !observedHomeUp.equals(input.lastObservedHomeUpLayout)) {
            if (isCoverDisplay(input.displayType)) {
                coverWrite = observedHomeUp;
                coverLayout = observedHomeUp;
            } else {
                mainWrite = observedHomeUp;
                mainLayout = observedHomeUp;
            }
        }

        Integer finalLayout = selectSavedLayout(
                true, false, input.displayType, mainLayout, coverLayout);
        return new UpdateResult(
                mainWrite,
                coverWrite,
                markInitialized,
                finalLayout,
                observedHomeUp != null ? observedHomeUp : input.lastObservedHomeUpLayout);
    }

    public static final class UpdateInput {
        public final boolean enabled;
        public final boolean initialized;
        public final int displayType;
        public final int homeUpLayout;
        public final Integer lastObservedHomeUpLayout;
        public final int savedMainLayout;
        public final int savedCoverLayout;

        public UpdateInput(
                boolean enabled,
                boolean initialized,
                int displayType,
                int homeUpLayout,
                Integer lastObservedHomeUpLayout,
                int savedMainLayout,
                int savedCoverLayout) {
            this.enabled = enabled;
            this.initialized = initialized;
            this.displayType = displayType;
            this.homeUpLayout = homeUpLayout;
            this.lastObservedHomeUpLayout = lastObservedHomeUpLayout;
            this.savedMainLayout = savedMainLayout;
            this.savedCoverLayout = savedCoverLayout;
        }
    }

    public static final class UpdateResult {
        public final Integer mainWrite;
        public final Integer coverWrite;
        public final boolean markInitialized;
        public final Integer finalLayout;
        public final Integer nextObservedHomeUpLayout;

        UpdateResult(
                Integer mainWrite,
                Integer coverWrite,
                boolean markInitialized,
                Integer finalLayout,
                Integer nextObservedHomeUpLayout) {
            this.mainWrite = mainWrite;
            this.coverWrite = coverWrite;
            this.markInitialized = markInitialized;
            this.finalLayout = finalLayout;
            this.nextObservedHomeUpLayout = nextObservedHomeUpLayout;
        }
    }
}
