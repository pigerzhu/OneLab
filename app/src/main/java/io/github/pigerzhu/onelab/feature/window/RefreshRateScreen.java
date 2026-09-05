package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.navigation.AppListPage;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_OVERRIDES;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.pigerzhu.onelab.contract.RefreshRateOverride;
import io.github.pigerzhu.onelab.contract.RefreshRateOverrides;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.system.SettingFeedbackPolicy;
import io.github.pigerzhu.onelab.ui.ChoiceGroup;
import io.github.pigerzhu.onelab.ui.Ui;

/** UI and persistence for the per-app system_server refresh-rate hook. */
public final class RefreshRateScreen {

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final AppListPage appList;

    public RefreshRateScreen(MainActivity host, Ui ui, SettingsStore settings, AppListPage appList) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
        this.appList = appList;
    }

    public View entryCard() {
        MaterialCardView card = ui.card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showPage());

        LinearLayout body = ui.cardBody();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(body);

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        body.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(host.getString(R.string.refresh_rate_title), 20, true,
                ui.colorOnSurface));

        TextView arrow = ui.text(">", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    void showPage() {
        host.setNestedBackAction(() -> host.showSystemUiPage(true));
        Map<String, RefreshRateOverride> overrides = refreshOverrides();
        appList.show(
                "",
                "",
                this::status,
                this::showSinglePolicyEditor,
                app -> overrides.containsKey(app.packageName),
                new AppListPage.BatchAction() {
                    @Override
                    public String actionText(int selectedCount) {
                        return selectedCount == 0
                                ? host.getString(R.string.app_picker_select)
                                : host.getResources().getQuantityString(
                                        R.plurals.refresh_rate_batch_action,
                                        selectedCount, selectedCount);
                    }

                    @Override
                    public void onAppsSelected(List<AppListPage.AppEntry> apps, Runnable refreshList) {
                        showBatchPolicyEditor(apps, refreshList);
                    }
                }
        );
    }

    private String status(AppListPage.AppEntry app) {
        RefreshRateOverride override = refreshOverrides().get(app.packageName);
        if (override == null) return host.getString(R.string.status_default);
        if (override.mode == RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS) {
            return host.getString(R.string.refresh_rate_status_adaptive);
        }
        if (override.mode == RefreshRateOverrides.MODE_FIXED) {
            return override.min < 0f
                    ? host.getString(R.string.refresh_rate_status_fixed_max)
                    : String.format(Locale.US,
                            host.getString(R.string.refresh_rate_status_fixed), override.min);
        }
        return String.format(Locale.US,
                host.getString(R.string.refresh_rate_status_range), override.min, override.max);
    }

    private void showSinglePolicyEditor(AppListPage.AppEntry app, Runnable refreshRow) {
        RefreshRateOverride current = refreshOverrides().get(app.packageName);
        List<AppListPage.AppEntry> apps = new ArrayList<>();
        apps.add(app);
        showPolicyEditor(app.label, apps, current, false, refreshRow);
    }

    private void showBatchPolicyEditor(List<AppListPage.AppEntry> apps, Runnable refreshList) {
        // A batch operation is normally used to unlock several apps while keeping adaptive VRR.
        showPolicyEditor(host.getResources().getQuantityString(
                        R.plurals.refresh_rate_batch_action, apps.size(), apps.size()), apps,
                new RefreshRateOverride(RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS, 0f, 0f), true, refreshList);
    }

    private void showPolicyEditor(
            String title,
            List<AppListPage.AppEntry> apps,
            RefreshRateOverride current,
            boolean batch,
            Runnable refreshList
    ) {
        LinearLayout content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);

        LinearLayout unlockRow = new LinearLayout(host);
        unlockRow.setGravity(Gravity.CENTER_VERTICAL);
        unlockRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout unlockCopy = new LinearLayout(host);
        unlockCopy.setOrientation(LinearLayout.VERTICAL);
        unlockCopy.addView(ui.text(host.getString(R.string.refresh_rate_unlock), 18, true,
                ui.colorOnSurface));
        unlockCopy.addView(ui.text(host.getString(R.string.refresh_rate_unlock_summary), 13,
                false, ui.colorOnSurfaceVariant));
        unlockRow.addView(unlockCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        MaterialSwitch unlock = new MaterialSwitch(host);
        unlock.setChecked(current != null);
        unlockRow.addView(unlock);
        content.addView(unlockRow, ui.matchWrap());

        ui.addSpace(content, 18);
        TextView behaviorTitle = ui.text(host.getString(R.string.refresh_rate_behavior), 16,
                true, ui.colorOnSurface);
        content.addView(behaviorTitle);

        ChoiceGroup behavior = new ChoiceGroup(host, ui);
        behavior.addOption(host.getString(R.string.refresh_rate_mode_adaptive),
                host.getString(R.string.refresh_rate_mode_adaptive_summary),
                RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS);
        behavior.addOption(host.getString(R.string.refresh_rate_mode_fixed),
                host.getString(R.string.refresh_rate_mode_fixed_summary), RefreshRateOverrides.MODE_FIXED);
        behavior.addOption(host.getString(R.string.refresh_rate_mode_range),
                host.getString(R.string.refresh_rate_mode_range_summary), RefreshRateOverrides.MODE_RANGE);
        content.addView(behavior, ui.matchWrap());

        EditText fixedRate = rateInput(current != null && current.mode == RefreshRateOverrides.MODE_FIXED && current.min > 0f
                ? current.min : 60f);
        LinearLayout fixedRow = labeledRateRow(host.getString(R.string.refresh_rate_fixed_at), fixedRate, "Hz");
        content.addView(fixedRow, ui.matchWrap());

        EditText minRate = rateInput(current != null && current.mode == RefreshRateOverrides.MODE_RANGE ? current.min : 10f);
        EditText maxRate = rateInput(current != null && current.mode == RefreshRateOverrides.MODE_RANGE ? current.max : 120f);
        LinearLayout rangeRow = new LinearLayout(host);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeRow.addView(minRate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView to = ui.text(host.getString(R.string.range_separator), 16, false,
                ui.colorOnSurfaceVariant);
        to.setGravity(Gravity.CENTER);
        rangeRow.addView(to, new LinearLayout.LayoutParams(ui.dp(42), ViewGroup.LayoutParams.WRAP_CONTENT));
        rangeRow.addView(maxRate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView hz = ui.text("Hz", 15, false, ui.colorOnSurfaceVariant);
        hz.setPadding(ui.dp(10), 0, 0, 0);
        rangeRow.addView(hz);
        content.addView(rangeRow, ui.matchWrap());

        if (current == null || current.mode == RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS) {
            behavior.setValue(RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS);
        } else if (current.mode == RefreshRateOverrides.MODE_FIXED) {
            behavior.setValue(RefreshRateOverrides.MODE_FIXED);
        } else {
            behavior.setValue(RefreshRateOverrides.MODE_RANGE);
        }

        Runnable updateEditor = () -> {
            boolean enabled = unlock.isChecked();
            behaviorTitle.setEnabled(enabled);
            behavior.setEnabled(enabled);
            fixedRate.setEnabled(enabled && behavior.value() == RefreshRateOverrides.MODE_FIXED);
            minRate.setEnabled(enabled && behavior.value() == RefreshRateOverrides.MODE_RANGE);
            maxRate.setEnabled(enabled && behavior.value() == RefreshRateOverrides.MODE_RANGE);
            fixedRow.setAlpha(enabled && behavior.value() == RefreshRateOverrides.MODE_FIXED ? 1f : 0.45f);
            rangeRow.setAlpha(enabled && behavior.value() == RefreshRateOverrides.MODE_RANGE ? 1f : 0.45f);
        };
        unlock.setOnCheckedChangeListener((button, checked) -> updateEditor.run());
        behavior.setOnChoiceChangedListener(value -> updateEditor.run());
        updateEditor.run();

        String message = batch
                ? host.getString(R.string.refresh_rate_batch_message)
                : host.getString(R.string.refresh_rate_single_message);
        ScrollView scrollView = new ScrollView(host);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(title)
                .setMessage(message)
                .setView(scrollView)
                .setNeutralButton(R.string.action_reset_default, null)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                applyPolicy(apps, null, refreshList);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                RefreshRateOverride next;
                if (!unlock.isChecked()) {
                    next = null;
                } else if (behavior.value() == RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS) {
                    next = new RefreshRateOverride(RefreshRateOverrides.MODE_HIGH_REFRESH_BYPASS, 0f, 0f);
                } else if (behavior.value() == RefreshRateOverrides.MODE_FIXED) {
                    Float value = parseRate(fixedRate.getText().toString());
                    if (value == null) {
                        Toast.makeText(host, R.string.refresh_rate_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    next = new RefreshRateOverride(RefreshRateOverrides.MODE_FIXED, value, value);
                } else {
                    Float min = parseRate(minRate.getText().toString());
                    Float max = parseRate(maxRate.getText().toString());
                    if (min == null || max == null || min > max) {
                        Toast.makeText(host, R.string.refresh_rate_invalid_range,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    next = new RefreshRateOverride(RefreshRateOverrides.MODE_RANGE, min, max);
                }
                applyPolicy(apps, next, refreshList);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private LinearLayout labeledRateRow(String label, EditText input, String suffix) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView prefix = ui.text(label, 15, false, ui.colorOnSurfaceVariant);
        row.addView(prefix, new LinearLayout.LayoutParams(ui.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView unit = ui.text(suffix, 15, false, ui.colorOnSurfaceVariant);
        unit.setPadding(ui.dp(10), 0, 0, 0);
        row.addView(unit);
        return row;
    }

    private EditText rateInput(float value) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.0f", value));
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setMinHeight(ui.dp(54));
        return input;
    }

    private Float parseRate(String raw) {
        try {
            float value = Float.parseFloat(raw.trim());
            return value >= 1f && value <= 240f ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, RefreshRateOverride> refreshOverrides() {
        return editableOverrides(settings.getGlobal(KEY_REFRESH_RATE_OVERRIDES, ""));
    }

    static Map<String, RefreshRateOverride> editableOverrides(String raw) {
        return new LinkedHashMap<>(RefreshRateOverrides.parse(raw));
    }

    private void applyPolicy(
            List<AppListPage.AppEntry> apps,
            RefreshRateOverride override,
            Runnable refreshList
    ) {
        Map<String, RefreshRateOverride> map = refreshOverrides();
        for (AppListPage.AppEntry app : apps) {
            if (override == null) {
                map.remove(app.packageName);
            } else {
                map.put(app.packageName, override);
            }
        }
        settings.putGlobalQuietlyAsync(
                KEY_REFRESH_RATE_OVERRIDES,
                RefreshRateOverrides.serialize(map), saved -> {
                    if (host.isFinishing() || host.isDestroyed()) return;
                    int message = SettingFeedbackPolicy.messageFor(
                            saved,
                            override == null
                                    ? SettingFeedbackPolicy.SuccessNotice.NONE
                                    : SettingFeedbackPolicy.SuccessNotice.REOPEN_APP,
                            R.string.toast_save_failed_permission);
                    if (message != 0) {
                        Toast.makeText(host, message,
                                saved ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                    }
                    if (saved) refreshList.run();
                });
    }
}
