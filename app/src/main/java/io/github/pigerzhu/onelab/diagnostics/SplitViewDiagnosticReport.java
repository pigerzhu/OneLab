package io.github.pigerzhu.onelab.diagnostics;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_ALLOWED_PACKAGES;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_DIAGNOSTICS;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;

/** Reconstructs every package gate used by Samsung's App split view settings page. */
final class SplitViewDiagnosticReport {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String QQ_PACKAGE = "com.tencent.mobileqq";
    private static final String EMBEDDING_OVERRIDE_PROPERTY =
            "android.window.PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE";
    private static final String PACKAGE_POLICY_DISABLED = "package_policy_disabled";

    private SplitViewDiagnosticReport() {
    }

    static String build(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String rawSnapshot = Settings.Global.getString(
                context.getContentResolver(), KEY_SPLIT_VIEW_ALLOWED_PACKAGES);
        List<String> allowedPackages = parsePackages(rawSnapshot);
        String systemServerDiagnostics = Settings.Global.getString(
                context.getContentResolver(), KEY_SPLIT_VIEW_DIAGNOSTICS);
        Map<String, String> sourceFields = parseSourceFields(systemServerDiagnostics);
        List<ApplicationInfo> installedApplications =
                packageManager.getInstalledApplications(0);
        Set<String> installedPackages = packageNames(installedApplications);
        Set<String> declaredEmbeddingPackages = embeddingOverridePackages(
                packageManager, installedApplications);
        Set<String> legacyPackages = Boolean.parseBoolean(
                sourceFields.getOrDefault("split_binder_seen", "false"))
                ? parsePackageSet(sourceFields.get("split_binder_packages"))
                : parsePackageSet(sourceFields.get("legacy_packages"));
        Set<String> embedPackages = Boolean.parseBoolean(
                sourceFields.getOrDefault("embed_binder_seen", "false"))
                ? parsePackageSet(sourceFields.get("embed_binder_packages"))
                : parsePackageSet(sourceFields.get("embed_packages"));
        Set<String> splitStateTwoPackages = parsePackageSet(
                sourceFields.get("split_state_2_packages"));
        Map<String, Integer> splitPackageStates = parsePackageStates(
                sourceFields.get("split_package_states"));
        boolean splitStateQueriesComplete = Boolean.parseBoolean(
                sourceFields.getOrDefault("split_state_query_complete", "false"));
        Set<String> filteredLegacyPackages = new HashSet<>(legacyPackages);
        filteredLegacyPackages.removeAll(splitStateTwoPackages);
        Set<String> expectedSamsungPackages = new HashSet<>(filteredLegacyPackages);
        expectedSamsungPackages.addAll(embedPackages);
        expectedSamsungPackages.retainAll(installedPackages);
        Set<String> declaredButMissingFromEmbed = new HashSet<>(declaredEmbeddingPackages);
        declaredButMissingFromEmbed.removeAll(embedPackages);

        boolean installed = false;
        boolean enabled = false;
        boolean system = false;
        int enabledSetting = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        String version = "not_visible";
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(WECHAT_PACKAGE, 0);
            installed = true;
            enabled = info.enabled;
            system = (info.flags
                    & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            try {
                enabledSetting = packageManager.getApplicationEnabledSetting(WECHAT_PACKAGE);
            } catch (IllegalArgumentException ignored) {
                enabledSetting = -1;
            }
            PackageInfo packageInfo = packageManager.getPackageInfo(WECHAT_PACKAGE, 0);
            version = packageInfo.versionName + " (" + packageInfo.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep the visibility evidence explicit in the report.
        }

        boolean enumerated = false;
        for (ApplicationInfo info : installedApplications) {
            if (info != null && WECHAT_PACKAGE.equals(info.packageName)) {
                enumerated = true;
                break;
            }
        }
        Intent launchIntent = packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE);
        ComponentName launchComponent = launchIntent == null
                ? null
                : launchIntent.getComponent();
        boolean listEligible = enumerated && !system && launchIntent != null;
        String embeddingOverrideProperty = embeddingOverrideProperty(packageManager, installed);

