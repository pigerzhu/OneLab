package io.github.pigerzhu.onelab.contract;

/** One per-application refresh-rate policy shared by the UI and the system_server hook. */
public final class RefreshRateOverride {
    public final int mode;
    public final float min;
    public final float max;

    public RefreshRateOverride(int mode, float min, float max) {
        this.mode = mode;
        this.min = min;
        this.max = max;
    }
}
