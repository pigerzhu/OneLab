package io.github.pigerzhu.onelab.hook.samsung;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_DIAGNOSTICS;

import io.github.pigerzhu.onelab.hook.core.HookConstants;
import io.github.pigerzhu.onelab.hook.core.HookUtils;

import android.content.ContentResolver;
import android.database.ContentObserver;
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
    private static final String TONGCHENG_PACKAGE = "com.tongcheng.android";
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
    private static final Set<String> LAST_SPLIT_BINDER_PACKAGES = new HashSet<>();
    private static final Set<String> LAST_EMBED_BINDER_PACKAGES = new HashSet<>();

    private static volatile Object activeRepository;
    private static volatile Object activeEmbedRepository;
    private static volatile ContentResolver activeResolver;
    private static volatile boolean observersRegistered;
    private static volatile boolean embedSnapshotReady;
    private static volatile boolean splitBinderObserved;
    private static volatile boolean embedBinderObserved;
    private static volatile boolean wechatInstalledInSystemServer;
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
        wechatInstalledInSystemServer = isPackageInstalled(
                atm, HookConstants.WECHAT_PACKAGE);
        registerTongchengEmbedSupport(atm);
        if (controller != null) initialize(controller);
    }

    private static void registerTongchengEmbedSupport(Object atm) {
        Object repository = activeEmbedRepository;
        if (repository == null || !isPackageInstalled(atm, TONGCHENG_PACKAGE)) return;
        try {
            XposedHelpers.callMethod(repository, "add", TONGCHENG_PACKAGE);
        } catch (Throwable throwable) {
            XposedBridge.log(TAG + ": failed to register Tongcheng embed support");
            XposedBridge.log(throwable);
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

        Set<String> embedPackages = new HashSet<>();
        Object embedded = HookUtils.findFieldValue(
                activeEmbedRepository, "mRepository");
        if (embedded instanceof Iterable<?>) {
            for (Object value : (Iterable<?>) embedded) {
                if (value instanceof String && !((String) value).isEmpty()) {
                    embedPackages.add((String) value);
                }
            }
        }

        if (source == SnapshotSource.SPLIT_BINDER) {
            splitBinderObserved = true;
            LAST_SPLIT_BINDER_PACKAGES.clear();
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

        String diagnostics = buildSnapshotDiagnostics(
                rules != null,
                legacyPackages,
                embedded instanceof Iterable<?>,
                embedPackages,
                packageSet);
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
                + "|embed_available=" + embedRepositoryAvailable
                + "|embed_count=" + embedPackages.size()
                + "|embed_wechat=" + embedPackages.contains(HookConstants.WECHAT_PACKAGE)
                + "|split_binder_seen=" + splitBinderObserved
                + "|split_binder_count=" + LAST_SPLIT_BINDER_PACKAGES.size()
                + "|split_binder_wechat="
                + LAST_SPLIT_BINDER_PACKAGES.contains(HookConstants.WECHAT_PACKAGE)
                + "|embed_binder_seen=" + embedBinderObserved
                + "|embed_binder_count=" + LAST_EMBED_BINDER_PACKAGES.size()
                + "|embed_binder_wechat="
                + LAST_EMBED_BINDER_PACKAGES.contains(HookConstants.WECHAT_PACKAGE)
                + "|embed_snapshot_ready=" + embedSnapshotReady
                + "|snapshot_count=" + snapshotPackages.size()
                + "|snapshot_wechat=" + snapshotPackages.contains(HookConstants.WECHAT_PACKAGE)
                + "|wechat_installed=" + wechatInstalledInSystemServer;
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

    private enum SnapshotSource {
        REPOSITORY,
        SPLIT_BINDER,
        EMBED_BINDER
    }

}
