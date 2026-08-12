package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.pigerzhu.onelab.contract.SettingsKeys;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class GalleryLabsHook {
    private static final String GALLERY_POC_FEATURES =
            "com.samsung.android.gallery.support.utils.PocFeatures";
    private static final String LABS_BASE_FRAGMENT =
            "com.samsung.android.gallery.settings.ui.LabsBaseFragment";
    private static final String ANDROIDX_PREFERENCE = "androidx.preference.Preference";
    private static final AtomicBoolean TRANSLATION_INSTALL_STARTED = new AtomicBoolean();

    private GalleryLabsHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installChineseTranslations(lpparam);
        try {
            Class<?> pocFeaturesClass = XposedHelpers.findClass(GALLERY_POC_FEATURES, lpparam.classLoader);
            XposedBridge.hookAllMethods(pocFeaturesClass, "isEnabled", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length != 1 || param.args[0] == null) {
                        return;
                    }
                    ContentResolver resolver = resolverFromFeature(param.args[0]);
                    if (!HookUtils.globalEnabled(
                            resolver, SettingsKeys.KEY_ENABLE_GALLERY_DEV_LABS, 0)) {
                        return;
                    }
                    String name = String.valueOf(param.args[0]);
                    if ("GalleryLabs".equals(name) || "GalleryLabsDev".equals(name)) {
                        param.setResult(Boolean.TRUE);
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked Gallery PocFeatures.isEnabled");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": gallery labs hook failed");
            XposedBridge.log(t);
        }
    }

    private static void installChineseTranslations(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.hookAllMethods(Application.class, "attach", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args == null
                        || param.args.length != 1
                        || !(param.args[0] instanceof Context)
                        || !TRANSLATION_INSTALL_STARTED.compareAndSet(false, true)) {
                    return;
                }
                Context context = (Context) param.args[0];
                AtomicBoolean enabled = new AtomicBoolean(translationEnabled(context));
                observeTranslationSetting(context, enabled);
                installResourceTranslations(context, enabled);
                installPreferenceTextTranslations(lpparam.classLoader, enabled);
                installLiteralTranslations(lpparam.classLoader, enabled);
            }
        });
    }

    private static void installResourceTranslations(Context context, AtomicBoolean enabled) {
        Resources resources = context.getResources();
        Map<Integer, String> translations = new HashMap<>();
        GalleryLabsChineseTranslations.resourceTranslations().forEach((name, translation) -> {
            int id = resources.getIdentifier(name, "string", context.getPackageName());
            if (id != 0) {
                translations.put(id, translation);
            }
        });
        XposedHelpers.findAndHookMethod(Resources.class, "getText", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!enabled.get() || param.args == null || param.args.length != 1) {
                            return;
                        }
                        String translation = translations.get((Integer) param.args[0]);
                        if (translation != null) {
                            param.setResult(translation);
                        }
                    }
                });
        Log.i(HookConstants.TAG, "Gallery Labs Chinese resources=" + translations.size());
    }

    private static void installLiteralTranslations(
            ClassLoader classLoader, AtomicBoolean enabled) {
        try {
            Class<?> fragment = XposedHelpers.findClass(LABS_BASE_FRAGMENT, classLoader);
            XposedHelpers.findAndHookMethod(fragment, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!enabled.get()) {
                                return;
                            }
                            translatePreference(XposedHelpers.callMethod(
                                    param.thisObject, "getPreferenceScreen"));
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(HookConstants.TAG + ": Gallery Labs literal translation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void installPreferenceTextTranslations(
            ClassLoader classLoader, AtomicBoolean enabled) {
        try {
            Class<?> preference = XposedHelpers.findClass(ANDROIDX_PREFERENCE, classLoader);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled.get()
                            || param.args == null
                            || param.args.length != 1
                            || param.args[0] == null
                            || !isLabsPreference(param.thisObject)) {
                        return;
                    }
                    String translation = GalleryLabsChineseTranslations.literalTranslation(
                            param.args[0].toString());
                    if (translation != null) {
                        param.args[0] = translation;
                    }
                }
            };
            XposedHelpers.findAndHookMethod(
                    preference, "setTitle", CharSequence.class, hook);
            XposedHelpers.findAndHookMethod(
                    preference, "setSummary", CharSequence.class, hook);
        } catch (Throwable throwable) {
            XposedBridge.log(HookConstants.TAG
                    + ": Gallery Labs Preference text translation failed");
            XposedBridge.log(throwable);
        }
    }

    private static boolean isLabsPreference(Object preference) {
        Object current = preference;
        try {
            while (current != null) {
                Object key = XposedHelpers.callMethod(current, "getKey");
                if ("labs_preference_screen".equals(key)) {
                    return true;
                }
                current = XposedHelpers.callMethod(current, "getParent");
            }
        } catch (Throwable ignored) {
            // A detached or older Preference implementation is handled by the onCreate pass.
        }
        return false;
    }

    private static void translatePreference(Object preference) {
        if (preference == null) {
            return;
        }
        try {
            replacePreferenceText(preference, "getTitle", "setTitle");
            replacePreferenceText(preference, "getSummary", "setSummary");
            Object count = XposedHelpers.callMethod(preference, "getPreferenceCount");
            if (!(count instanceof Integer)) {
                return;
            }
            for (int i = 0; i < (Integer) count; i++) {
                translatePreference(XposedHelpers.callMethod(preference, "getPreference", i));
            }
        } catch (Throwable ignored) {
            // Ordinary Preference leaves are not PreferenceGroups.
        }
    }

    private static void replacePreferenceText(
            Object preference, String getter, String setter) {
        Object original = XposedHelpers.callMethod(preference, getter);
        if (original == null) {
            return;
        }
        String translation = GalleryLabsChineseTranslations.literalTranslation(
                original.toString());
        if (translation != null) {
            XposedHelpers.callMethod(preference, setter, translation);
        }
    }

    private static boolean translationEnabled(Context context) {
        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    SettingsKeys.KEY_ENABLE_GALLERY_LABS_ZH_CN,
                    0) == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void observeTranslationSetting(Context context, AtomicBoolean enabled) {
        ContentResolver resolver = context.getContentResolver();
        resolver.registerContentObserver(
                Settings.Global.getUriFor(SettingsKeys.KEY_ENABLE_GALLERY_LABS_ZH_CN),
                false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        enabled.set(translationEnabled(context));
                    }
                });
    }

    private static ContentResolver resolverFromFeature(Object feature) {
        Object appContext = HookUtils.invokeStaticNoArg(
                "com.samsung.android.gallery.support.utils.AppResources",
                "getAppContext",
                feature.getClass().getClassLoader()
        );
        return HookUtils.resolverFromContextObject(appContext);
    }
}
