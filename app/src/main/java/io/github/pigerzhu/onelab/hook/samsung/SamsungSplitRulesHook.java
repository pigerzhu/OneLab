package io.github.pigerzhu.onelab.hook.samsung;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Bridges verified app-supplied fold rules into Samsung's split-activity repository. */
public final class SamsungSplitRulesHook {
    private static final String TAG = "OneLab/SamsungSplitRules";
    private static final int ONE_UI_8_5 = 80500;
    private static final String LEGACY_CONTROLLER_CLASS =
            "com.android.server.wm.MultiTaskingController";
    private static final String ONE_UI_8_5_CONTROLLER_CLASS =
            "com.android.server.wm.SplitActivityController";
    private static final String LEGACY_CONTROLLER_FIELD = "mMultiTaskingController";
    private static final String MULTI_TASKING_CORE_FIELD = "mMultiTaskingCore";
    private static final String[] ONELAB_EMBED_PACKAGES = {
            HookConstants.TONGCHENG_PACKAGE,
            HookConstants.QQ_PACKAGE,
            HookConstants.ITHOME_PACKAGE,
            HookConstants.HUPU_PACKAGE
    };
    private static final String ONE_UI_8_5_CONTROLLER_FIELD = "mSplitActivityController";
    private static final String BINDER_CLASS =
            "com.android.server.wm.MultiTaskingBinder";
    private static final String ATM_SERVICE_CLASS =
            "com.android.server.wm.ActivityTaskManagerService";
    private static final String REPOSITORY_CLASS =
            "com.android.server.wm.SplitActivityInfoRepository";
    private static final String ACTIVITY_STARTER_CLASS =
            "com.android.server.wm.ActivityStarter";
    private static final Object LOCK = new Object();
    private static final Set<String> INJECTED_PACKAGES = new HashSet<>();

    private static volatile Object activeRepository;
    private static volatile Object activeEmbedRepository;
    private static volatile ContentResolver activeResolver;
    private static volatile Object activeContext;
    private static volatile boolean observersRegistered;
    private static volatile boolean embedSnapshotReady;
    private static volatile ControllerPath controllerPath;

