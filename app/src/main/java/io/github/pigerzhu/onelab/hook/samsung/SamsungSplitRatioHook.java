package io.github.pigerzhu.onelab.hook.samsung;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_XHS_IMAGE_FULLSCREEN;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;
import io.github.pigerzhu.onelab.contract.XhsImageFullscreenContract;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

/** Applies fixed per-package bounds to Samsung split-activity LEFT/RIGHT groups. */
public final class SamsungSplitRatioHook {
    private static final String TAG = "OneLab/SamsungSplitRatio";
    private static final String ACTIVITY_RECORD_CLASS =
            "com.android.server.wm.ActivityRecord";
    private static final String ATM_SERVICE_CLASS =
            "com.android.server.wm.ActivityTaskManagerService";
    private static final String WEIBO_PACKAGE = "com.sina.weibo";
    private static final String XHS_PACKAGE = "com.xingin.xhs";

    private static volatile Map<String, Float> ratios = Collections.emptyMap();
    private static volatile boolean observerRegistered;
    private static volatile boolean xhsFullscreenEnabled;
    private static volatile boolean xhsViewerVisible;
    private static Object xhsRightGroup;
    private static Object xhsTask;
    private static Rect xhsRightBounds;

    private SamsungSplitRatioHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityRecordClass =
                    XposedHelpers.findClass(ACTIVITY_RECORD_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    activityRecordClass,
                    "onConfigurationChanged",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            applyCustomBounds(param.thisObject);
                        }
                    });
            XposedBridge.hookAllMethods(
                    activityRecordClass,
                    "reparentGroup",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            applyCustomBounds(param.thisObject, true);
                        }
                    });
            XposedBridge.hookAllMethods(
                    activityRecordClass,
                    "commitVisibility",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object activityRecord = param.thisObject;
                            if (param.args.length == 0
                                    || !Boolean.FALSE.equals(param.args[0])) {
                                return;
                            }
                            Object packageName = HookUtils.findFieldValue(
                                    activityRecord, "packageName");
                            if (XHS_PACKAGE.equals(packageName)
                                    && Boolean.TRUE.equals(HookUtils.findFieldValue(
                                    activityRecord, "finishing"))
                                    && isXhsFullscreenActive()) {
                                xhsViewerVisible = false;
                                applyXhsFullscreenBounds();
                            }
                            if (!Boolean.TRUE.equals(
                                    HookUtils.findFieldValue(activityRecord, "finishing"))) {
                                return;
                            }
                            if (!WEIBO_PACKAGE.equals(
                                    packageName)) {
                                return;
                            }
                            try {
                                Object group = HookUtils.findFieldValue(
                                        activityRecord, "mActivityGroup");
                                if (group != null) {
                                    XposedHelpers.callMethod(
                                            group, "removeChild", activityRecord, false);
                                }
                            } catch (Throwable ignored) {
                                // Keep Samsung's native group cleanup if internals differ.
                            }
                        }
                    });

            Class<?> atmServiceClass =
                    XposedHelpers.findClass(ATM_SERVICE_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    atmServiceClass,
                    "onSystemReady",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object context = HookUtils.firstContextFromObject(param.thisObject);
                            initialize(context instanceof Context ? (Context) context : null);
                        }
                    });
            XposedBridge.log(TAG + ": installed");
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void initialize(Context context) {
        if (context == null) return;
        ContentResolver resolver = context.getContentResolver();
        refresh(resolver);
        if (observerRegistered) return;
        synchronized (SamsungSplitRatioHook.class) {
            if (observerRegistered) return;
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_SPLIT_VIEW_RATIO_OVERRIDES),
                    false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            refresh(resolver);
                        }
                    });
            ContentObserver fullscreenObserver = new ContentObserver(
                    new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    refreshXhsFullscreenEnabled(resolver);
                    applyXhsFullscreenBounds();
                }
            };
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN),
                    false, fullscreenObserver);
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_ENABLE_XHS_IMAGE_FULLSCREEN),
                    false, fullscreenObserver);
            refreshXhsFullscreenEnabled(resolver);
            registerXhsViewerReceiver(context);
            observerRegistered = true;
        }
    }

    private static void registerXhsViewerReceiver(Context context) {
        IntentFilter filter = new IntentFilter(XhsImageFullscreenContract.ACTION_VIEWER_STATE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (!XhsImageFullscreenContract.ACTION_VIEWER_STATE.equals(intent.getAction())) {
                    return;
                }
                xhsViewerVisible = intent.getBooleanExtra(
                        XhsImageFullscreenContract.EXTRA_VISIBLE, false);
                applyXhsFullscreenBounds();
            }
        }, filter, Context.RECEIVER_EXPORTED);
    }

    private static void refreshXhsFullscreenEnabled(ContentResolver resolver) {
        xhsFullscreenEnabled = Settings.Global.getInt(
                resolver, KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN, 0) == 1
                && Settings.Global.getInt(
                resolver, KEY_ENABLE_XHS_IMAGE_FULLSCREEN, 0) == 1;
    }

    private static void refresh(ContentResolver resolver) {
        ratios = SplitViewRatioOverrides.immutableSnapshot(
                Settings.Global.getString(resolver, KEY_SPLIT_VIEW_RATIO_OVERRIDES));
    }

    private static void applyCustomBounds(Object activityRecord) {
        applyCustomBounds(activityRecord, false);
    }

    private static void applyCustomBounds(Object activityRecord, boolean updateActivities) {
        Object group = HookUtils.findFieldValue(activityRecord, "mActivityGroup");
        Object zoneValue = HookUtils.findFieldValue(group, "mZone");
        if (!(zoneValue instanceof Integer)) return;
        int zone = (Integer) zoneValue;
        if (zone != 0 && zone != 1) return;

        Object packageValue = HookUtils.findFieldValue(activityRecord, "packageName");
        if (!(packageValue instanceof String)) return;
        if (XHS_PACKAGE.equals(packageValue) && zone == 1) {
            rememberXhsRightGroup(activityRecord, group);
            if (isXhsFullscreenActive()) {
                applyXhsFullscreenBounds();
                return;
            }
        }
        Float ratioValue = ratios.get(packageValue);
        if (ratioValue == null) return;
        float ratio = ratioValue;
        if (ratio <= 0f || ratio >= 1f || !Float.isFinite(ratio)) return;

        Object task = HookUtils.findFieldValue(activityRecord, "task");
        Object groups = HookUtils.findFieldValue(task, "mActivityGroups");
        if (groups == null) return;
        try {
            Object leftGroup = XposedHelpers.callMethod(groups, "get", 0);
            Object rightGroup = XposedHelpers.callMethod(groups, "get", 1);
            Rect leftBounds = (Rect) HookUtils.findFieldValue(leftGroup, "mBounds");
            Rect rightBounds = (Rect) HookUtils.findFieldValue(rightGroup, "mBounds");
            Rect taskBounds = (Rect) XposedHelpers.callMethod(task, "getBounds");
            if (leftBounds == null || rightBounds == null || taskBounds == null
                    || taskBounds.width() <= 1) {
                return;
            }

            int splitX = taskBounds.left + Math.round(taskBounds.width() * ratio);
            splitX = Math.max(taskBounds.left + 1, Math.min(taskBounds.right - 1, splitX));
            leftBounds.set(taskBounds.left, taskBounds.top, splitX, taskBounds.bottom);
            rightBounds.set(splitX, taskBounds.top, taskBounds.right, taskBounds.bottom);
            if (updateActivities) {
                applyBoundsToChildren(leftGroup, leftBounds);
                applyBoundsToChildren(rightGroup, rightBounds);
            }
        } catch (Throwable ignored) {
            // Preserve Samsung's default 1:1 bounds if this firmware changes group internals.
        }
    }

    private static void applyBoundsToChildren(Object group, Rect bounds) {
        Object children = HookUtils.findFieldValue(group, "mChildren");
        if (!(children instanceof List<?>)) return;
        for (Object child : (List<?>) children) {
            XposedHelpers.callMethod(child, "setBounds", new Rect(bounds));
        }
    }

    private static synchronized void rememberXhsRightGroup(Object activityRecord, Object group) {
        Object task = HookUtils.findFieldValue(activityRecord, "task");
        Rect bounds = (Rect) HookUtils.findFieldValue(group, "mBounds");
        if (task == null || bounds == null || bounds.width() <= 1) return;
        xhsRightGroup = group;
        xhsTask = task;
        if (!isXhsFullscreenActive()) xhsRightBounds = new Rect(bounds);
    }

    private static synchronized void applyXhsFullscreenBounds() {
        if (xhsRightGroup == null || xhsTask == null || xhsRightBounds == null) return;
        try {
            Rect taskBounds = (Rect) XposedHelpers.callMethod(xhsTask, "getBounds");
            if (taskBounds == null || taskBounds.width() <= 1) return;
            int[] target = XhsFullscreenBoundsPolicy.targetBounds(
                    rectValues(taskBounds), rectValues(xhsRightBounds),
                    isXhsFullscreenActive());
            if (target == null) return;
            Rect bounds = new Rect(target[0], target[1], target[2], target[3]);
            Rect groupBounds = (Rect) HookUtils.findFieldValue(xhsRightGroup, "mBounds");
            if (groupBounds != null) groupBounds.set(bounds);
            applyBoundsToChildren(xhsRightGroup, bounds);
            XposedBridge.log(TAG + ": XHS image viewer "
                    + (isXhsFullscreenActive() ? "expanded " : "restored ") + bounds);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": XHS image-viewer bounds failed");
            XposedBridge.log(throwable);
        }
    }

    private static boolean isXhsFullscreenActive() {
        return xhsFullscreenEnabled && xhsViewerVisible;
    }

    private static int[] rectValues(Rect rect) {
        return new int[]{rect.left, rect.top, rect.right, rect.bottom};
    }
}
