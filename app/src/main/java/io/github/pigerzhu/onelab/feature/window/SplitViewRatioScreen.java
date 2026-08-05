package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.R;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_SPLIT_VIEW_RATIO_OVERRIDES;

import android.app.AlertDialog;
import android.text.InputType;
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
        copy.addView(ui.text(host.getString(R.string.split_ratio_title), 20, true,
                ui.colorOnSurface));

        TextView arrow = ui.text(">", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    private void loadAndShowPage() {
        new Thread(() -> {
            Set<String> allowed = splitViewClient.allowedPackages();
            host.runOnUiThread(() -> {
                if (allowed.isEmpty()) {
                    Toast.makeText(host, R.string.split_ratio_list_not_synced,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                showPage(allowed);
            });
        }, "OneLab-SplitViewApps").start();
    }

    private void showPage(Set<String> allowedPackages) {
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
                                ? host.getString(R.string.app_picker_select)
                                : host.getResources().getQuantityString(
                                        R.plurals.split_ratio_batch_action,
                                        selectedCount, selectedCount);
                    }

                    @Override
                    public void onAppsSelected(
                            List<AppListPage.AppEntry> apps,
                            Runnable refreshList
                    ) {
                        showEditor(host.getResources().getQuantityString(
                                        R.plurals.split_ratio_batch_action,
                                        apps.size(), apps.size()),
                                apps, null, refreshList);
                    }
                },
                app -> allowedPackages.contains(app.packageName),
                this::showHelp
        );
    }

    private void showHelp() {
        new AlertDialog.Builder(host)
                .setTitle(R.string.split_ratio_help_title)
                .setMessage(R.string.split_ratio_help_message)
                .setPositiveButton(R.string.action_got_it, null)
                .show();
    }

    private String status(AppListPage.AppEntry app) {
        Float ratio = ratioOverrides().get(app.packageName);
        if (ratio == null) return host.getString(R.string.split_ratio_status_default);
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
                .setMessage(R.string.split_ratio_editor_message)
                .setView(content)
                .setNeutralButton(R.string.action_reset_default, null)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
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
                    Toast.makeText(host, R.string.split_ratio_invalid,
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
                        ? R.string.split_ratio_reset_done
                        : R.string.toast_saved_reopen_app
                        : R.string.toast_save_failed_permission,
                saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
        if (saved) refreshList.run();
    }
}