    private SamsungSplitRulesHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            controllerPath = selectControllerPath(lpparam.classLoader);
            Class<?> repositoryClass =
                    XposedHelpers.findClass(REPOSITORY_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    repositoryClass,
                    "onPackageFeatureDataChanged",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            synchronized (LOCK) {
                                if (param.thisObject != activeRepository) return;
                                INJECTED_PACKAGES.clear();
                                applyLocked(param.thisObject);
                            }
                        }
                    });

            Class<?> controllerClass =
                    XposedHelpers.findClass(
                            controllerPath.className,
                            lpparam.classLoader
                    );
            XposedBridge.hookAllMethods(
                    controllerClass,
                    "getSplitActivityInfo",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 3) return;
                            String packageName = String.valueOf(param.args[0]);
                            String targetActivity = String.valueOf(param.args[2]);
                            if (isForcedFullscreen(packageName, targetActivity)) {
                                param.setResult(null);
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
                            initializeFromAtm(param.thisObject);
                        }
                    });

            Class<?> activityStarterClass =
                    XposedHelpers.findClass(ACTIVITY_STARTER_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    activityStarterClass,
                    "reparentActivitiesToActivityGroupIfNeeded",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 3) return;
                            Object targetRecord = param.args[2];
                            String packageName = activityRecordPackageName(targetRecord);
                            String activityName = activityRecordClassName(targetRecord);
                            if (isForcedFullscreen(packageName, activityName)) {
                                param.setResult(null);
                            }
                        }
                    });

            Class<?> binderClass =
                    XposedHelpers.findClass(BINDER_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(
                    binderClass,
                    "getSplitActivityAllowPackages",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            initializeFromBinder(param.thisObject);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            synchronized (LOCK) {
                                publishAllowedPackagesLocked(
                                        repositoryMap(activeRepository),
                                        param.getResult());
                            }
                        }
                    });
            XposedBridge.hookAllMethods(
                    binderClass,
                    "getSupportEmbedActivityPackages",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            initializeFromBinder(param.thisObject);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            initializeFromBinder(param.thisObject);
                            synchronized (LOCK) {
                                embedSnapshotReady = true;
                                publishAllowedPackagesLocked(
                                        repositoryMap(activeRepository),
                                        param.getResult());
                            }
                        }
                    });
            hookManagedEmbedEnabledState(binderClass);
            XposedBridge.log(TAG + ": installed " + controllerPath.label);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": installation failed");
            XposedBridge.log(throwable);
        }
    }

    private static void initializeFromBinder(Object binder) {
        Object atm = HookUtils.findFieldValue(binder, "mAtm");
        initializeFromAtm(atm);
    }

    private static void initializeFromAtm(Object atm) {
        ControllerPath path = controllerPath;
        Object controller = path == null
                ? null
                : HookUtils.findFieldValue(atm, path.fieldName);
        Object multiTaskingController =
                HookUtils.findFieldValue(atm, LEGACY_CONTROLLER_FIELD);
        if (multiTaskingController == null) {
            multiTaskingController = HookUtils.findFieldValue(
                    atm, MULTI_TASKING_CORE_FIELD);
        }
        activeEmbedRepository = HookUtils.findFieldValue(
                multiTaskingController, "mActivityEmbeddedPackageRepository");
        activeContext = HookUtils.findFieldValue(atm, "mContext");
        registerOnelabEmbedSupport(atm);
        if (controller != null) initialize(controller);
    }

    private static void registerOnelabEmbedSupport(Object atm) {
        Object repository = activeEmbedRepository;
        if (repository == null) return;
        for (String packageName : ONELAB_EMBED_PACKAGES) {
            if (!isPackageInstalled(atm, packageName)) continue;
            try {
                XposedHelpers.callMethod(repository, "add", packageName);
            } catch (Throwable throwable) {
                XposedBridge.log(TAG + ": failed to register embed support for "
                        + packageName);
                XposedBridge.log(throwable);
            }
        }
    }

    private static void hookManagedEmbedEnabledState(Class<?> binderClass) {
        XposedBridge.hookAllMethods(
                binderClass,
                "getEmbedActivityPackageEnabled",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        initializeFromBinder(param.thisObject);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String settingKey = managedEmbedSettingKey(param.args);
                        if (param.hasThrowable() || settingKey == null) return;
                        ContentResolver resolver = activeResolver;
                        if (resolver != null) {
                            int defaultValue = KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING.equals(settingKey)
                                    ? 1 : 0;
                            param.setResult(HookUtils.globalEnabled(
                                    resolver, settingKey, defaultValue));
                        }
                    }
                });
        XposedBridge.hookAllMethods(
                binderClass,
                "setEmbedActivityPackageEnabled",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        initializeFromBinder(param.thisObject);
                        if (!isIthomeSelfUpdate(param.args)) return;
                        ContentResolver resolver = activeResolver;
                        if (resolver == null) return;
                        putGlobalIntAsSystem(
                                resolver,
                                KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING,
                                (Boolean) param.args[1] ? 1 : 0);
                        param.setResult(null);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String settingKey = managedEmbedSettingKey(param.args);
                        if (param.hasThrowable() || settingKey == null
                                || param.args.length < 2
                                || !(param.args[1] instanceof Boolean)) {
                            return;
                        }
                        ContentResolver resolver = activeResolver;
                        if (resolver != null) {
                            putGlobalIntAsSystem(
                                    resolver,
                                    settingKey,
                                    (Boolean) param.args[1] ? 1 : 0);
                        }
                    }
                });
    }

    private static String managedEmbedSettingKey(Object[] args) {
        if (args == null
                || args.length < 2
                || !(args[args.length - 1] instanceof Integer)
                || (Integer) args[args.length - 1] != 0) {
            return null;
        }
        if (HookConstants.QQ_PACKAGE.equals(args[0])) {
            return KEY_ENABLE_QQ_FOLD_LAYOUT;
        }
        if (HookConstants.ITHOME_PACKAGE.equals(args[0])) {
            return KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING;
        }
        if (HookConstants.HUPU_PACKAGE.equals(args[0])) {
            return KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING;
        }
        return null;
    }

    private static boolean isIthomeSelfUpdate(Object[] args) {
        return KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING.equals(managedEmbedSettingKey(args))
                && args.length >= 2
                && args[1] instanceof Boolean
                && HookUtils.packageForCallingUid(
                activeContext, HookConstants.ITHOME_PACKAGE);
    }

    private static void putGlobalIntAsSystem(
            ContentResolver resolver,
            String key,
            int value
    ) {
        long token = Binder.clearCallingIdentity();
        try {
            Settings.Global.putInt(resolver, key, value);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private static boolean isPackageInstalled(Object atm, String packageName) {
        try {
            Object context = HookUtils.findFieldValue(atm, "mContext");
            Object packageManager = XposedHelpers.callMethod(context, "getPackageManager");
            XposedHelpers.callMethod(packageManager, "getPackageInfo", packageName, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void initialize(Object controller) {
        try {
            Object repository = HookUtils.findFieldValue(
                    controller, "mSplitActivityInfoRepository");
            Object atm = HookUtils.findFieldValue(controller, "mAtm");
            if (atm == null) {
                atm = HookUtils.findFieldValue(controller, "mAtmService");
            }
            ContentResolver resolver = HookUtils.resolverFromAnyContext(atm);
            if (repository == null || resolver == null) {
                Log.w(TAG, "Samsung split repository is unavailable");
                XposedBridge.log(TAG + ": Samsung split repository is unavailable");
                return;
            }

            synchronized (LOCK) {
                activeRepository = repository;
                activeResolver = resolver;
                refreshEnabledStates(resolver);
                registerObserversLocked(resolver);
                applyLocked(repository);
            }
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": initialization failed");
            XposedBridge.log(throwable);
        }
    }

    private static void refreshEnabledStates(ContentResolver resolver) {
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            ruleSet.enabled.set(HookUtils.globalEnabled(
                    resolver, ruleSet.settingKey, 0));
        }
    }

    private static void registerObserversLocked(ContentResolver resolver) {
        if (observersRegistered) return;
        Handler handler = new Handler(Looper.getMainLooper());
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(ruleSet.settingKey),
                    false,
                    new ContentObserver(handler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            ruleSet.enabled.set(HookUtils.globalEnabled(
                                    resolver, ruleSet.settingKey, 0));
                            synchronized (LOCK) {
                                applyLocked(activeRepository);
                            }
                        }
                    });
        }
        observersRegistered = true;
    }

    private static void applyLocked(Object repository) {
        if (repository == null) return;
        Map<?, ?> rules = repositoryMap(repository);
        if (rules == null) return;

        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (!ruleSet.enabled.get()) {
                if (INJECTED_PACKAGES.remove(ruleSet.packageName)) {
                    rules.remove(ruleSet.packageName);
                    Log.i(TAG, "Removed split rules for " + ruleSet.packageName);
                }
                continue;
            }
            if (rules.containsKey(ruleSet.packageName)) continue;

            try {
                for (SamsungSplitRuleCatalog.ActivityPair pair : ruleSet.pairs) {
                    XposedHelpers.callMethod(
                            repository,
                            "add",
                            ruleSet.packageName,
                            pair.source,
                            pair.target);
                }
                INJECTED_PACKAGES.add(ruleSet.packageName);
                Log.i(TAG, "Injected " + ruleSet.pairs.length
                        + " split rules for " + ruleSet.packageName);
                XposedBridge.log(TAG + ": Injected " + ruleSet.pairs.length
                        + " split rules for " + ruleSet.packageName);
            } catch (Throwable throwable) {
                rules.remove(ruleSet.packageName);
                INJECTED_PACKAGES.remove(ruleSet.packageName);
                XposedBridge.log(TAG + ": rule injection failed for "
                        + ruleSet.packageName);
                XposedBridge.log(throwable);
            }
        }
        publishAllowedPackagesLocked(rules, null);
    }

    private static void publishAllowedPackagesLocked(Map<?, ?> rules, Object returnedPackages) {
        ContentResolver resolver = activeResolver;
        if (resolver == null || rules == null) return;

        Set<String> packageSet = new HashSet<>();
        for (Object key : rules.keySet()) {
            if (key instanceof String && !((String) key).isEmpty()) {
                packageSet.add((String) key);
            }
        }
        Object embedded = HookUtils.findFieldValue(
                activeEmbedRepository, "mRepository");
        if (embedded instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) embedded) {
                if (value instanceof String && !((String) value).isEmpty()) {
                    packageSet.add((String) value);
                }
            }
        }
        addPackageNames(packageSet, returnedPackages);

        String current = Settings.Global.getString(
                resolver, KEY_SPLIT_VIEW_ALLOWED_PACKAGES);
        if (!embedSnapshotReady && current != null && !current.isEmpty()) {
            addPackageNames(packageSet, current.split(","));
        }
        List<String> packages = new ArrayList<>(packageSet);
        Collections.sort(packages);
        String snapshot = String.join(",", packages);
        if (!snapshot.equals(current)) {
            Settings.Global.putString(
                    resolver, KEY_SPLIT_VIEW_ALLOWED_PACKAGES, snapshot);
        }
    }

    private static void addPackageNames(Set<String> packages, Object values) {
        if (values instanceof String) {
            String packageName = ((String) values).trim();
            if (!packageName.isEmpty()) packages.add(packageName);
            return;
        }
        if (values instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) values) {
                addPackageNames(packages, value);
            }
            return;
        }
        if (values instanceof Object[]) {
            for (Object value : (Object[]) values) {
                addPackageNames(packages, value);
            }
        }
    }

    private static boolean isForcedFullscreen(String packageName, String activityName) {
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (ruleSet.enabled.get()
                    && ruleSet.packageName.equals(packageName)
                    && ruleSet.fullscreenActivities.contains(activityName)) {
                return true;
            }
        }
        return false;
    }

    private static String activityRecordPackageName(Object activityRecord) {
        Object value = HookUtils.findFieldValue(activityRecord, "packageName");
        return value instanceof String ? (String) value : "";
    }

    private static String activityRecordClassName(Object activityRecord) {
        Object activityInfo = HookUtils.findFieldValue(activityRecord, "info");
        Object value = HookUtils.findFieldValue(activityInfo, "name");
        return value instanceof String ? (String) value : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> repositoryMap(Object repository) {
        Object value = HookUtils.findFieldValue(repository, "mRepository");
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static ControllerPath selectControllerPath(ClassLoader classLoader) {
        int oneUiVersion = oneUiVersion(classLoader);
        ControllerPath preferred = oneUiVersion >= ONE_UI_8_5
                ? ControllerPath.ONE_UI_8_5
                : ControllerPath.LEGACY;
        if (XposedHelpers.findClassIfExists(preferred.className, classLoader) != null) {
            return preferred;
        }
        ControllerPath fallback = preferred == ControllerPath.ONE_UI_8_5
                ? ControllerPath.LEGACY
                : ControllerPath.ONE_UI_8_5;
        if (XposedHelpers.findClassIfExists(fallback.className, classLoader) != null) {
            Log.w(TAG, "One UI version/controller mismatch; using "
                    + fallback.label);
            return fallback;
        }
        throw new IllegalStateException("Samsung split-activity controller unavailable");
    }

    private static int oneUiVersion(ClassLoader classLoader) {
        try {
            Class<?> systemProperties = XposedHelpers.findClass(
                    "android.os.SystemProperties",
                    classLoader
            );
            Object value = XposedHelpers.callStaticMethod(
                    systemProperties,
                    "getInt",
                    "ro.build.version.oneui",
                    0
            );
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable throwable) {
            Log.w(TAG, "Unable to read One UI version; detecting controller class");
            return 0;
        }
    }

    private static final class ControllerPath {
        static final ControllerPath LEGACY = new ControllerPath(
                "One UI 8 legacy controller",
                LEGACY_CONTROLLER_CLASS,
                LEGACY_CONTROLLER_FIELD
        );
        static final ControllerPath ONE_UI_8_5 = new ControllerPath(
                "One UI 8.5 controller",
                ONE_UI_8_5_CONTROLLER_CLASS,
                ONE_UI_8_5_CONTROLLER_FIELD
        );

        final String label;
        final String className;
        final String fieldName;

        ControllerPath(String label, String className, String fieldName) {
            this.label = label;
            this.className = className;
            this.fieldName = fieldName;
        }
    }

}
