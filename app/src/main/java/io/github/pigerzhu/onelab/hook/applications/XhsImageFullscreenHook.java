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
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.XhsImageFullscreenContract;

/** Detects XHS's native full-height image viewer and reports its lifecycle to system_server. */
public final class XhsImageFullscreenHook {
    private static final String TAG = "OneLab/XhsImageFullscreen";
    private static final Set<View> OBSERVED_VIEWERS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static volatile boolean installed;
    private static volatile boolean enabled;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Context applicationContext;
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);
    private static WeakReference<View> activeViewer = new WeakReference<>(null);
    private static boolean viewerReportedVisible;
    private static boolean gestureStartedInViewer;
    private static long viewerEnteredAtMillis;
    private static int pendingViewerGeneration;

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
        hookViewAdditions();
        hookViewerExitInputs();
        XposedBridge.log(TAG + ": installed");
    }

    private static void hookActivityResume() {
        XposedBridge.hookAllMethods(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isTargetActivity(activity)) return;
                resumedActivity = new WeakReference<>(activity);
                if (enabled) activity.getWindow().getDecorView().post(
                        () -> inspectTree(activity, activity.getWindow().getDecorView()));
            }
        });
    }

    private static void hookViewAdditions() {
        XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!enabled || param.args.length == 0 || !(param.args[0] instanceof View)) {
                    return;
                }
                Activity activity = resumedActivity.get();
                if (!isTargetActivity(activity)) return;
                View added = (View) param.args[0];
                added.post(() -> inspectTree(activity, added));
            }
        });
    }

    private static void hookViewerExitInputs() {
        XposedBridge.hookAllMethods(Activity.class, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isTargetActivity(activity) || !(param.args[0] instanceof MotionEvent)) {
                    return;
                }
                int action = ((MotionEvent) param.args[0]).getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    gestureStartedInViewer = viewerReportedVisible;
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (!isTargetActivity(activity) || !(param.args[0] instanceof MotionEvent)) {
                    return;
                }
                int action = ((MotionEvent) param.args[0]).getActionMasked();
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                        && gestureStartedInViewer) {
                    gestureStartedInViewer = false;
                    scheduleExitCheck(activity);
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

    private static void inspectTree(Activity activity, View changedView) {
        if (!enabled || activity != resumedActivity.get() || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        View candidate = findCandidate(changedView, decor);
        if (candidate == null) return;
        observeViewer(candidate);
    }

    private static View findCandidate(View view, View decor) {
        View ancestor = view;
        while (ancestor != null) {
            if (isCandidate(ancestor, decor)) return ancestor;
            if (!(ancestor.getParent() instanceof View)) break;
            ancestor = (View) ancestor.getParent();
        }
        return findCandidateBelow(view, decor);
    }

    private static View findCandidateBelow(View view, View decor) {
        if (isCandidate(view, decor)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View candidate = findCandidateBelow(group.getChildAt(index), decor);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static boolean isCandidate(View view, View decor) {
        return XhsImageFullscreenPolicy.isViewerCandidate(
                resumedActivity.get() == null ? null : resumedActivity.get().getClass().getName(),
                isRecyclerView(view),
                containsResource(view, "photoImageViewLayout"),
                containsResource(view, "mediaContainer"),
                decor.getHeight(),
                view.getHeight());
    }

    private static boolean isRecyclerView(View view) {
        Class<?> type = view.getClass();
        while (type != null) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean containsResource(View view, String entryName) {
        if (entryName.equals(resourceName(view))) return true;
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (containsResource(group.getChildAt(index), entryName)) return true;
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
        if (!OBSERVED_VIEWERS.add(viewer)) return;
        activeViewer = new WeakReference<>(viewer);
        if (!viewerReportedVisible) {
            int generation = ++pendingViewerGeneration;
            MAIN_HANDLER.postDelayed(
                    () -> confirmViewerSettled(viewer, generation),
                    XhsImageFullscreenPolicy.VIEWER_SETTLE_DELAY_MS);
        }
        viewer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                synchronized (XhsImageFullscreenHook.class) {
                    OBSERVED_VIEWERS.remove(view);
                    if (activeViewer.get() != view) return;
                    activeViewer = new WeakReference<>(null);
                    if (!viewerReportedVisible) pendingViewerGeneration++;
                }
            }
        });
    }

    private static synchronized void confirmViewerSettled(View viewer, int generation) {
        if (!enabled || viewerReportedVisible || pendingViewerGeneration != generation
                || activeViewer.get() != viewer || !viewer.isAttachedToWindow()) return;
        Activity activity = resumedActivity.get();
        if (!isTargetActivity(activity) || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        if (findCandidate(decor, decor) == null) return;
        viewerReportedVisible = true;
        viewerEnteredAtMillis = SystemClock.uptimeMillis();
        sendState(true);
    }

    private static void confirmUserDrivenExit(Activity activity) {
        if (!viewerReportedVisible || activity != resumedActivity.get()
                || activity.isFinishing()) return;
        View decor = activity.getWindow().getDecorView();
        View candidate = findCandidate(decor, decor);
        if (candidate != null) {
            observeViewer(candidate);
            return;
        }
        viewerReportedVisible = false;
        activeViewer = new WeakReference<>(null);
        sendState(false);
    }

    private static void scheduleExitCheck(Activity activity) {
        long stableAt = viewerEnteredAtMillis + 1_500L;
        long delay = Math.max(500L, stableAt - SystemClock.uptimeMillis());
        MAIN_HANDLER.postDelayed(() -> confirmUserDrivenExit(activity), delay);
    }

    private static void observeSettings(ContentResolver resolver) {
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                boolean wasEnabled = enabled;
                refreshEnabled(resolver);
                if (wasEnabled && !enabled) {
                    activeViewer = new WeakReference<>(null);
                    viewerReportedVisible = false;
                    pendingViewerGeneration++;
                    sendState(false);
                } else if (!wasEnabled && enabled) {
                    Activity activity = resumedActivity.get();
                    if (isTargetActivity(activity)) {
                        activity.getWindow().getDecorView().post(
                                () -> inspectTree(activity, activity.getWindow().getDecorView()));
                    }
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
}
