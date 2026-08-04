package io.github.pigerzhu.onelab.feature.window;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.contract.SplitViewRatioOverrides;
import io.github.pigerzhu.onelab.navigation.AppListPage;
import io.github.pigerzhu.onelab.system.SamsungSplitViewClient;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

/** Per-application fixed ratio controls for Samsung's ActivityRecordGroup split view. */
public final class SplitViewRatioScreen {
    private static final String TAG = "OneLab/SplitViewRatio";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final AppListPage appList;
    private final SamsungSplitViewClient splitViewClient;

    public SplitViewRatioScreen(
            MainActivity host,
            Ui ui,
            SettingsStore settings,
            AppListPage appList
    ) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
        this.appList = appList;
        this.splitViewClient = new SamsungSplitViewClient(settings);
    }

    public View entryCard() {
        MaterialCardView card = ui.card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> loadAndShowPage());

        LinearLayout body = ui.cardBody();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(body);

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        body.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text("应用分栏比例", 20, true, ui.colorOnSurface));

        TextView arrow = ui.text(">", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    private void loadAndShowPage() {
        new Thread(() -> {
            Set<String> allowed = splitViewClient.allowedPackages();
            logWechatListEligibility(allowed);
            host.runOnUiThread(() -> {
                if (allowed.isEmpty()) {
                    Toast.makeText(host, "尚未同步三星应用程序分屏视图列表，请重启设备后重试",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                showPage(allowed);
            });
        }, "OneLab-SplitViewApps").start();
    }

    private void logWechatListEligibility(Set<String> allowedPackages) {
        PackageManager packageManager = host.getPackageManager();
        boolean installed = false;
        boolean enabled = false;
        boolean system = false;
        boolean enumerated = false;
        boolean launchable = false;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(WECHAT_PACKAGE, 0);
            installed = true;
            enabled = info.enabled;
            system = (info.flags
                    & (ApplicationInfo.FLAG_SYSTEM
                    | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            // The report distinguishes an absent package from a package hidden from this user.
        }
        for (ApplicationInfo info : packageManager.getInstalledApplications(0)) {
            if (info != null && WECHAT_PACKAGE.equals(info.packageName)) {
                enumerated = true;
                break;
            }
        }
        Intent launchIntent = packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE);
        launchable = launchIntent != null;
        boolean listEligible = enumerated && !system && launchable;
        Log.i(TAG, "wechat allowed=" + allowedPackages.contains(WECHAT_PACKAGE)
                + " installed=" + installed
                + " enabled=" + enabled
                + " enumerated=" + enumerated
                + " system=" + system
                + " launchable=" + launchable
                + " list_eligible=" + listEligible);
    }

    private void showPage(Set<String> allowedPackages) {
        Log.i(TAG, "app cache initialized=" + appList.hasCachedApps()
                + " wechat=" + appList.cachedAppsContain(WECHAT_PACKAGE));
        host.setNestedBackAction(() -> host.showSystemUiPage(true));
        Map<String, Float> configured = ratioOverrides();
        appList.show(
                "",
                "",
                this::status,
                this::showSingleEditor,
                app -> configured.containsKey(app.packageName),
                new AppListPage.BatchAction() {
                    @Override
                    public String actionText(int selectedCount) {
                        return selectedCount == 0
                                ? "选择应用"
                                : "为 " + selectedCount + " 个应用设置比例";
                    }

                    @Override
                    public void onAppsSelected(
                            List<AppListPage.AppEntry> apps,
                            Runnable refreshList
                    ) {
                        showEditor("为 " + apps.size() + " 个应用设置比例",
                                apps, null, refreshList);
                    }
                },
                app -> allowedPackages.contains(app.packageName),
                this::showHelp
        );
    }

    private void showHelp() {
        new AlertDialog.Builder(host)
                .setTitle("比例设置不生效？")
                .setMessage("请先强制停止并重新打开目标应用。\n\n"
                        + "如果仍未生效，请将该应用加入 OneLab 的 LSPosed 作用域，"
                        + "再强制停止并重新打开应用。\n\n"
                        + "部分应用使用自定义分栏方式，可能仍不支持比例调整。"
                        + "支付、银行等敏感应用不建议加入作用域。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private String status(AppListPage.AppEntry app) {
        Float ratio = ratioOverrides().get(app.packageName);
        if (ratio == null) return "默认 · 50:50";
        return formatPercent(ratio * 100f) + ":" + formatPercent((1f - ratio) * 100f);
    }

    private void showSingleEditor(AppListPage.AppEntry app, Runnable refreshRow) {
        Float current = ratioOverrides().get(app.packageName);
        showEditor(app.label, java.util.Collections.singletonList(app), current, refreshRow);
    }

    private void showEditor(
            String title,
            List<AppListPage.AppEntry> apps,
            Float current,
            Runnable refreshList
    ) {
        float leftValue = current == null ? 50f : current * 100f;
        float rightValue = current == null ? 50f : (1f - current) * 100f;
        EditText left = ratioInput(leftValue, current != null);
        EditText right = ratioInput(rightValue, current != null);

        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView colon = ui.text(":", 22, true, ui.colorOnSurface);
        colon.setGravity(Gravity.CENTER);
        row.addView(colon, new LinearLayout.LayoutParams(
                ui.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(right, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);
        content.addView(row, ui.matchWrap());

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(title)
                .setMessage("输入左右栏比例。测试阶段不限制比例范围，重新打开应用后生效。")
                .setView(content)
                .setNeutralButton("恢复默认", null)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                saveRatio(apps, null, refreshList);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Float leftPart = parsePositive(left.getText().toString());
                Float rightPart = parsePositive(right.getText().toString());
                if (leftPart == null || rightPart == null
                        || !Float.isFinite(leftPart + rightPart)) {
                    Toast.makeText(host, "左右两边都需要输入大于 0 的数字",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                saveRatio(apps, leftPart / (leftPart + rightPart), refreshList);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private EditText ratioInput(float value, boolean hasSavedValue) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (hasSavedValue) {
            input.setText(formatPercent(value));
        } else {
            input.setHint(formatPercent(value));
        }
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setMinHeight(ui.dp(56));
        return input;
    }

    private Float parsePositive(String raw) {
        try {
            float value = Float.parseFloat(raw.trim());
            return value > 0f && Float.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatPercent(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private Map<String, Float> ratioOverrides() {
        return SplitViewRatioOverrides.parse(
                settings.getGlobal(KEY_SPLIT_VIEW_RATIO_OVERRIDES, ""));
    }

    private void saveRatio(
            List<AppListPage.AppEntry> apps,
            Float ratio,
            Runnable refreshList
    ) {
        Map<String, Float> values = new LinkedHashMap<>(ratioOverrides());
        for (AppListPage.AppEntry app : apps) {
            if (ratio == null) {
                values.remove(app.packageName);
            } else {
                values.put(app.packageName, ratio);
            }
        }
        boolean saved = settings.putGlobalQuietly(
                KEY_SPLIT_VIEW_RATIO_OVERRIDES,
                SplitViewRatioOverrides.serialize(values));
        Toast.makeText(host,
                saved
                        ? ratio == null
                        ? "已恢复默认比例"
                        : "已保存，重新打开应用后生效"
                        : "保存失败，请授予 WRITE_SECURE_SETTINGS 或 root",
                saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        if (saved) refreshList.run();
    }
}
