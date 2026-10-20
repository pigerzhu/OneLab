package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Arrays;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.contract.GpuFrequencyRange;
import io.github.pigerzhu.onelab.contract.GpuFrequencyTable;

import de.robv.android.xposed.XposedBridge;

/** In-process settings snapshot for SDHMS hooks. */
final class SdhmsHookConfig {
    private static final Object LOCK = new Object();
    private static volatile Snapshot snapshot = Snapshot.defaults();
    private static volatile ContentResolver resolver;
    private static volatile boolean observerRegistered;
    private static volatile SnapshotListener snapshotListener;
    private static volatile int[] runtimeGpuFrequencies = new int[0];

    private SdhmsHookConfig() {
    }

    static Snapshot current(ContentResolver candidate) {
        ensureObserver(candidate);
        return snapshot;
    }

    static void observe(ContentResolver candidate, SnapshotListener listener) {
        snapshotListener = listener;
        ensureObserver(candidate);
        notifyListener(snapshot);
    }

    static void setRuntimeGpuFrequencies(int[] frequencies) {
        runtimeGpuFrequencies = frequencies == null
                ? new int[0]
                : Arrays.copyOf(frequencies, frequencies.length);
        reload();
    }

    private static void ensureObserver(ContentResolver candidate) {
        if (candidate == null || observerRegistered) {
            return;
        }
        synchronized (LOCK) {
            if (observerRegistered) {
                return;
            }
            resolver = candidate;
            Handler handler = new Handler(Looper.getMainLooper());
            ContentObserver observer = new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    reload();
                }
            };
            try {
                register(candidate, observer, SettingsKeys.KEY_ENABLE_SDHMS_THERMAL);
                register(candidate, observer, SettingsKeys.KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT);
                register(candidate, observer, SettingsKeys.KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION);
                register(candidate, observer, SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS);
                register(candidate, observer, SettingsKeys.KEY_ENABLE_SDHMS_CPU_CAP_RELEASE);
                register(candidate, observer, SettingsKeys.KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT);
                register(candidate, observer, SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT);
                register(candidate, observer, SettingsKeys.KEY_GPU_RANGE_MIN_MHZ);
                register(candidate, observer, SettingsKeys.KEY_GPU_RANGE_MAX_MHZ);
                register(candidate, observer, SettingsKeys.KEY_GPU_SUPPORTED_FREQUENCIES);
                reload();
                observerRegistered = true;
            } catch (Throwable t) {
                XposedBridge.log(HookConstants.TAG + ": SDHMS settings observer failed");
                XposedBridge.log(t);
            }
        }
    }

    private static void register(
            ContentResolver contentResolver,
            ContentObserver observer,
            String key
    ) {
        contentResolver.registerContentObserver(
                Settings.Global.getUriFor(key),
                false,
                observer
        );
    }

    private static void reload() {
        ContentResolver contentResolver = resolver;
        if (contentResolver == null) {
            return;
        }
        try {
            int[] gpuFrequencies = Arrays.copyOf(
                    runtimeGpuFrequencies, runtimeGpuFrequencies.length);
            GpuFrequencyRange gpuRange = GpuFrequencyTable.isUsable(gpuFrequencies)
                    ? GpuFrequencyRange.normalize(
                            Settings.Global.getInt(contentResolver,
                                    SettingsKeys.KEY_GPU_RANGE_MIN_MHZ, gpuFrequencies[0]),
                            Settings.Global.getInt(contentResolver,
                                    SettingsKeys.KEY_GPU_RANGE_MAX_MHZ,
                                    gpuFrequencies[gpuFrequencies.length - 1]),
                            gpuFrequencies)
                    : null;
            Snapshot updated = new Snapshot(
                    enabled(contentResolver, SettingsKeys.KEY_ENABLE_SDHMS_THERMAL, 0),
                    enabled(contentResolver, SettingsKeys.KEY_DISABLE_SDHMS_BRIGHTNESS_LIMIT, 0),
                    enabled(contentResolver, SettingsKeys.KEY_DISABLE_SDHMS_CP_THERMAL_MITIGATION, 0),
                    enabled(contentResolver, SettingsKeys.KEY_ENABLE_SDHMS_PERF_CAP_BYPASS, 0),
                    enabled(contentResolver, SettingsKeys.KEY_ENABLE_SDHMS_CPU_CAP_RELEASE, 1),
                    enabled(contentResolver, SettingsKeys.KEY_DISABLE_SSRM_MULTIWINDOW_LIMIT, 0),
                    enabled(contentResolver, SettingsKeys.KEY_ENABLE_GPU_RANGE_EXPERIMENT, 0),
                    gpuFrequencies,
                    gpuRange
            );
            snapshot = updated;
            notifyListener(updated);
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS settings reload failed");
            XposedBridge.log(t);
        }
    }

    private static boolean enabled(ContentResolver contentResolver, String key, int fallback) {
        return Settings.Global.getInt(contentResolver, key, fallback) == 1;
    }

    private static void notifyListener(Snapshot updated) {
        SnapshotListener listener = snapshotListener;
        if (listener == null) return;
        try {
            listener.onChanged(updated);
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": SDHMS settings listener failed");
            XposedBridge.log(t);
        }
    }

    interface SnapshotListener {
        void onChanged(Snapshot snapshot);
    }

    static final class Snapshot {
        final boolean thermalEnabled;
        final boolean brightnessLimitDisabled;
        final boolean cpThermalMitigationDisabled;
        final boolean perfCapBypassEnabled;
        final boolean cpuCapReleaseEnabled;
        final boolean ssrmMultiWindowLimitDisabled;
        final boolean gpuRangeExperimentEnabled;
        final int[] gpuFrequencies;
        final GpuFrequencyRange gpuRange;

        Snapshot(
                boolean thermalEnabled,
                boolean brightnessLimitDisabled,
                boolean cpThermalMitigationDisabled,
                boolean perfCapBypassEnabled,
                boolean cpuCapReleaseEnabled,
                boolean ssrmMultiWindowLimitDisabled,
                boolean gpuRangeExperimentEnabled,
                int[] gpuFrequencies,
                GpuFrequencyRange gpuRange
        ) {
            this.thermalEnabled = thermalEnabled;
            this.brightnessLimitDisabled = brightnessLimitDisabled;
            this.cpThermalMitigationDisabled = cpThermalMitigationDisabled;
            this.perfCapBypassEnabled = perfCapBypassEnabled;
            this.cpuCapReleaseEnabled = cpuCapReleaseEnabled;
            this.ssrmMultiWindowLimitDisabled = ssrmMultiWindowLimitDisabled;
            this.gpuRangeExperimentEnabled = gpuRangeExperimentEnabled;
            this.gpuFrequencies = gpuFrequencies;
            this.gpuRange = gpuRange;
        }

        static Snapshot defaults() {
            return new Snapshot(
                    false,
                    false,
                    false,
                    false,
                    true,
                    false,
                    false,
                    new int[0],
                    null
            );
        }
    }
}
