package io.github.pigerzhu.onelab.hook.applications;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;

/**
 * Applies OneLab's configured split ratio to Feishu's own Forseti two-pane
 * container. Feishu renders both panes inside one Activity, so AndroidX
 * Activity Embedding hooks do not participate in this layout.
 */
public final class LarkSplitRatioHook {
    private static final String TAG = "OneLab/LarkSplitRatio";
    private static final String NORMAL_CHAIN_VIEW_GROUP =
            "com.ss.android.lark.forseti.widget.ForsetiNormalChainViewGroup";
    private static final String FORSETI_WIDTH =
            "com.ss.android.lark.api.bean.ForsetiWidth";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FAILURE = new AtomicBoolean();

    private static volatile Float ratio;
    private static volatile boolean observerRegistered;

    private LarkSplitRatioHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = (Context) param.args[0];
                        initialize(context.getContentResolver(), lpparam.packageName);
                        installForClassLoader(context.getClassLoader());
                    }
                });
    }

    private static void installForClassLoader(ClassLoader classLoader) {
        if (classLoader == null || !INSTALLED.compareAndSet(false, true)) return;
        Class<?> group = XposedHelpers.findClassIfExists(NORMAL_CHAIN_VIEW_GROUP, classLoader);
        Class<?> width = XposedHelpers.findClassIfExists(FORSETI_WIDTH, classLoader);
        LarkPaneMeasureLocator.Targets targets = group == null || width == null
                ? null
                : LarkPaneMeasureLocator.findUnique(group, width);
        if (targets == null) {
            INSTALLED.set(false);
            XposedBridge.log(TAG + ": unique Forseti pane measure methods unavailable");
            return;
        }

        XposedBridge.hookMethod(targets.left, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Float current = ratio;
                if (current == null
                        || param.args.length != 3
                        || !(param.args[0] instanceof Integer)
                        || !(param.args[2] instanceof Boolean)
                        || !((Boolean) param.args[2])) {
                    return;
                }
                int totalWidth = (Integer) param.args[0];
                if (totalWidth > 0) {
                    param.setResult(exactMeasureSpec(
                            LarkPaneWidthPolicy.width(totalWidth, current, true)));
                }
            }
        });
        XposedBridge.hookMethod(targets.right, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Float current = ratio;
                if (current == null
                        || param.args.length != 2
                        || !(param.args[0] instanceof Integer)
                        || param.args[1] == null) {
                    return;
                }
                String widthMode = String.valueOf(param.args[1]);
                if (!"AVERAGE_LEVEL".equals(widthMode)
                        && !widthMode.endsWith("_RIGHT_SIDE")) {
                    return;
                }
                int totalWidth = (Integer) param.args[0];
                if (totalWidth > 0) {
                    param.setResult(exactMeasureSpec(
                            LarkPaneWidthPolicy.width(totalWidth, current, false)));
                }
            }
        });
        XposedBridge.log(TAG + ": Forseti pane ratio hooks installed by descriptor");
    }

    private static int exactMeasureSpec(int width) {
        return View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
    }

    private static void initialize(ContentResolver resolver, String packageName) {
        refresh(resolver, packageName);
        if (observerRegistered) return;
        synchronized (LarkSplitRatioHook.class) {
            if (observerRegistered) return;
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(KEY_SPLIT_VIEW_RATIO_OVERRIDES),
                    false,
                    new ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            refresh(resolver, packageName);
                        }
                    });
            observerRegistered = true;
        }
    }

    private static void refresh(ContentResolver resolver, String packageName) {
        try {
            Map<String, Float> values = SplitViewRatioOverrides.parse(
                    Settings.Global.getString(resolver, KEY_SPLIT_VIEW_RATIO_OVERRIDES));
            ratio = values.get(packageName);
        } catch (Throwable throwable) {
            if (LOGGED_FAILURE.compareAndSet(false, true)) {
                XposedBridge.log(TAG + ": ratio setting update failed");
                XposedBridge.log(throwable);
            }
        }
    }
}
