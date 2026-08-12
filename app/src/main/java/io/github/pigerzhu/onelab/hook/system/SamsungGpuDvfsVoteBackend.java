package io.github.pigerzhu.onelab.hook.system;

import android.content.Context;
import de.robv.android.xposed.XposedHelpers;

import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;

public final class SamsungGpuDvfsVoteBackend implements GpuDvfsVoteBackend {
    private static final String DVFS_MANAGER_CLASS =
            "com.samsung.android.os.SemDvfsManager";
    private static final String MINIMUM_TAG = "OneLab_GPU_FREQ_MIN";
    private static final String MAXIMUM_TAG = "OneLab_GPU_FREQ_MAX";
    private static final int MINIMUM_DVFS_TYPE = 16;
    private static final int MAXIMUM_DVFS_TYPE = 17;

    private final Context context;
    private final ClassLoader classLoader;
    private final DvfsOperations operations;

    private Object minimumVote;
    private Object maximumVote;

    public SamsungGpuDvfsVoteBackend(Context context, ClassLoader classLoader) {
        this(context, classLoader, new XposedDvfsOperations());
    }

    SamsungGpuDvfsVoteBackend(Context context, ClassLoader classLoader, DvfsOperations operations) {
        this.context = context;
        this.classLoader = classLoader;
        this.operations = operations;
    }

    @Override
    public boolean acquireMinimum(int mhz) {
        try {
            Object vote = minimumVote;
            if (vote == null) {
                vote = createVote(MINIMUM_TAG, MINIMUM_DVFS_TYPE);
                minimumVote = vote;
            }
            return acquireSupported(vote, mhz);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean acquireMaximum(int mhz) {
        try {
            Object vote = maximumVote;
            if (vote == null) {
                vote = createVote(MAXIMUM_TAG, MAXIMUM_DVFS_TYPE);
                maximumVote = vote;
            }
            return acquireSupported(vote, mhz);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void releaseAll() {
        if (releaseVote(minimumVote)) {
            minimumVote = null;
        }
        if (releaseVote(maximumVote)) {
            maximumVote = null;
        }
    }

    public int[] getCommonSupportedFrequencies() {
        try {
            Object minimum = minimumVote;
            if (minimum == null) {
                minimum = createVote(MINIMUM_TAG, MINIMUM_DVFS_TYPE);
                minimumVote = minimum;
            }
            Object maximum = maximumVote;
            if (maximum == null) {
                maximum = createVote(MAXIMUM_TAG, MAXIMUM_DVFS_TYPE);
                maximumVote = maximum;
            }
            return GpuFrequencyTable.common(
                    operations.getSupportedFrequencyForSsrm(minimum),
                    operations.getSupportedFrequencyForSsrm(maximum));
        } catch (Throwable ignored) {
            return new int[0];
        }
    }

    private Object createVote(String tag, int dvfsType) {
        return operations.createVote(context, classLoader, tag, dvfsType);
    }

    private boolean acquireSupported(Object vote, int mhz) {
        try {
            int[] supported = operations.getSupportedFrequencyForSsrm(vote);
            if (!contains(supported, mhz)) {
                return false;
            }
            operations.setDvfsValue(vote, mhz);
            operations.acquire(vote);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean contains(int[] supported, int mhz) {
        if (supported == null) {
            return false;
        }
        for (int frequency : supported) {
            if (frequency == mhz) {
                return true;
            }
        }
        return false;
    }

    private boolean releaseVote(Object vote) {
        if (vote == null) {
            return true;
        }
        try {
            return operations.release(vote);
        } catch (Throwable t) {
            return false;
        }
    }

    interface DvfsOperations {
        Object createVote(Object context, ClassLoader classLoader, String tag, int dvfsType);

        int[] getSupportedFrequencyForSsrm(Object vote);

        void setDvfsValue(Object vote, int mhz);

        void acquire(Object vote);

        boolean release(Object vote);
    }

    private static final class XposedDvfsOperations implements DvfsOperations {
        @Override
        public Object createVote(Object context, ClassLoader classLoader, String tag, int dvfsType) {
            Class<?> type = XposedHelpers.findClass(DVFS_MANAGER_CLASS, classLoader);
            return XposedHelpers.callStaticMethod(type, "createInstance", context, tag, dvfsType);
        }

        @Override
        public int[] getSupportedFrequencyForSsrm(Object vote) {
            return (int[]) XposedHelpers.callMethod(vote, "getSupportedFrequencyForSsrm");
        }

        @Override
        public void setDvfsValue(Object vote, int mhz) {
            XposedHelpers.callMethod(vote, "setDvfsValue", mhz);
        }

        @Override
        public void acquire(Object vote) {
            XposedHelpers.callMethod(vote, "acquire");
        }

        @Override
        public boolean release(Object vote) {
            try {
                XposedHelpers.callMethod(vote, "release");
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
