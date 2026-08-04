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
import java.util.List;
import java.util.Map;

import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;

/** Captures the two independent gates that decide whether WeChat appears in the ratio list. */
final class SplitViewDiagnosticReport {
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    private SplitViewDiagnosticReport() {
    }

    static String build(Context context) {
        PackageManager packageManager = context.getPackageManager();
        String rawSnapshot = Settings.Global.getString(
                context.getContentResolver(), KEY_SPLIT_VIEW_ALLOWED_PACKAGES);
        List<String> allowedPackages = parsePackages(rawSnapshot);
        String systemServerDiagnostics = Settings.Global.getString(
                context.getContentResolver(), KEY_SPLIT_VIEW_DIAGNOSTICS);

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
        for (ApplicationInfo info : packageManager.getInstalledApplications(0)) {
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

        output.append("\n[system_server_sources]\n");
        if (systemServerDiagnostics == null || systemServerDiagnostics.isEmpty()) {
            output.append("status=unset\n");
        } else {
            for (String item : systemServerDiagnostics.split("\\|")) {
                output.append(item).append('\n');
            }
        }

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
