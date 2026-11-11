package io.github.pigerzhu.onelab.hook.system;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import io.github.pigerzhu.onelab.contract.SettingsKeys;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Forces an arbitrary per-app min aspect ratio on the unfolded main display.
 *
 * One UI 8 (Android 16) resolves the effective aspect ratio through
 * {@code com.android.server.wm.AppCompatAspectRatioPolicy#getMinAspectRatio()}, which is the
 * final value the letterbox computation consumes. The policy keeps a direct {@code mActivityRecord}
 * reference, so we read the package name from there and, when OneLab has a custom float configured
 * for it, override the return value. This bypasses every upstream gate (user override code, system
 * defaults) because it replaces the final result. The Samsung subclass
 * {@code MultiTaskingAppCompatAspectRatioPolicy} only defines a constructor and inherits this
 * method, so hooking the base class covers both.
 *
 * <p>Overrides are stored in {@code Settings.Global} under
 * {@link HookConstants#KEY_ASPECT_RATIO_OVERRIDES} as {@code pkg:ratio:inner;pkg:ratio:inner},
 * e.g. {@code com.tencent.mm:1.5000:1}. A ratio must be &gt; 1.0 to letterbox; {@code inner} is 1
 * to apply only on the inner/main display (id {@value #INNER_DISPLAY_ID}) or 0 for all displays.
 * The cover display on this fold is a separate display id, so inner-only overrides are skipped
 * there.
 */
public final class AspectRatioHook {
    private static final String POLICY_CLASS = "com.android.server.wm.AppCompatAspectRatioPolicy";
    private static final String OVERRIDES_CLASS =
            "com.android.server.wm.AppCompatAspectRatioOverrides";
    private static final int INNER_DISPLAY_ID = 0;
    private static final Object LOCK = new Object();

    private static volatile ContentResolver resolver;
    private static volatile Map<String, RatioOverride> overrides = Collections.emptyMap();
    private static volatile boolean observerRegistered;
    private static String cachedRaw;

    private AspectRatioHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> policyClass = XposedHelpers.findClass(POLICY_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(policyClass, "getMinAspectRatio", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Float custom = customAspectRatioFor(param.thisObject);
                    if (custom != null && !isFullscreenOverride(param.thisObject)) {
                        param.setResult(custom);
                    }
                }
            });
            Log.i(HookConstants.TAG, "Hooked AppCompatAspectRatioPolicy.getMinAspectRatio");
            installUserAspectRatioStateHooks(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": aspect ratio hook failed");
            XposedBridge.log(t);
        }
    }

    private static void installUserAspectRatioStateHooks(ClassLoader classLoader) {
        try {
            Class<?> overridesClass = XposedHelpers.findClass(OVERRIDES_CLASS, classLoader);
            XposedHelpers.findAndHookMethod(
                    overridesClass,
                    "getUserMinAspectRatioOverrideCode",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Integer original = asInteger(param.getResult());
                            if (original == null) return;
                            Float custom = customAspectRatioFor(param.thisObject);
                            if (custom == null) return;
                            param.setResult(AspectRatioFullscreenPolicy.syntheticUserCode(
                                    custom, original));
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    overridesClass,
                    "getUserMinAspectRatio",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            Float custom = customAspectRatioFor(param.thisObject);
                            if (custom == null || isFullscreenOverride(param.thisObject)) return;
                            param.setResult(custom);
                        }
                    });
            XposedBridge.log(HookConstants.TAG
                    + ": Hooked AppCompatAspectRatioOverrides user ratio state");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG
                    + ": AppCompatAspectRatioOverrides state hook failed");
            XposedBridge.log(t);
        }
    }

    private static Float customAspectRatioFor(Object policy) {
        Object activityRecord = activityRecordOf(policy);
        if (activityRecord == null) {
            return null;
        }
        ensureObserver(activityRecord);
        String packageName = packageNameOf(activityRecord);
        if (packageName == null) {
            return null;
        }
        RatioOverride override = overrides.get(packageName);
        if (override == null) {
            return null;
        }
        if (override.innerOnly && !isOnInnerDisplay(activityRecord)) {
            return null;
        }
        return override.ratio;
    }

    private static Object activityRecordOf(Object owner) {
        return HookUtils.findFieldValue(owner, "mActivityRecord");
    }

    private static boolean isFullscreenOverride(Object owner) {
        try {
            Object overrides = HookUtils.findFieldValue(owner, "mAppCompatOverrides");
            if (overrides != null) {
                Object aspectRatioOverrides = HookUtils.findFieldValue(
                        overrides, "mAspectRatioOverrides");
                if (aspectRatioOverrides != null) overrides = aspectRatioOverrides;
            }
            if (overrides == null) overrides = owner;
            Object code = XposedHelpers.callMethod(
                    overrides, "getUserMinAspectRatioOverrideCode");
            return code instanceof Integer && (Integer) code == 6;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Integer asInteger(Object value) {
        return value instanceof Integer ? (Integer) value : null;
    }

    private static boolean isOnInnerDisplay(Object activityRecord) {
        try {
            Object displayId = XposedHelpers.callMethod(activityRecord, "getDisplayId");
            if (displayId instanceof Integer) {
                return (Integer) displayId == INNER_DISPLAY_ID;
            }
        } catch (Throwable ignored) {
            // Preserve the system policy when an inner-only rule cannot identify the display.
        }
        return false;
    }

    private static String packageNameOf(Object activityRecord) {
        Object direct = HookUtils.findFieldValue(activityRecord, "packageName");
        if (direct instanceof String) {
            return (String) direct;
        }
        Object component = HookUtils.findFieldValue(activityRecord, "mActivityComponent");
        if (component != null) {
            try {
                return (String) XposedHelpers.callMethod(component, "getPackageName");
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void ensureObserver(Object activityRecord) {
        if (observerRegistered) {
            return;
        }
        ContentResolver contentResolver = findResolver(activityRecord);
        if (contentResolver == null) {
            return;
        }
        synchronized (LOCK) {
            if (observerRegistered) {
                return;
            }
            resolver = contentResolver;
            Handler handler = new Handler(Looper.getMainLooper());
            contentResolver.registerContentObserver(
                    Settings.Global.getUriFor(SettingsKeys.KEY_ASPECT_RATIO_OVERRIDES),
                    false,
                    new ContentObserver(handler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            reloadConfig();
                        }
                    }
            );
            observerRegistered = true;
            handler.post(AspectRatioHook::reloadConfig);
            Log.i(HookConstants.TAG, "Registered aspect-ratio settings observer");
        }
    }

    private static ContentResolver findResolver(Object activityRecord) {
        ContentResolver cached = resolver;
        if (cached != null) {
            return cached;
        }
        Object atmService = HookUtils.findFieldValue(activityRecord, "mAtmService");
        Object context = atmService != null ? HookUtils.findFieldValue(atmService, "mContext") : null;
        ContentResolver contentResolver = HookUtils.resolverFromContextObject(context);
        if (contentResolver == null) {
            contentResolver = HookUtils.resolverFromAnyContext(activityRecord);
        }
        if (contentResolver != null) {
            resolver = contentResolver;
        }
        return contentResolver;
    }

    private static void reloadConfig() {
        ContentResolver contentResolver = resolver;
        if (contentResolver == null) {
            return;
        }
        try {
            String raw = Settings.Global.getString(
                    contentResolver,
                    SettingsKeys.KEY_ASPECT_RATIO_OVERRIDES
            );
            synchronized (LOCK) {
                if (Objects.equals(raw, cachedRaw)) {
                    return;
                }
                cachedRaw = raw;
            }
            overrides = parse(raw);
            Log.i(HookConstants.TAG, "Applied " + overrides.size() + " aspect-ratio policies");
        } catch (Throwable t) {
            XposedBridge.log(HookConstants.TAG + ": aspect-ratio config reload failed");
            XposedBridge.log(t);
        }
    }

    private static Map<String, RatioOverride> parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RatioOverride> parsed = new HashMap<>();
        for (String entry : raw.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length < 2) {
                continue;
            }
            String packageName = parts[0].trim();
            if (packageName.isEmpty()) {
                continue;
            }
            try {
                float ratio = Float.parseFloat(parts[1].trim());
                if (ratio > 1.0f) {
                    boolean innerOnly = parts.length < 3 || !"0".equals(parts[2].trim());
                    parsed.put(packageName, new RatioOverride(ratio, innerOnly));
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed ratio; keep the rest of the map usable.
            }
        }
        return parsed.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(parsed);
    }

    private static final class RatioOverride {
        final float ratio;
        final boolean innerOnly;

        RatioOverride(float ratio, boolean innerOnly) {
            this.ratio = ratio;
            this.innerOnly = innerOnly;
        }
    }
}
