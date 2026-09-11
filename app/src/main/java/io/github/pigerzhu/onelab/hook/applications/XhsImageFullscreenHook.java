package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_XHS_IMAGE_FULLSCREEN;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.XhsImageFullscreenContract;

/** Detects XHS's native full-height image viewer and reports its lifecycle to system_server. */
public final class XhsImageFullscreenHook {
    private static final String TAG = "OneLab/XhsImageFullscreen";

    private static volatile boolean installed;
    private static volatile boolean enabled;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Context applicationContext;
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static WeakReference<View> activeViewer = new WeakReference<>(null);
    private static XhsViewerTapPolicy tapPolicy;
    private static boolean viewerReportedVisible;
    private static boolean gestureStartedInViewer;
    private static long viewerEnteredAtMillis;
    private static Runnable pendingInspection;

    private XhsImageFullscreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(lpparam.processName)) return;
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Context)) return;
                initialize((Context) param.args[0]);
            }
        });
    }

    private static synchronized void initialize(Context context) {
        if (installed) return;
        installed = true;
        applicationContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        refreshEnabled(applicationContext.getContentResolver());
        observeSettings(applicationContext.getContentResolver());
        hookActivityResume();
        hookActivityCleanup();
        hookViewerExitInputs();
        XposedBridge.log(TAG + ": installed (tap-triggered scan)");
    }

    private static void hookActivityResume() {
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isTargetActivity(activity)) return;
                Activity previous = resumedActivity.get();
                if (previous != null && previous != activity) resetViewerState(true);
                resumedActivity = new WeakReference<>(activity);
                tapPolicy = new XhsViewerTapPolicy(
                        ViewConfiguration.get(activity).getScaledTouchSlop(),
                        ViewConfiguration.getLongPressTimeout());
            }
        });
    }

    private static void hookActivityCleanup() {
        XC_MethodHook cleanup = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (isTargetActivity(activity) && activity == resumedActivity.get()) {
                    resetViewerState(true);
                }
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "onPause", cleanup);
        XposedBridge.hookAllMethods(Activity.class, "onDestroy", cleanup);
    }

    private static void hookViewerExitInputs() {
        XposedBridge.hookAllMethods(Activity.class, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!enabled || activity != resumedActivity.get()
                        || !(param.args[0] instanceof MotionEvent)) {
                    return;
                }
                MotionEvent event = (MotionEvent) param.args[0];
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    gestureStartedInViewer = viewerReportedVisible;
                    if (!viewerReportedVisible) {
                        cancelPendingInspection();
                        ensureTapPolicy(activity).onDown(
                                event.getRawX(), event.getRawY(), event.getEventTime());
                    }
                } else if (!viewerReportedVisible && (action == MotionEvent.ACTION_MOVE
                        || action == MotionEvent.ACTION_POINTER_DOWN
                        || action == MotionEvent.ACTION_POINTER_UP)) {
                    ensureTapPolicy(activity).onMove(
                            event.getRawX(), event.getRawY(), event.getPointerCount());
                } else if (!viewerReportedVisible && action == MotionEvent.ACTION_CANCEL) {
                    ensureTapPolicy(activity).onCancel();
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!enabled || activity != resumedActivity.get()
                        || !(param.args[0] instanceof MotionEvent)) {
                    return;
                }
                MotionEvent event = (MotionEvent) param.args[0];
                int action = event.getActionMasked();
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                        && gestureStartedInViewer) {
                    gestureStartedInViewer = false;
                    scheduleExitCheck(activity);
                } else if (!viewerReportedVisible && action == MotionEvent.ACTION_UP
                        && ensureTapPolicy(activity).onUp(
                        event.getRawX(), event.getRawY(), event.getEventTime())) {
                    scheduleEnterCheck(activity);
                }
            }
        });
        XposedBridge.hookAllMethods(Activity.class, "onBackPressed", new XC_MethodHook() {
            private boolean checkAfterBack;

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                checkAfterBack = isTargetActivity((Activity) param.thisObject)
                        && viewerReportedVisible;
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!checkAfterBack) return;
                Activity activity = (Activity) param.thisObject;
                scheduleExitCheck(activity);
            }
        });
    }

    private static XhsViewerTapPolicy ensureTapPolicy(Activity activity) {
        if (tapPolicy == null) {
            tapPolicy = new XhsViewerTapPolicy(
                    ViewConfiguration.get(activity).getScaledTouchSlop(),
                    ViewConfiguration.getLongPressTimeout());
        }
        return tapPolicy;
    }

    private static void inspectTree(Activity activity) {
        if (!enabled || activity != resumedActivity.get() || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        View candidate = scanTree(decor, decor.getHeight(), activity.getClass().getName()).candidate;
        if (candidate == null) return;
        observeViewer(candidate);
    }

    private static ScanResult scanTree(View view, int rootHeight, String activityClassName) {
        String entryName = resourceName(view);
        boolean hasPhotoLayout = "photoImageViewLayout".equals(entryName);
        boolean hasMediaContainer = "mediaContainer".equals(entryName);
        View candidate = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                ScanResult child = scanTree(
                        group.getChildAt(index), rootHeight, activityClassName);
                hasPhotoLayout |= child.hasPhotoLayout;
                hasMediaContainer |= child.hasMediaContainer;
                if (candidate == null) candidate = child.candidate;
            }
        }
        if (candidate == null && XhsImageFullscreenPolicy.isViewerCandidate(
                activityClassName, isRecyclerView(view), hasPhotoLayout, hasMediaContainer,
                rootHeight, view.getHeight())) {
            candidate = view;
        }
        return new ScanResult(hasPhotoLayout, hasMediaContainer, candidate);
    }

    private static boolean isRecyclerView(View view) {
        Class<?> type = view.getClass();
        while (type != null) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized void observeViewer(View viewer) {
        if (activeViewer.get() == viewer && viewerReportedVisible) return;
        activeViewer = new WeakReference<>(viewer);
        if (!viewerReportedVisible) {
            viewerReportedVisible = true;
            viewerEnteredAtMillis = SystemClock.uptimeMillis();
            sendState(true);
        }
        viewer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                synchronized (XhsImageFullscreenHook.class) {
                    if (activeViewer.get() != view) return;
                    activeViewer = new WeakReference<>(null);
                    Activity activity = resumedActivity.get();
                    if (viewerReportedVisible && activity != null) scheduleExitCheck(activity);
                }
            }
        });
    }

    private static void confirmUserDrivenExit(Activity activity) {
        if (!viewerReportedVisible || activity != resumedActivity.get()
                || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        View candidate = scanTree(
                decor, decor.getHeight(), activity.getClass().getName()).candidate;
        if (candidate != null) {
            observeViewer(candidate);
            return;
        }
        viewerReportedVisible = false;
        activeViewer = new WeakReference<>(null);
        sendState(false);
    }

    private static void scheduleEnterCheck(Activity activity) {
        scheduleInspection(activity, XhsImageFullscreenPolicy.VIEWER_SETTLE_DELAY_MS,
                () -> inspectTree(activity));
    }

    private static void scheduleExitCheck(Activity activity) {
        long stableAt = viewerEnteredAtMillis + 1_500L;
        long delay = Math.max(500L, stableAt - SystemClock.uptimeMillis());
        scheduleInspection(activity, delay, () -> confirmUserDrivenExit(activity));
    }

    private static synchronized void scheduleInspection(
            Activity activity, long delayMillis, Runnable action) {
        cancelPendingInspection();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                synchronized (XhsImageFullscreenHook.class) {
                    if (pendingInspection != this) return;
                    pendingInspection = null;
                }
                if (activity == resumedActivity.get()) action.run();
            }
        };
        pendingInspection = task;
        MAIN_HANDLER.postDelayed(task, delayMillis);
    }

    private static synchronized void cancelPendingInspection() {
        if (pendingInspection == null) return;
        MAIN_HANDLER.removeCallbacks(pendingInspection);
        pendingInspection = null;
    }

    private static synchronized void resetViewerState(boolean notifySystem) {
        cancelPendingInspection();
        tapPolicy = null;
        gestureStartedInViewer = false;
        activeViewer = new WeakReference<>(null);
        boolean wasVisible = viewerReportedVisible;
        viewerReportedVisible = false;
        resumedActivity = new WeakReference<>(null);
        if (notifySystem && wasVisible) sendState(false);
    }

    private static void observeSettings(ContentResolver resolver) {
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean wasEnabled = enabled;
                refreshEnabled(resolver);
                if (wasEnabled && !enabled) {
                    resetViewerState(true);
                }
            }
        };
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN), false, observer);
        resolver.registerContentObserver(
                Settings.Global.getUriFor(KEY_ENABLE_XHS_IMAGE_FULLSCREEN), false, observer);
    }

    private static void refreshEnabled(ContentResolver resolver) {
        enabled = XhsImageFullscreenPolicy.isEnabled(
                Settings.Global.getString(resolver, KEY_ENABLE_SPLIT_IMAGE_FULLSCREEN),
                Settings.Global.getString(resolver, KEY_ENABLE_XHS_IMAGE_FULLSCREEN));
    }

    private static boolean isTargetActivity(Activity activity) {
        return activity != null && activity.getClass().getName()
                .endsWith(XhsImageFullscreenPolicy.TARGET_ACTIVITY_SUFFIX);
    }

    private static void sendState(boolean visible) {
        Context context = applicationContext;
        if (context == null) return;
        Intent intent = new Intent(XhsImageFullscreenContract.ACTION_VIEWER_STATE);
        intent.putExtra(XhsImageFullscreenContract.EXTRA_VISIBLE, visible);
        context.sendBroadcast(intent);
        XposedBridge.log(TAG + ": viewer " + (visible ? "entered" : "exited"));
    }

    private static final class ScanResult {
        final boolean hasPhotoLayout;
        final boolean hasMediaContainer;
        final View candidate;

        ScanResult(boolean hasPhotoLayout, boolean hasMediaContainer, View candidate) {
            this.hasPhotoLayout = hasPhotoLayout;
            this.hasMediaContainer = hasMediaContainer;
            this.candidate = candidate;
        }
    }
}
