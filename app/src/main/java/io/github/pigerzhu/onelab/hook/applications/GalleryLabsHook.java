package io.github.pigerzhu.onelab.hook.applications;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.widget.TextView;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
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

    private static final Map<Object, Map<String, CharSequence>> ORIGINALS = new WeakHashMap<>();
    private static final Map<String, String> RESOURCE_ORIGINALS = new HashMap<>();
    private static final Map<TextView, CharSequence> VIEW_ORIGINALS = new WeakHashMap<>();
    private static final Map<String, String> RESOURCE_TEXTS = new HashMap<>();
    private static final AtomicBoolean ENABLED = new AtomicBoolean();

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
            XposedBridge.log(HookConstants.TAG + ": Hooked Gallery PocFeatures.isEnabled");
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
                AtomicBoolean enabled = ENABLED;
                enabled.set(translationEnabled(context));
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
                RESOURCE_ORIGINALS.put(translation, resources.getText(id).toString());
                RESOURCE_TEXTS.put(resources.getText(id).toString(), translation);
            }
        });
        XposedHelpers.findAndHookMethod(TextView.class, "setText",
                CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        TextView view = (TextView) param.thisObject;
                        CharSequence original = (CharSequence) param.args[0];
                        String translated = original == null ? null : RESOURCE_TEXTS.get(original.toString());
                        synchronized (VIEW_ORIGINALS) {
                            if (translated == null) {
                                VIEW_ORIGINALS.remove(view);
                            } else {
                                VIEW_ORIGINALS.put(view, original);
                            }
                        }
                        if (enabled.get() && translated != null) {
                            param.args[0] = translated;
                        }
                    }
                });
        XposedBridge.log(HookConstants.TAG + ": Gallery Labs Chinese resources="
                + translations.size() + "; enabled=" + enabled.get());
    }

    private static void installLiteralTranslations(
            ClassLoader classLoader, AtomicBoolean enabled) {
        try {
            Class<?> fragment = XposedHelpers.findClass(LABS_BASE_FRAGMENT, classLoader);
            XposedHelpers.findAndHookMethod(fragment, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
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
                    if (param.args == null
                            || param.args.length != 1
                            || param.args[0] == null
                            || (!ORIGINALS.containsKey(param.thisObject)
                            && !isLabsPreference(param.thisObject))) {
                        return;
                    }
                    String setter = param.method.getName();
                    CharSequence original = (CharSequence) param.args[0];
                    original = RESOURCE_ORIGINALS.getOrDefault(original.toString(), original.toString());
                    String translation = translationFor(original.toString());
                    synchronized (ORIGINALS) {
                        Map<String, CharSequence> texts = ORIGINALS.computeIfAbsent(
                                param.thisObject, ignored -> new HashMap<>());
                        if (translation != null) {
                            texts.put(setter, original);
                        } else {
                            texts.remove(setter);
                        }
                    }
                    if (translation != null) {
                        param.args[0] = enabled.get() ? translation : original;
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
        CharSequence source = RESOURCE_ORIGINALS.getOrDefault(original.toString(), original.toString());
        String translation = translationFor(source.toString());
        if (translation != null) {
            synchronized (ORIGINALS) {
                ORIGINALS.computeIfAbsent(preference, ignored -> new HashMap<>()).put(setter, source);
            }
            XposedHelpers.callMethod(preference, setter, source);
        }
    }

    private static String translationFor(String original) {
        String resource = RESOURCE_TEXTS.get(original);
        return resource != null ? resource : GalleryLabsChineseTranslations.literalTranslation(original);
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
                        synchronized (ORIGINALS) {
                            for (Object preference : new ArrayList<>(ORIGINALS.keySet())) {
                                Map<String, CharSequence> texts = ORIGINALS.get(preference);
                                if (texts == null) {
                                    continue;
                                }
                                for (Map.Entry<String, CharSequence> text : new HashMap<>(texts).entrySet()) {
                                    XposedHelpers.callMethod(preference, text.getKey(), text.getValue());
                                }
                            }
                        }
                        synchronized (VIEW_ORIGINALS) {
                            for (TextView view : new ArrayList<>(VIEW_ORIGINALS.keySet())) {
                                CharSequence original = VIEW_ORIGINALS.get(view);
                                if (original != null) {
                                    view.setText(original);
                                }
                            }
                        }
                        XposedBridge.log(HookConstants.TAG + ": Gallery Labs translation enabled=" + enabled.get());
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
