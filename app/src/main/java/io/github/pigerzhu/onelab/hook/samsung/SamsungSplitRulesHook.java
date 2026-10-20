package io.github.pigerzhu.onelab.hook.samsung;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_ITHOME_ACTIVITY_EMBEDDING;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_HUPU_ACTIVITY_EMBEDDING;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_QQ_FOLD_LAYOUT;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_DIAGNOSTICS;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    private static final String PACKAGE_FEATURE_GROUP_RECORD_CLASS =
            "com.samsung.android.server.packagefeature.core.PackageFeatureGroupRecord";
    private static final String FOLDABLE_PACKAGE_POLICY = "FoldablePackagePolicy";
    private static final String SPLIT_ACTIVITY_FEATURE = "splitActivity";
    private static final String PACKAGE_POLICY_DISABLED = "package_policy_disabled";
    private static final String ACTIVITY_RECORD_CLASS =
            "com.android.server.wm.ActivityRecord";
    private static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
    private static final Object LOCK = new Object();
    private static final Set<String> INJECTED_PACKAGES = new HashSet<>();
    private static final Set<String> LAST_SPLIT_BINDER_PACKAGES = new HashSet<>();
    private static final Set<String> LAST_EMBED_BINDER_PACKAGES = new HashSet<>();
    private static final Map<String, Integer> LAST_SPLIT_PACKAGE_STATES = new HashMap<>();
    private static final List<String> PACKAGE_FEATURE_EVENTS = new ArrayList<>();

    private static volatile Object activeRepository;
    private static volatile Object activeEmbedRepository;
    private static volatile ContentResolver activeResolver;
    private static volatile Object activeContext;
    private static volatile boolean observersRegistered;
    private static volatile boolean embedSnapshotReady;
    private static volatile boolean splitBinderObserved;
    private static volatile boolean embedBinderObserved;
    private static volatile int splitStateQueryUserId = -1;
    private static volatile int splitStateQueryCallingUid = -1;
    private static volatile boolean wechatInstalledInSystemServer;
    private static volatile boolean packageFeatureObserved;
    private static volatile boolean packagePolicyDisabledSeen;
    private static volatile String foldablePolicySource = "unobserved";
    private static volatile int foldablePolicyVersion = -1;
    private static volatile int splitActivityPolicyEffectiveCount = -1;
    private static final Set<String> SPLIT_ACTIVITY_POLICY_PACKAGES = new HashSet<>();
    private static volatile ControllerPath controllerPath;
    private static volatile Method setRequestedOrientationMethod;

    private SamsungSplitRulesHook() {
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        installPackageFeatureDiagnostics(lpparam.classLoader);
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
            installOrientationHook(lpparam.classLoader);
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
                                applyFollowDeviceOrientation(
                                        targetRecord, packageName, activityName);
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
                                        param.getResult(),
                                        SnapshotSource.SPLIT_BINDER);
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
                                        param.getResult(),
                                        SnapshotSource.EMBED_BINDER);
                            }
                        }
                    });
            XposedBridge.hookAllMethods(
                    binderClass,
                    "getSplitActivityPackageEnabled",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()
                                    || param.args == null
                                    || param.args.length < 2
                                    || !(param.args[0] instanceof String)
                                    || !(param.args[1] instanceof Number)
                                    || !(param.getResult() instanceof Number)) {
                                return;
                            }
                            synchronized (LOCK) {
                                LAST_SPLIT_PACKAGE_STATES.put(
                                        (String) param.args[0],
                                        ((Number) param.getResult()).intValue());
                                splitStateQueryUserId = ((Number) param.args[1]).intValue();
                                splitStateQueryCallingUid = Binder.getCallingUid();
                                if (splitStateQueriesCompleteLocked()) {
                                    publishDiagnosticsLocked(
                                            repositoryMap(activeRepository),
                                            embeddedRepositoryPackages(),
                                            combinedSnapshotPackages());
                                }
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

    private static void installPackageFeatureDiagnostics(ClassLoader classLoader) {
        try {
            Class<?> recordClass = XposedHelpers.findClass(
                    PACKAGE_FEATURE_GROUP_RECORD_CLASS, classLoader);
            XposedBridge.hookAllMethods(
                    recordClass,
                    "propagateToCallbacks",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            capturePackageFeatureState(param.thisObject);
                        }
                    });
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": package-feature diagnostics unavailable");
            XposedBridge.log(throwable);
        }
    }

    private static void capturePackageFeatureState(Object record) {
        try {
            Object group = HookUtils.findFieldValue(record, "mGroup");
            Object groupName = HookUtils.findFieldValue(group, "mName");
            if (!FOLDABLE_PACKAGE_POLICY.equals(String.valueOf(groupName))) return;

            Object groupData = HookUtils.findFieldValue(record, "mGroupData");
            Object rawSplitActivityData = groupData == null
                    ? null
                    : XposedHelpers.callMethod(
                            groupData, "getPackageFeature", SPLIT_ACTIVITY_FEATURE);
            Object effectiveSplitActivityData = XposedHelpers.callMethod(
                    record, "getPackageFeatureData", SPLIT_ACTIVITY_FEATURE);
            Object version = XposedHelpers.callMethod(record, "getCurrentVersion");
            Object source = HookUtils.findFieldValue(record, "mGroupDataSource");
            Object dataUtil = HookUtils.findFieldValue(record, "mGroupDataUtil");
            ContentResolver resolver = HookUtils.resolverFromAnyContext(dataUtil);
            boolean disabled = resolver != null
                    && Settings.Global.getInt(resolver, PACKAGE_POLICY_DISABLED, 0) == 1;

            synchronized (LOCK) {
                packageFeatureObserved = true;
                packagePolicyDisabledSeen |= disabled;
                foldablePolicySource = disabled ? "POLICY_DISABLED" : String.valueOf(source);
                foldablePolicyVersion = version instanceof Number
                        ? ((Number) version).intValue()
                        : -1;
                splitActivityPolicyEffectiveCount = effectiveSplitActivityData instanceof Map
                        ? ((Map<?, ?>) effectiveSplitActivityData).size()
                        : -1;
                SPLIT_ACTIVITY_POLICY_PACKAGES.clear();
                addPackageNames(
                        SPLIT_ACTIVITY_POLICY_PACKAGES,
                        rawSplitActivityData instanceof Map
                                ? ((Map<?, ?>) rawSplitActivityData).keySet()
                                : null);
                String event = "t=" + SystemClock.elapsedRealtime()
                        + ",source=" + foldablePolicySource
                        + ",version=" + foldablePolicyVersion
                        + ",raw=" + SPLIT_ACTIVITY_POLICY_PACKAGES.size()
                        + ",effective=" + splitActivityPolicyEffectiveCount;
                if (PACKAGE_FEATURE_EVENTS.isEmpty()
                        || !samePackageFeatureEvent(
                                PACKAGE_FEATURE_EVENTS.get(PACKAGE_FEATURE_EVENTS.size() - 1),
                                event)) {
                    PACKAGE_FEATURE_EVENTS.add(event);
                    if (PACKAGE_FEATURE_EVENTS.size() > 16) {
                        PACKAGE_FEATURE_EVENTS.remove(0);
                    }
                }
                if (activeResolver == null && resolver != null) {
                    activeResolver = resolver;
                }
                publishDiagnosticsLocked(
                        repositoryMap(activeRepository),
                        embeddedRepositoryPackages(),
                        combinedSnapshotPackages());
            }
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": failed to capture package-feature state");
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
        wechatInstalledInSystemServer = isPackageInstalled(
                atm, HookConstants.WECHAT_PACKAGE);
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
            refreshEnabledState(resolver, ruleSet);
        }
    }

    private static void installOrientationHook(ClassLoader classLoader) {
        try {
            Class<?> activityRecordClass = XposedHelpers.findClass(
                    ACTIVITY_RECORD_CLASS, classLoader);
            Method method = activityRecordClass.getDeclaredMethod(
                    "setRequestedOrientation", int.class);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null
                            || param.args.length != 1
                            || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    String packageName = activityRecordPackageName(param.thisObject);
                    String activityName = activityRecordClassName(param.thisObject);
                    if (shouldIgnorePortraitRequest(
                            packageName, activityName, (Integer) param.args[0])) {
                        param.setResult(null);
                    }
                }
            });
            setRequestedOrientationMethod = method;
            XposedBridge.log(TAG + ": orientation compatibility installed");
        } catch (Throwable throwable) {
            setRequestedOrientationMethod = null;
            XposedBridge.log(TAG + ": orientation compatibility unavailable");
            XposedBridge.log(throwable);
        }
    }

    private static void applyFollowDeviceOrientation(
            Object activityRecord,
            String packageName,
            String activityName
    ) {
        Method method = setRequestedOrientationMethod;
        if (method == null || !shouldFollowDeviceOrientation(packageName, activityName)) return;
        try {
            XposedBridge.invokeOriginalMethod(
                    method,
                    activityRecord,
                    new Object[]{SCREEN_ORIENTATION_UNSPECIFIED});
        } catch (Throwable throwable) {
            setRequestedOrientationMethod = null;
            XposedBridge.log(TAG + ": failed to apply follow-device orientation");
            XposedBridge.log(throwable);
        }
    }

    private static void refreshEnabledState(
            ContentResolver resolver,
            SamsungSplitRuleCatalog.RuleSet ruleSet
    ) {
        boolean settingEnabled = ruleSet.settingKey == null || HookUtils.globalEnabled(
                resolver, ruleSet.settingKey, 0);
        boolean masterEnabled = ruleSet.masterSettingKey == null || HookUtils.globalEnabled(
                resolver, ruleSet.masterSettingKey, 0);
        ruleSet.enabled.set(ruleSet.isEnabledBy(settingEnabled, masterEnabled));
    }

    private static void registerObserversLocked(ContentResolver resolver) {
        if (observersRegistered) return;
        Handler handler = new Handler(Looper.getMainLooper());
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (ruleSet.settingKey == null) continue;
            resolver.registerContentObserver(
                    Settings.Global.getUriFor(ruleSet.settingKey),
                    false,
                    new ContentObserver(handler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            refreshEnabledState(resolver, ruleSet);
                            synchronized (LOCK) {
                                applyLocked(activeRepository);
                            }
                        }
                    });
            if (ruleSet.masterSettingKey != null) {
                resolver.registerContentObserver(
                        Settings.Global.getUriFor(ruleSet.masterSettingKey),
                        false,
                        new ContentObserver(handler) {
                            @Override
                            public void onChange(boolean selfChange) {
                                refreshEnabledState(resolver, ruleSet);
                                synchronized (LOCK) {
                                    applyLocked(activeRepository);
                                }
                            }
                        });
            }
        }
        observersRegistered = true;
    }

    private static void applyLocked(Object repository) {
        if (repository == null) return;
        Map<?, ?> rules = repositoryMap(repository);
        if (rules == null) return;

        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (!ruleSet.managesRepository()) continue;
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
        publishAllowedPackagesLocked(rules, null, SnapshotSource.REPOSITORY);
    }

    private static void publishAllowedPackagesLocked(
            Map<?, ?> rules,
            Object returnedPackages,
            SnapshotSource source
    ) {
        ContentResolver resolver = activeResolver;
        if (resolver == null) return;

        Set<String> legacyPackages = new HashSet<>();
        if (rules != null) {
            for (Object key : rules.keySet()) {
                if (key instanceof String && !((String) key).isEmpty()) {
                    legacyPackages.add((String) key);
                }
            }
        }

        Set<String> embedPackages = embeddedRepositoryPackages();
        Object embedded = HookUtils.findFieldValue(activeEmbedRepository, "mRepository");

        if (source == SnapshotSource.SPLIT_BINDER) {
            splitBinderObserved = true;
            LAST_SPLIT_BINDER_PACKAGES.clear();
            LAST_SPLIT_PACKAGE_STATES.clear();
            splitStateQueryUserId = -1;
            splitStateQueryCallingUid = -1;
            addPackageNames(LAST_SPLIT_BINDER_PACKAGES, returnedPackages);
        } else if (source == SnapshotSource.EMBED_BINDER) {
            embedBinderObserved = true;
            LAST_EMBED_BINDER_PACKAGES.clear();
            addPackageNames(LAST_EMBED_BINDER_PACKAGES, returnedPackages);
        }

        Set<String> packageSet = new HashSet<>(legacyPackages);
        packageSet.addAll(embedPackages);
        packageSet.addAll(LAST_SPLIT_BINDER_PACKAGES);
        packageSet.addAll(LAST_EMBED_BINDER_PACKAGES);

        String current = Settings.Global.getString(
                resolver, KEY_SPLIT_VIEW_ALLOWED_PACKAGES);
        if (!embedSnapshotReady && current != null && !current.isEmpty()) {
            addPackageNames(packageSet, current.split(","));
        }
        List<String> packages = new ArrayList<>(packageSet);
        Collections.sort(packages);
        String snapshot = String.join(",", packages);
        if (rules != null && !snapshot.equals(current)) {
            Settings.Global.putString(
                    resolver, KEY_SPLIT_VIEW_ALLOWED_PACKAGES, snapshot);
        }

        publishDiagnosticsLocked(rules, embedPackages, packageSet);
    }

    private static void publishDiagnosticsLocked(
            Map<?, ?> rules,
            Set<String> embedPackages,
            Set<String> snapshotPackages
    ) {
        ContentResolver resolver = activeResolver;
        if (resolver == null) return;

        Set<String> legacyPackages = new HashSet<>();
        if (rules != null) addPackageNames(legacyPackages, rules.keySet());
        Object embedded = HookUtils.findFieldValue(activeEmbedRepository, "mRepository");
        String diagnostics = buildSnapshotDiagnostics(
                rules != null,
                legacyPackages,
                embedded instanceof Iterable<?>,
                embedPackages,
                snapshotPackages);
        String previousDiagnostics = Settings.Global.getString(
                resolver, KEY_SPLIT_VIEW_DIAGNOSTICS);
        if (!diagnostics.equals(previousDiagnostics)) {
            Settings.Global.putString(resolver, KEY_SPLIT_VIEW_DIAGNOSTICS, diagnostics);
            XposedBridge.log(TAG + ": snapshot " + diagnostics);
        }
    }

    private static String buildSnapshotDiagnostics(
            boolean legacyRepositoryAvailable,
            Set<String> legacyPackages,
            boolean embedRepositoryAvailable,
            Set<String> embedPackages,
            Set<String> snapshotPackages
    ) {
        ControllerPath path = controllerPath;
        return "controller=" + (path == null ? "unknown" : path.label)
                + "|legacy_available=" + legacyRepositoryAvailable
                + "|legacy_count=" + legacyPackages.size()
                + "|legacy_wechat=" + legacyPackages.contains(HookConstants.WECHAT_PACKAGE)
                + "|legacy_packages=" + sortedPackageString(legacyPackages)
                + "|embed_available=" + embedRepositoryAvailable
                + "|embed_count=" + embedPackages.size()
                + "|embed_wechat=" + embedPackages.contains(HookConstants.WECHAT_PACKAGE)
                + "|embed_packages=" + sortedPackageString(embedPackages)
                + "|split_binder_seen=" + splitBinderObserved
                + "|split_binder_count=" + LAST_SPLIT_BINDER_PACKAGES.size()
                + "|split_binder_wechat="
                + LAST_SPLIT_BINDER_PACKAGES.contains(HookConstants.WECHAT_PACKAGE)
                + "|split_binder_packages="
                + sortedPackageString(LAST_SPLIT_BINDER_PACKAGES)
                + "|split_state_query_count=" + LAST_SPLIT_PACKAGE_STATES.size()
                + "|split_state_query_complete=" + splitStateQueriesCompleteLocked()
                + "|split_state_query_user_id=" + splitStateQueryUserId
                + "|split_state_query_calling_uid=" + splitStateQueryCallingUid
                + "|split_state_0_packages=" + packagesForStateLocked(0)
                + "|split_state_1_packages=" + packagesForStateLocked(1)
                + "|split_state_2_packages=" + packagesForStateLocked(2)
                + "|split_package_states=" + sortedStateStringLocked()
                + "|embed_binder_seen=" + embedBinderObserved
                + "|embed_binder_count=" + LAST_EMBED_BINDER_PACKAGES.size()
                + "|embed_binder_wechat="
                + LAST_EMBED_BINDER_PACKAGES.contains(HookConstants.WECHAT_PACKAGE)
                + "|embed_binder_packages="
                + sortedPackageString(LAST_EMBED_BINDER_PACKAGES)
                + "|embed_snapshot_ready=" + embedSnapshotReady
                + "|snapshot_count=" + snapshotPackages.size()
                + "|snapshot_wechat=" + snapshotPackages.contains(HookConstants.WECHAT_PACKAGE)
                + "|wechat_installed=" + wechatInstalledInSystemServer
                + "|onelab_injected_packages=" + sortedPackageString(INJECTED_PACKAGES)
                + "|package_policy_disabled_current="
                + (activeResolver == null
                ? "unknown"
                : Settings.Global.getInt(activeResolver, PACKAGE_POLICY_DISABLED, 0))
                + "|package_policy_disabled_seen=" + packagePolicyDisabledSeen
                + "|package_feature_observed=" + packageFeatureObserved
                + "|foldable_policy_source=" + foldablePolicySource
                + "|foldable_policy_version=" + foldablePolicyVersion
                + "|split_activity_policy_raw_count="
                + SPLIT_ACTIVITY_POLICY_PACKAGES.size()
                + "|split_activity_policy_effective_count="
                + splitActivityPolicyEffectiveCount
                + "|split_activity_policy_raw_packages="
                + sortedPackageString(SPLIT_ACTIVITY_POLICY_PACKAGES)
                + "|foldable_policy_events="
                + String.join(";", PACKAGE_FEATURE_EVENTS);
    }

    private static Set<String> embeddedRepositoryPackages() {
        Set<String> packages = new HashSet<>();
        Object embedded = HookUtils.findFieldValue(activeEmbedRepository, "mRepository");
        addPackageNames(packages, embedded);
        return packages;
    }

    private static Set<String> combinedSnapshotPackages() {
        Set<String> packages = new HashSet<>();
        Map<?, ?> rules = repositoryMap(activeRepository);
        if (rules != null) addPackageNames(packages, rules.keySet());
        packages.addAll(embeddedRepositoryPackages());
        packages.addAll(LAST_SPLIT_BINDER_PACKAGES);
        packages.addAll(LAST_EMBED_BINDER_PACKAGES);
        return packages;
    }

    private static String sortedPackageString(Set<String> packages) {
        List<String> sorted = new ArrayList<>(packages);
        Collections.sort(sorted);
        return String.join(",", sorted);
    }

    private static boolean splitStateQueriesCompleteLocked() {
        return splitBinderObserved
                && !LAST_SPLIT_BINDER_PACKAGES.isEmpty()
                && LAST_SPLIT_PACKAGE_STATES.keySet().containsAll(LAST_SPLIT_BINDER_PACKAGES);
    }

    private static String packagesForStateLocked(int state) {
        Set<String> packages = new HashSet<>();
        for (Map.Entry<String, Integer> entry : LAST_SPLIT_PACKAGE_STATES.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == state) {
                packages.add(entry.getKey());
            }
        }
        return sortedPackageString(packages);
    }

    private static String sortedStateStringLocked() {
        List<String> packages = new ArrayList<>(LAST_SPLIT_PACKAGE_STATES.keySet());
        Collections.sort(packages);
        List<String> states = new ArrayList<>();
        for (String packageName : packages) {
            states.add(packageName + ":" + LAST_SPLIT_PACKAGE_STATES.get(packageName));
        }
        return String.join(",", states);
    }

    private static boolean samePackageFeatureEvent(String previous, String current) {
        int previousSource = previous.indexOf(",source=");
        int currentSource = current.indexOf(",source=");
        return previousSource >= 0
                && currentSource >= 0
                && previous.substring(previousSource).equals(current.substring(currentSource));
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

    private static boolean shouldFollowDeviceOrientation(
            String packageName,
            String activityName
    ) {
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (ruleSet.packageName.equals(packageName)
                    && ruleSet.shouldFollowDeviceOrientation(activityName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldIgnorePortraitRequest(
            String packageName,
            String activityName,
            int requestedOrientation
    ) {
        for (SamsungSplitRuleCatalog.RuleSet ruleSet
                : SamsungSplitRuleCatalog.RULE_SETS) {
            if (ruleSet.packageName.equals(packageName)
                    && ruleSet.shouldIgnorePortraitRequest(
                    activityName, requestedOrientation)) {
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

    private enum SnapshotSource {
        REPOSITORY,
        SPLIT_BINDER,
        EMBED_BINDER
    }

}
