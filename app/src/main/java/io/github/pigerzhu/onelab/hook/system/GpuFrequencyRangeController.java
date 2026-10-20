package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;

public final class GpuFrequencyRangeController {
    private final GpuDvfsVoteBackend backend;

    private boolean active;
    private int activeMinMhz;
    private int activeMaxMhz;

    public GpuFrequencyRangeController(GpuDvfsVoteBackend backend) {
        this.backend = backend;
    }

    public Status apply(boolean enabled, GpuFrequencyRange range) {
        if (!enabled) {
            safeReleaseAll();
            clearActiveState();
            return Status.DISABLED;
        }
        if (active
                && activeMinMhz == range.minMhz()
                && activeMaxMhz == range.maxMhz()) {
            return Status.ACTIVE;
        }
        releaseActiveVotes();
        try {
            if (!backend.acquireMinimum(range.minMhz())) {
                safeReleaseAll();
                return Status.MIN_UNAVAILABLE;
            }
            if (!backend.acquireMaximum(range.maxMhz())) {
                safeReleaseAll();
                return Status.MAX_UNAVAILABLE;
            }
        } catch (Throwable t) {
            safeReleaseAll();
            return Status.FAILED;
        }
        active = true;
        activeMinMhz = range.minMhz();
        activeMaxMhz = range.maxMhz();
        return Status.ACTIVE;
    }

    private void safeReleaseAll() {
        try {
            backend.releaseAll();
        } catch (Throwable t) {
            // Cleanup is best-effort; fail-open means return the intended status.
        }
    }

    private void releaseActiveVotes() {
        if (!active) {
            return;
        }
        safeReleaseAll();
        clearActiveState();
    }

    private void clearActiveState() {
        active = false;
        activeMinMhz = 0;
        activeMaxMhz = 0;
    }

    public enum Status {
        DISABLED,
        ACTIVE,
        MIN_UNAVAILABLE,
        MAX_UNAVAILABLE,
        FREQUENCIES_UNAVAILABLE,
        FAILED
    }
}
