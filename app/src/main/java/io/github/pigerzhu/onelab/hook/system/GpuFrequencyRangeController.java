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
            releaseAfterFailure();
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
                releaseAfterFailure();
                return Status.MIN_UNAVAILABLE;
            }
            if (!backend.acquireMaximum(range.maxMhz())) {
                releaseAfterFailure();
                return Status.MAX_UNAVAILABLE;
            }
        } catch (Throwable t) {
            releaseAfterFailure();
            return Status.FAILED;
        }
        active = true;
        activeMinMhz = range.minMhz();
        activeMaxMhz = range.maxMhz();
        return Status.ACTIVE;
    }

    private void releaseActiveVotes() {
        if (!active) {
            return;
        }
        releaseAfterFailure();
    }

    private void releaseAfterFailure() {
        active = false;
        activeMinMhz = 0;
        activeMaxMhz = 0;
        backend.releaseAll();
    }

    public enum Status {
        DISABLED,
        ACTIVE,
        MIN_UNAVAILABLE,
        MAX_UNAVAILABLE,
        FAILED
    }
}
