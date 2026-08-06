package io.github.pigerzhu.onelab.hook.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;
import io.github.pigerzhu.onelab.hook.system.GpuFrequencyRangeController.Status;
import org.junit.Test;

public final class GpuFrequencyRangeControllerTest {
    @Test
    public void activeRequiresBothVotes() {
        FakeBackend backend = new FakeBackend(true, true);
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.ACTIVE,
                controller.apply(true, GpuFrequencyRange.normalize(231, 770)));

        assertEquals(231, backend.minimum);
        assertEquals(770, backend.maximum);
    }

    @Test
    public void minimumFailureReleasesEveryOwnedVote() {
        FakeBackend backend = new FakeBackend(false, true);
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.MIN_UNAVAILABLE,
                controller.apply(true, GpuFrequencyRange.normalize(231, 770)));

        assertTrue(backend.released);
    }

    @Test
    public void maximumFailureReleasesEveryOwnedVote() {
        FakeBackend backend = new FakeBackend(true, false);
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.MAX_UNAVAILABLE,
                controller.apply(true, GpuFrequencyRange.normalize(231, 770)));

        assertTrue(backend.released);
    }

    @Test
    public void backendExceptionReleasesEveryOwnedVote() {
        FakeBackend backend = new FakeBackend(true, true);
        backend.throwOnMaximum = true;
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.FAILED,
                controller.apply(true, GpuFrequencyRange.normalize(231, 770)));

        assertTrue(backend.released);
    }

    @Test
    public void backendThrowableReleasesEveryOwnedVote() {
        FakeBackend backend = new FakeBackend(true, true);
        backend.throwErrorOnMaximum = true;
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.FAILED,
                controller.apply(true, GpuFrequencyRange.normalize(231, 770)));

        assertTrue(backend.released);
    }

    @Test
    public void disablingReleasesVotes() {
        FakeBackend backend = new FakeBackend(true, true);
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);

        assertEquals(Status.DISABLED,
                controller.apply(false, GpuFrequencyRange.normalize(80, 1000)));

        assertTrue(backend.released);
    }

    @Test
    public void repeatedSameRangeDoesNotAcquireOrReleaseAgain() {
        FakeBackend backend = new FakeBackend(true, true);
        GpuFrequencyRangeController controller = new GpuFrequencyRangeController(backend);
        GpuFrequencyRange range = GpuFrequencyRange.normalize(231, 770);

        assertEquals(Status.ACTIVE, controller.apply(true, range));
        assertEquals(Status.ACTIVE, controller.apply(true, range));

        assertEquals(1, backend.minimumAcquireCount);
        assertEquals(1, backend.maximumAcquireCount);
        assertEquals(0, backend.releaseCount);
    }

    private static final class FakeBackend implements GpuDvfsVoteBackend {
        private final boolean minimumAvailable;
        private final boolean maximumAvailable;

        int minimum;
        int maximum;
        int minimumAcquireCount;
        int maximumAcquireCount;
        int releaseCount;
        boolean released;
        boolean throwOnMaximum;
        boolean throwErrorOnMaximum;

        FakeBackend(boolean minimumAvailable, boolean maximumAvailable) {
            this.minimumAvailable = minimumAvailable;
            this.maximumAvailable = maximumAvailable;
        }

        @Override
        public boolean acquireMinimum(int mhz) {
            minimum = mhz;
            minimumAcquireCount++;
            return minimumAvailable;
        }

        @Override
        public boolean acquireMaximum(int mhz) {
            if (throwOnMaximum) {
                throw new IllegalStateException("maximum unavailable");
            }
            if (throwErrorOnMaximum) {
                throw new LinkageError("xposed class unavailable");
            }
            maximum = mhz;
            maximumAcquireCount++;
            return maximumAvailable;
        }

        @Override
        public void releaseAll() {
            released = true;
            releaseCount++;
        }
    }
}
