package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;

/**
 * Enables XHS's existing Pad video-detail route inside the XHS process.
 */
public final class XhsFoldVideoHook {
    private static final String TAG = "OneLab/XhsFoldVideo";

    private static final String AB_TEST_HELPER =
            "com.xingin.detailfeed.abtest.DetailFeedAbTestHelper";
    private static final String INTENT_DATA =
            "com.xingin.matrix.detail.intent.DetailFeedIntentData";
    private static final String DEVICE_INFO_CONTAINER =
            "com.xingin.adaptation.device.DeviceInfoContainer";
    private static final String PAD_VIDEO_PROXY =
            "com.xingyin.pad.videofeed.spi.PadNewVideoProxyImpl";
    private static final String PAD_VIDEO_CONTAINER =
            "com.xingyin.pad.videofeed.containerv2.PadNewContainerPresenter";
    private static final String PAD_COMMENT_DIALOG =
            "com.xingin.matrix.comment.dialog.VideoCommentLandscapeDialog";
    private static final String PAD_COMMENT_RESIZE =
            "com.xingyin.pad.videofeed.page.comment.PadCommentPanelResizeVideoPresenter";

    private static final String[] VIDEO_FRAME_FLAGS = {
            "enableNewVideoFeedFrame",
            "padVideoPlayNewFramework",
            "padVideoIsNewVideoFrame",
            "padVideoNewFrameStyleAdjust",
            "padVideoCommentTextOptCombo"
    };
    private static final String[] VIDEO_ROUTE_TRUE = {
            "isNewVideoFeedFrame",
            "y1"
    };
    private static final String VIDEO_ROUTE_FALSE = "isOldVideoFeedStyle";
    private static final String VIDEO_BUSINESS_TYPE = "isVideoFeedBusinessType";

    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Object INSTALL_LOCK = new Object();
    private static final AtomicBoolean LOGGED_HOME_ACTIVE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_VIDEO_ACTIVE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PAD_PROXY = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_PAD_CONTAINER = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENT_DIALOG = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENT_RESIZE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENT_ROUTE_GATE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENT_FACTORY = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_COMMENT_SCREEN_GATE = new AtomicBoolean();

