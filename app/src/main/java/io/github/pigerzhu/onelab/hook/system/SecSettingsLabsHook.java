package io.github.pigerzhu.onelab.hook.system;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.widget.TextView;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.AbstractMap;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.hook.core.HookConstants;

/** Translates only the stable Connectivity Labs labels in Samsung Settings. */
public final class SecSettingsLabsHook {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SecSettingsLabsHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        XposedBridge.log(HookConstants.TAG + ": SecSettings Labs dispatcher installed");
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length != 1
                        || !(param.args[0] instanceof Context)) {
                    return;
                }
                XposedBridge.log(HookConstants.TAG + ": SecSettings Labs application attached");
                installResources((Context) param.args[0]);
            }
        });
    }

    private static void installResources(Context context) {
        ContentResolver resolver = context.getContentResolver();
        AtomicBoolean enabled = new AtomicBoolean(isEnabled(resolver));
        Map<TextView, CharSequence> originals = new WeakHashMap<>();
        try {
            // This overload also receives XML, resource-ID and single-argument text updates.
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                    CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            TextView view = (TextView) param.thisObject;
                            CharSequence original = (CharSequence) param.args[0];
                            String translation = original == null ? null
                                    : SecSettingsConnectivityLabsTranslations.translate(
                                            original.toString());
                            synchronized (originals) {
                                if (translation == null) {
                                    originals.remove(view);
                                } else {
                                    originals.put(view, original);
                                }
                            }
                            if (enabled.get() && translation != null) {
                                param.args[0] = translation;
                            }
                        }
                    });
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_CONNECTIVITY_LABS_ZH_CN),
                    false,
                    new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
                        @Override
                        public void onChange(boolean selfChange) {
                            enabled.set(isEnabled(resolver));
                            ArrayList<Map.Entry<TextView, CharSequence>> snapshot = new ArrayList<>();
                            synchronized (originals) {
                                for (Map.Entry<TextView, CharSequence> entry : originals.entrySet()) {
                                    snapshot.add(new AbstractMap.SimpleImmutableEntry<>(entry));
                                }
                            }
                            for (Map.Entry<TextView, CharSequence> entry : snapshot) {
                                TextView view = entry.getKey();
                                view.post(() -> {
                                    CharSequence original;
                                    synchronized (originals) {
                                        original = originals.get(view);
                                    }
                                    if (original != null) {
                                        view.setText(original);
                                    }
                                });
                            }
                            XposedBridge.log(HookConstants.TAG
                                    + ": SecSettings Labs enabled=" + enabled.get());
                        }
                    });
            XposedBridge.log(HookConstants.TAG + ": SecSettings Labs live translation installed; enabled="
                    + enabled.get());
        } catch (Throwable throwable) {
            XposedBridge.log(HookConstants.TAG + ": SecSettings Labs translation failed");
            XposedBridge.log(throwable);
        }
    }

    private static boolean isEnabled(ContentResolver resolver) {
        try {
            return Settings.Global.getInt(
                    resolver, SettingsKeys.KEY_ENABLE_CONNECTIVITY_LABS_ZH_CN, 0) == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