        Map<String, Float> ratios = SplitViewRatioOverrides.parse(
                Settings.Global.getString(
                        context.getContentResolver(), KEY_SPLIT_VIEW_RATIO_OVERRIDES));
        Float wechatRatio = ratios.get(WECHAT_PACKAGE);

        StringBuilder output = new StringBuilder();
        output.append("target_package=").append(WECHAT_PACKAGE).append('\n');
        output.append("app_process_user_id=").append(Process.myUid() / 100000).append('\n');
        output.append("wechat_version=").append(version).append('\n');
        output.append("wechat_installed_visible=").append(installed).append('\n');
        output.append("wechat_enabled=").append(enabled).append('\n');
        output.append("wechat_enabled_setting=").append(enabledSetting).append('\n');
        output.append("wechat_enumerated_by_app_scan=").append(enumerated).append('\n');
        output.append("wechat_system_app=").append(system).append('\n');
        output.append("wechat_launchable=").append(launchIntent != null).append('\n');
        output.append("wechat_launch_component=")
                .append(launchComponent == null ? "none" : launchComponent.flattenToShortString())
                .append('\n');
        output.append("wechat_app_list_eligible=").append(listEligible).append('\n');
        output.append("wechat_embedding_override_property=")
                .append(embeddingOverrideProperty).append('\n');
        output.append("wechat_in_split_snapshot=")
                .append(allowedPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("wechat_saved_ratio=")
                .append(wechatRatio == null ? "default" : wechatRatio).append('\n');
        output.append("split_snapshot_count=").append(allowedPackages.size()).append('\n');
        output.append("split_snapshot_raw_length=")
                .append(rawSnapshot == null ? 0 : rawSnapshot.length()).append('\n');
        output.append("system_server_diagnostics=")
                .append(systemServerDiagnostics == null ? "unset" : "available")
                .append('\n');
        output.append("package_policy_disabled=")
                .append(Settings.Global.getInt(
                        context.getContentResolver(), PACKAGE_POLICY_DISABLED, -1))
                .append('\n');
        output.append("installed_application_count=")
                .append(installedApplications.size()).append('\n');
        output.append("legacy_installed_intersection_count=")
                .append(intersectionSize(legacyPackages, installedPackages)).append('\n');
        output.append("embed_installed_intersection_count=")
                .append(intersectionSize(embedPackages, installedPackages)).append('\n');
        output.append("samsung_expected_visible_count=")
                .append(expectedSamsungPackages.size()).append('\n');
        output.append("samsung_expected_contains_wechat=")
                .append(expectedSamsungPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("embedding_property_declared_installed_count=")
                .append(declaredEmbeddingPackages.size()).append('\n');
        output.append("embedding_declared_but_repository_missing_count=")
                .append(declaredButMissingFromEmbed.size()).append('\n');

        output.append("\n[system_server_sources]\n");
        if (systemServerDiagnostics == null || systemServerDiagnostics.isEmpty()) {
            output.append("status=unset\n");
        } else {
            for (String item : systemServerDiagnostics.split("\\|")) {
                output.append(item).append('\n');
            }
        }

        output.append("\n[diagnostic_assessment]\n");
        appendAssessment(
                output,
                sourceFields,
                installedPackages,
                legacyPackages,
                embedPackages,
                expectedSamsungPackages,
                declaredEmbeddingPackages,
                declaredButMissingFromEmbed);

        appendPackageSection(
                output,
                "installed_legacy_candidates",
                intersection(legacyPackages, installedPackages));
        appendPackageSection(
                output,
                "installed_legacy_after_state_filter",
                intersection(filteredLegacyPackages, installedPackages));
        appendPackageSection(
                output,
                "installed_legacy_filtered_by_state_2",
                intersection(splitStateTwoPackages, installedPackages));
        appendPackageSection(
                output,
                "installed_embedding_candidates",
                intersection(embedPackages, installedPackages));
        appendPackageSection(
                output,
                "samsung_expected_visible_packages",
                expectedSamsungPackages);
        appendPackageSection(
                output,
                "installed_embedding_property_packages",
                declaredEmbeddingPackages);
        appendPackageSection(
                output,
                "embedding_declared_but_repository_missing",
                declaredButMissingFromEmbed);
        appendCandidateDecisions(
                output,
                installedPackages,
                legacyPackages,
                embedPackages,
                declaredEmbeddingPackages,
                splitPackageStates,
                splitStateQueriesComplete);

        output.append("\n[split_snapshot_packages]\n");
        if (allowedPackages.isEmpty()) {
            output.append("status=empty\n");
        } else {
            for (String packageName : allowedPackages) {
                output.append(packageName).append('\n');
            }
        }
        return output.toString();
    }

    private static void appendAssessment(
            StringBuilder output,
            Map<String, String> fields,
            Set<String> installedPackages,
            Set<String> legacyPackages,
            Set<String> embedPackages,
            Set<String> expectedSamsungPackages,
            Set<String> declaredEmbeddingPackages,
            Set<String> declaredButMissingFromEmbed
    ) {
        if (fields.isEmpty()) {
            output.append("status=incomplete\n");
            output.append("reason=system_server_diagnostics_unavailable\n");
            output.append("action=reboot_after_install_and_verify_android_scope\n");
            return;
        }

        boolean policyDisabled = "1".equals(fields.get("package_policy_disabled_current"))
                || "POLICY_DISABLED".equals(fields.get("foldable_policy_source"));
        boolean packageFeatureObserved = Boolean.parseBoolean(
                fields.getOrDefault("package_feature_observed", "false"));
        int policyVersion = parseInt(fields.get("foldable_policy_version"), -1);
        int rawPolicyCount = parseInt(fields.get("split_activity_policy_raw_count"), -1);
        boolean stateQueriesComplete = Boolean.parseBoolean(
                fields.getOrDefault("split_state_query_complete", "false"));

        output.append("status=")
                .append(stateQueriesComplete ? "complete" : "incomplete")
                .append('\n');
        if (policyDisabled) {
            output.append("primary_finding=package_policy_disabled\n");
        } else if (!packageFeatureObserved) {
            output.append("primary_finding=foldable_package_policy_not_observed\n");
        } else if (policyVersion <= 0 || rawPolicyCount <= 0) {
            output.append("primary_finding=foldable_package_policy_empty_or_uninitialized\n");
        } else if (!stateQueriesComplete) {
            output.append("primary_finding=open_samsung_app_split_view_during_recording\n");
        } else if (expectedSamsungPackages.size() <= 4) {
            output.append("primary_finding=system_sources_resolve_to_only_")
                    .append(expectedSamsungPackages.size()).append("_installed_packages\n");
        } else {
            output.append("primary_finding=system_sources_expect_")
                    .append(expectedSamsungPackages.size()).append("_visible_packages\n");
        }

        output.append("wechat_installed=")
                .append(installedPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("wechat_in_legacy=")
                .append(legacyPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("wechat_in_embed=")
                .append(embedPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("wechat_declares_embedding_property=")
                .append(declaredEmbeddingPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("wechat_expected_visible=")
                .append(expectedSamsungPackages.contains(WECHAT_PACKAGE)).append('\n');
        output.append("embedding_repository_registration_gaps=")
                .append(declaredButMissingFromEmbed.size()).append('\n');
    }

    private static Map<String, String> parseSourceFields(String raw) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return fields;
        for (String item : raw.split("\\|")) {
            int separator = item.indexOf('=');
            if (separator <= 0) continue;
            fields.put(item.substring(0, separator), item.substring(separator + 1));
        }
        return fields;
    }

    private static Map<String, Integer> parsePackageStates(String raw) {
        Map<String, Integer> states = new HashMap<>();
        if (raw == null || raw.isEmpty()) return states;
        for (String item : raw.split(",")) {
            int separator = item.lastIndexOf(':');
            if (separator <= 0) continue;
            String packageName = item.substring(0, separator).trim();
            int state = parseInt(item.substring(separator + 1).trim(), Integer.MIN_VALUE);
            if (!packageName.isEmpty() && state != Integer.MIN_VALUE) {
                states.put(packageName, state);
            }
        }
        return states;
    }

    private static Set<String> packageNames(List<ApplicationInfo> applications) {
        Set<String> packages = new HashSet<>();
        for (ApplicationInfo info : applications) {
            if (info != null && info.packageName != null && !info.packageName.isEmpty()) {
                packages.add(info.packageName);
            }
        }
        return packages;
    }

    private static Set<String> embeddingOverridePackages(
            PackageManager packageManager,
            List<ApplicationInfo> applications
    ) {
        Set<String> packages = new HashSet<>();
        for (ApplicationInfo info : applications) {
            if (info == null || info.packageName == null) continue;
            try {
                if (packageManager.getProperty(
                        EMBEDDING_OVERRIDE_PROPERTY, info.packageName) != null) {
                    packages.add(info.packageName);
                }
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // A missing property is expected for most installed applications.
            }
        }
        return packages;
    }

    private static Set<String> parsePackageSet(String raw) {
        return new LinkedHashSet<>(parsePackages(raw));
    }

    private static Set<String> intersection(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.retainAll(second);
        return result;
    }

    private static int intersectionSize(Set<String> first, Set<String> second) {
        return intersection(first, second).size();
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void appendPackageSection(
            StringBuilder output,
            String name,
            Set<String> packages
    ) {
        output.append('\n').append('[').append(name).append("]\n");
        if (packages.isEmpty()) {
            output.append("status=empty\n");
            return;
        }
        List<String> sorted = new ArrayList<>(packages);
        Collections.sort(sorted);
        for (String packageName : sorted) output.append(packageName).append('\n');
    }

    private static void appendCandidateDecisions(
            StringBuilder output,
            Set<String> installedPackages,
            Set<String> legacyPackages,
            Set<String> embedPackages,
            Set<String> declaredEmbeddingPackages,
            Map<String, Integer> splitPackageStates,
            boolean stateQueriesComplete
    ) {
        Set<String> candidates = new HashSet<>();
        candidates.addAll(intersection(legacyPackages, installedPackages));
        candidates.addAll(intersection(embedPackages, installedPackages));
        candidates.addAll(declaredEmbeddingPackages);
        if (installedPackages.contains(WECHAT_PACKAGE)) candidates.add(WECHAT_PACKAGE);
        if (installedPackages.contains(QQ_PACKAGE)) candidates.add(QQ_PACKAGE);

        output.append("\n[candidate_decisions]\n");
        if (candidates.isEmpty()) {
            output.append("status=empty\n");
            return;
        }
        List<String> sorted = new ArrayList<>(candidates);
        Collections.sort(sorted);
        for (String packageName : sorted) {
            boolean legacy = legacyPackages.contains(packageName);
            boolean embed = embedPackages.contains(packageName);
            Integer state = splitPackageStates.get(packageName);
            String expectedVisible;
            if (embed) {
                expectedVisible = "true";
            } else if (!legacy) {
                expectedVisible = "false";
            } else if (state != null) {
                expectedVisible = String.valueOf(state != 2);
            } else {
                expectedVisible = stateQueriesComplete ? "false" : "unknown";
            }
            output.append(packageName)
                    .append(" installed=true")
                    .append(" legacy=").append(legacy)
                    .append(" legacy_state=").append(state == null ? "unobserved" : state)
                    .append(" embed=").append(embed)
                    .append(" declares_embedding=")
                    .append(declaredEmbeddingPackages.contains(packageName))
                    .append(" expected_visible=").append(expectedVisible)
                    .append('\n');
        }
    }

    private static String embeddingOverrideProperty(
            PackageManager packageManager,
            boolean installed
    ) {
        if (!installed) return "package_not_visible";
        try {
            PackageManager.Property property = packageManager.getProperty(
                    EMBEDDING_OVERRIDE_PROPERTY, WECHAT_PACKAGE);
            return property == null ? "missing" : "present";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "missing";
        } catch (RuntimeException exception) {
            return "error:" + exception.getClass().getSimpleName();
        }
    }

    private static List<String> parsePackages(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> packages = new ArrayList<>();
        for (String item : raw.split(",")) {
            String packageName = item.trim();
            if (!packageName.isEmpty() && !packages.contains(packageName)) {
                packages.add(packageName);
            }
        }
        Collections.sort(packages);
        return packages;
    }
}