    private XhsFoldVideoHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length < 1
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                Context context = (Context) param.args[0];
                installForClassLoader(context, context.getClassLoader());
            }
        });
    }

    private static void installForClassLoader(Context context, ClassLoader classLoader) {
        if (classLoader == null) return;
        synchronized (INSTALL_LOCK) {
            if (!INSTALLED_LOADERS.add(classLoader)) return;
        }

        try {
            AtomicBoolean homeEnabled = new AtomicBoolean(isHomeEnabled(context));
            AtomicBoolean videoEnabled = new AtomicBoolean(isVideoEnabled(context));
            observeEnabledSettings(context, homeEnabled, videoEnabled);
            FoldGate gate = new FoldGate(context, homeEnabled, videoEnabled);
            int hooks = 0;
            hooks += hookHorizontalFoldDeviceFlag(classLoader, gate);
            hooks += hookVideoFrameFlags(classLoader, gate);
            hooks += hookVideoIntentRoutes(classLoader, gate);
            hooks += hookPadDeviceFlag(classLoader, gate);
            hooks += hookCommentLayoutCompatibility(
                    context.getApplicationInfo().sourceDir, classLoader, gate);
            hooks += hookStablePadLifecycle(classLoader, gate);
            Log.i(TAG, "Installed " + hooks + " stable video hooks");
        } catch (Throwable throwable) {
            synchronized (INSTALL_LOCK) {
                INSTALLED_LOADERS.remove(classLoader);
            }
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static int hookVideoFrameFlags(ClassLoader classLoader, FoldGate gate) {
        int hooks = 0;
        for (String methodName : VIDEO_FRAME_FLAGS) {
            hooks += hookAfter(classLoader, AB_TEST_HELPER, methodName, param -> {
                if (gate.isEligible()) gate.setTrue(param);
            });
        }
        return hooks;
    }

    private static int hookVideoIntentRoutes(ClassLoader classLoader, FoldGate gate) {
        int hooks = 0;
        for (String methodName : VIDEO_ROUTE_TRUE) {
            hooks += hookAfter(classLoader, INTENT_DATA, methodName, param -> {
                if (gate.isVideoDetail(param.thisObject)) gate.setTrue(param);
            });
        }
        hooks += hookAfter(classLoader, INTENT_DATA, VIDEO_ROUTE_FALSE, param -> {
            if (gate.isVideoDetail(param.thisObject)) param.setResult(false);
        });
        return hooks;
    }

    private static int hookHorizontalFoldDeviceFlag(ClassLoader classLoader, FoldGate gate) {
        return hookAfter(classLoader, DEVICE_INFO_CONTAINER, "isHorizontalFolderDevice", param -> {
            if (gate.isHomeEnabled()) gate.setHomeTrue(param);
        });
    }

    private static int hookPadDeviceFlag(ClassLoader classLoader, FoldGate gate) {
        return hookAfter(classLoader, DEVICE_INFO_CONTAINER, "isPad", param -> {
            if (gate.isEligible()) gate.setTrue(param);
        });
    }

    private static int hookStablePadLifecycle(ClassLoader classLoader, FoldGate gate) {
        int hooks = hookAfter(classLoader, PAD_VIDEO_PROXY,
                "getPadNewDetailFeedContainer", param -> {
                    if (gate.isEligible()) logStage(LOGGED_PAD_PROXY, "Pad video SPI invoked");
                });
        hooks += hookConstructors(classLoader, PAD_VIDEO_CONTAINER, gate,
                LOGGED_PAD_CONTAINER, "Pad video container created");
        hooks += hookConstructors(classLoader, PAD_COMMENT_DIALOG, gate,
                LOGGED_COMMENT_DIALOG, "Landscape comment dialog created");
        hooks += hookConstructors(classLoader, PAD_COMMENT_RESIZE, gate,
                LOGGED_COMMENT_RESIZE, "Comment resize presenter created");
        return hooks;
    }

    private static int hookCommentLayoutCompatibility(String apkPath, ClassLoader classLoader,
            FoldGate gate) {
        try {
            XhsCommentHookTargets targets = XhsCommentHookLocator.find(apkPath, classLoader);
            int hooks = 0;
            for (Method routeGate : targets.routeGates) {
                hooks += hookAfter(routeGate, param -> {
                    if (gate.isEligible()) {
                        gate.setTrue(param);
                        logStage(LOGGED_COMMENT_ROUTE_GATE, "Comment route gate matched");
                    }
                });
            }
            for (Method factory : targets.dialogFactories) {
                hooks += hookBefore(factory, param -> {
                    if (!gate.isEligible() || param.args == null) return;
                    int index = param.args.length == 15 ? 10 : 11;
                    if (index < param.args.length && param.args[index] instanceof Boolean) {
                        param.args[index] = true;
                        logStage(LOGGED_COMMENT_FACTORY, "Comment dialog factory matched");
                    }
                });
            }
            hooks += hookBefore(targets.screenGate, param -> {
                if (gate.isEligible() && param.args != null && param.args.length == 1
                        && param.args[0] instanceof Context) {
                    gate.setTrue(param);
                    logStage(LOGGED_COMMENT_SCREEN_GATE, "Comment screen gate matched");
                }
            });
            Log.i(TAG, "Located semantic comment hooks: routes=" + targets.routeGates.size()
                    + ", factories=" + targets.dialogFactories.size());
            return hooks;
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": semantic comment hooks unavailable");
            XposedBridge.log(throwable);
            return 0;
        }
    }

    private static int hookAfter(ClassLoader classLoader, String className, String methodName,
            HookAction action) {
        try {
            Class<?> type = classLoader.loadClass(className);
            XposedBridge.hookAllMethods(type, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        action.apply(param);
                    } catch (Throwable ignored) {
                    }
                }
            });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int hookAfter(Method method, HookAction action) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    action.apply(param);
                } catch (Throwable ignored) {
                }
            }
        });
        return 1;
    }

    private static int hookBefore(Method method, HookAction action) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    action.apply(param);
                } catch (Throwable ignored) {
                }
            }
        });
        return 1;
    }

    private static int hookConstructors(ClassLoader classLoader, String className, FoldGate gate,
            AtomicBoolean logged, String message) {
        try {
            Class<?> type = classLoader.loadClass(className);
            XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (gate.isEligible()) logStage(logged, message);
                }
            });
            return 1;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void logStage(AtomicBoolean logged, String message) {
        if (logged.compareAndSet(false, true)) Log.i(TAG, message);
    }

    private static void observeEnabledSettings(Context context, AtomicBoolean homeEnabled,
            AtomicBoolean videoEnabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_XHS_FOLD_HOME),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        homeEnabled.set(isHomeEnabled(context));
                    }
                });
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_XHS_FOLD_VIDEO),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        videoEnabled.set(isVideoEnabled(context));
                    }
                });
    }

    private static boolean isHomeEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(), SettingsKeys.KEY_ENABLE_XHS_FOLD_HOME, 0);
    }

    private static boolean isVideoEnabled(Context context) {
        return HookUtils.globalEnabled(
                context.getContentResolver(), SettingsKeys.KEY_ENABLE_XHS_FOLD_VIDEO, 0);
    }

    private interface HookAction {
        void apply(XC_MethodHook.MethodHookParam param);
    }

    private static final class FoldGate {
        private final Context context;
        private final AtomicBoolean homeEnabled;
        private final AtomicBoolean videoEnabled;
        private volatile Class<?> videoBusinessTypeOwner;
        private volatile Method videoBusinessTypeMethod;

        FoldGate(Context context, AtomicBoolean homeEnabled, AtomicBoolean videoEnabled) {
            this.context = context;
            this.homeEnabled = homeEnabled;
            this.videoEnabled = videoEnabled;
        }

        boolean isEligible() {
            Configuration configuration = context.getResources().getConfiguration();
            return XhsFoldLayoutPolicy.isVideoLayoutEligible(
                    videoEnabled.get(), configuration.smallestScreenWidthDp);
        }

        boolean isHomeEnabled() {
            return homeEnabled.get();
        }

        boolean isVideoEnabled() {
            return videoEnabled.get();
        }

        boolean isVideoDetail(Object detailIntentData) {
            if (!isEligible() || detailIntentData == null) return false;
            try {
                Method method = videoBusinessTypeMethod(detailIntentData.getClass());
                if (method == null) return false;
                return Boolean.TRUE.equals(method.invoke(detailIntentData));
            } catch (Throwable ignored) {
                return false;
            }
        }

        void setTrue(XC_MethodHook.MethodHookParam param) {
            param.setResult(true);
            logActive();
        }

        void setHomeTrue(XC_MethodHook.MethodHookParam param) {
            param.setResult(true);
            if (LOGGED_HOME_ACTIVE.compareAndSet(false, true)) {
                Log.i(TAG, "Fold home layout enabled");
            }
        }

        void logActive() {
            if (LOGGED_VIDEO_ACTIVE.compareAndSet(false, true)) {
                Log.i(TAG, "Pad video route enabled");
            }
        }

        private Method videoBusinessTypeMethod(Class<?> owner) {
            Method cached = videoBusinessTypeMethod;
            if (owner == videoBusinessTypeOwner && cached != null) return cached;
            synchronized (this) {
                if (owner == videoBusinessTypeOwner && videoBusinessTypeMethod != null) {
                    return videoBusinessTypeMethod;
                }
                try {
                    Method method = owner.getMethod(VIDEO_BUSINESS_TYPE);
                    method.setAccessible(true);
                    videoBusinessTypeOwner = owner;
                    videoBusinessTypeMethod = method;
                    return method;
                } catch (Throwable ignored) {
                    return null;
                }
            }
        }

    }
}
