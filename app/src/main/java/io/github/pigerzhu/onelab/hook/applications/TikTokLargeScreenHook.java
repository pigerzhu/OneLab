package io.github.pigerzhu.onelab.hook.applications;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;

/** Enables TikTok's existing comments side panel while observing other native tablet pages. */
public final class TikTokLargeScreenHook {
    private static final String TAG = "OneLab/TikTokLargeScreen";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private TikTokLargeScreenHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TikTokLargeScreenPolicy.isMainProcess(lpparam.packageName, lpparam.processName)) return;
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Context) || !STARTED.compareAndSet(false, true)) return;
                Context context = (Context) param.args[0];
                AtomicBoolean enabled = new AtomicBoolean(isEnabled(context));
                AtomicBoolean liveEnabled = new AtomicBoolean(isLiveEnabled(context));
                AtomicBoolean portraitEnabled = new AtomicBoolean(isPortraitExperimentEnabled(context));
                observeEnabled(context, enabled);
                observeLiveEnabled(context, liveEnabled);
                observePortraitEnabled(context, portraitEnabled);
                try {
                    TikTokLargeScreenTargets targets = TikTokLargeScreenLocator.find(
                            context.getApplicationInfo().sourceDir, context.getClassLoader());
                    XposedBridge.hookMethod(targets.methods.get(TikTokLargeScreenPolicy.COMMENTS_GATE),
                            forceResult(enabled));
                    hookExactSettingsOverride(context.getClassLoader(), enabled);
                    hookPortraitCommentGate(
                            targets.methods.get(TikTokLargeScreenPolicy.PORTRAIT_COMMENT_GATE),
                            enabled, portraitEnabled);
                    hookPortraitPanelWidth(
                            targets.methods.get(TikTokLargeScreenPolicy.COMMENT_PANEL_WIDTH),
                            context, enabled, portraitEnabled);
                    hookLiveMultiScreen(context.getClassLoader(), liveEnabled);
                    observeNativePages(context.getClassLoader());
                    XposedBridge.log(TAG + ": installed comments gate; native page probes enabled");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": semantic location failed open: " + t);
                }
            }
        });
    }

    private static XC_MethodHook forceResult(AtomicBoolean enabled) {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (TikTokLargeScreenPolicy.shouldForceCommentGate(enabled.get(),
                        TikTokLargeScreenPolicy.COMMENTS_GATE)) param.setResult(Boolean.TRUE);
            }
        };
    }

    private static void hookExactSettingsOverride(ClassLoader loader, AtomicBoolean enabled) {
        Class<?> settings = XposedHelpers.findClassIfExists(
                "com.bytedance.ies.abmock.SettingsManager", loader);
        if (settings == null) return;
        List<Method> candidates = new ArrayList<>();
        for (Method method : settings.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == boolean.class
                    && parameters.length == 2
                    && parameters[0] == String.class
                    && parameters[1] == boolean.class) {
                candidates.add(method);
            }
        }
        Method target = TikTokLargeScreenTargets.requireUnique(
                TikTokLargeScreenPolicy.FOLDABLE_OVERRIDE, candidates);
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (TikTokLargeScreenPolicy.shouldForceCommentGate(enabled.get(),
                        String.valueOf(param.args[0]))) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    private static void observeNativePages(ClassLoader loader) {
        hookConstructors(loader, "com.ss.android.ugc.aweme.inbox.tablet.TabletInboxViewModel", "Inbox");
        hookConstructors(loader, "com.bytedance.android.livesdk.pad.PadLiveRoomMultiScreenFragment", "Live");
        hookConstructors(loader,
                "com.ss.android.ugc.aweme.search.pages.core.ui.fragment.SearchContainerFragment",
                "Search");
    }

    private static void hookLiveMultiScreen(ClassLoader loader, AtomicBoolean enabled) {
        Class<?> setting = XposedHelpers.findClassIfExists(
                TikTokLargeScreenPolicy.LIVE_MULTI_SCREEN_CLASS, loader);
        if (setting == null) return;
        XposedHelpers.findAndHookMethod(setting, "getValue", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (TikTokLargeScreenPolicy.shouldForceLiveMultiScreen(enabled.get())) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    private static void hookPortraitCommentGate(Method target,
            AtomicBoolean commentsEnabled, AtomicBoolean portraitEnabled) {
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Activity)) return;
                Activity activity = (Activity) param.args[0];
                Configuration configuration = param.args[1] instanceof Configuration
                        ? (Configuration) param.args[1] : activity.getResources().getConfiguration();
                if (TikTokLargeScreenPolicy.shouldForcePortraitComments(
                        commentsEnabled.get(), portraitEnabled.get(),
                        configuration.screenWidthDp, configuration.screenHeightDp,
                        activity.isInMultiWindowMode(), activity.isInPictureInPictureMode())) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    private static void hookPortraitPanelWidth(Method target, Context context,
            AtomicBoolean commentsEnabled, AtomicBoolean portraitEnabled) {
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Configuration configuration = context.getResources().getConfiguration();
                int originalWidthPx = (Integer) param.getResult();
                int widthPx = context.getResources().getDisplayMetrics().widthPixels;
                int resolvedWidthPx = TikTokLargeScreenPolicy.resolveCommentPanelWidthPx(
                        commentsEnabled.get(), portraitEnabled.get(),
                        configuration.screenWidthDp, configuration.screenHeightDp,
                        widthPx, originalWidthPx);
                if (resolvedWidthPx != originalWidthPx) {
                    param.setResult(resolvedWidthPx);
                }
            }
        });
    }

    private static void hookConstructors(ClassLoader loader, String className, String page) {
        Class<?> type = XposedHelpers.findClassIfExists(className, loader);
        if (type == null) return;
        XposedBridge.hookAllConstructors(type, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log(TAG + ": native " + page + " large-screen component observed; unchanged");
            }
        });
    }

    private static boolean isEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS, 0) != 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isLiveEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN, 0) != 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isPortraitExperimentEnabled(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN, 0) != 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void observeEnabled(Context context, AtomicBoolean enabled) {
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS),
                false, new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) {
                        enabled.set(isEnabled(context));
                    }
                });
    }

    private static void observeLiveEnabled(Context context, AtomicBoolean enabled) {
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN),
                false, new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) {
                        enabled.set(isLiveEnabled(context));
                    }
                });
    }

    private static void observePortraitEnabled(Context context, AtomicBoolean enabled) {
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN),
                false, new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) {
                        enabled.set(isPortraitExperimentEnabled(context));
                    }
                });
    }
}
