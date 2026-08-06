package io.github.pigerzhu.onelab.hook.system;

import android.content.Context;
import de.robv.android.xposed.XposedHelpers;

public final class SamsungGpuDvfsVoteBackend implements GpuDvfsVoteBackend {
    private static final String DVFS_MANAGER_CLASS =
            "com.samsung.android.os.SemDvfsManager";
    private static final String MINIMUM_TAG = "OneLab_GPU_FREQ_MIN";
    private static final String MAXIMUM_TAG = "OneLab_GPU_FREQ_MAX";
    private static final int MINIMUM_DVFS_TYPE = 16;
    private static final int MAXIMUM_DVFS_TYPE = 17;

    private final Context context;
    private final ClassLoader classLoader;

    private Object minimumVote;
    private Object maximumVote;

    public SamsungGpuDvfsVoteBackend(Context context, ClassLoader classLoader) {
        this.context = context;
        this.classLoader = classLoader;
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
        releaseVote(minimumVote);
        releaseVote(maximumVote);
        minimumVote = null;
        maximumVote = null;
    }

    private Object createVote(String tag, int dvfsType) {
        Class<?> type = XposedHelpers.findClass(DVFS_MANAGER_CLASS, classLoader);
        return XposedHelpers.callStaticMethod(type, "createInstance", context, tag, dvfsType);
    }

    private boolean acquireSupported(Object vote, int mhz) {
        try {
            int[] supported = (int[]) XposedHelpers.callMethod(
                    vote, "getSupportedFrequencyForSsrm");
            if (!contains(supported, mhz)) {
                return false;
            }
            XposedHelpers.callMethod(vote, "setDvfsValue", mhz);
            XposedHelpers.callMethod(vote, "acquire");
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

    private static void releaseVote(Object vote) {
        if (vote == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(vote, "release");
        } catch (Throwable t) {
            // Best effort: failing to release must not block fail-open recovery.
        }
    }
}
