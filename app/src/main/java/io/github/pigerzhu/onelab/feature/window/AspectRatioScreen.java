package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.navigation.AppListPage;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ASPECT_RATIO_OVERRIDES;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AspectRatioScreen {

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private final AppListPage appList;

    public AspectRatioScreen(MainActivity host, Ui ui, SettingsStore settings, AppListPage appList) {
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
        copy.addView(ui.text(host.getString(R.string.aspect_ratio_title), 20, true,
                ui.colorOnSurface));

        TextView arrow = ui.text(">", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    void showPage() {
        host.setNestedBackAction(() -> host.showSystemUiPage(true));
        Map<String, AspectOverride> overrides = aspectRatioOverrides();
        appList.show(
                "",
                "",
                this::aspectRatioStatus,
                this::showAspectRatioDialog,
                app -> overrides.containsKey(app.packageName),
                new AppListPage.BatchAction() {
                    @Override
                    public String actionText(int selectedCount) {
                        return selectedCount == 0
                                ? host.getString(R.string.app_picker_select)
                                : host.getResources().getQuantityString(
                                        R.plurals.aspect_ratio_batch_action,
                                        selectedCount, selectedCount);
                    }

                    @Override
                    public void onAppsSelected(List<AppListPage.AppEntry> apps, Runnable refreshList) {
                        showBatchAspectRatioDialog(apps, refreshList);
                    }
                }
        );
    }

    private String aspectRatioStatus(AppListPage.AppEntry app) {
        AspectOverride override = aspectRatioOverrides().get(app.packageName);
        if (override == null) {
            return host.getString(R.string.status_default);
        }
        return String.format(Locale.US, "%.2f:1", override.ratio)
                + (override.innerOnly
                        ? host.getString(R.string.aspect_ratio_status_inner_suffix) : "");
    }

    private void showAspectRatioDialog(AppListPage.AppEntry app, Runnable refreshRow) {
        AspectOverride current = aspectRatioOverrides().get(app.packageName);

        EditText width = ratioInput("16");
        EditText height = ratioInput("9");

        LinearLayout ratioRow = new LinearLayout(host);
        ratioRow.setOrientation(LinearLayout.HORIZONTAL);
        ratioRow.setGravity(Gravity.CENTER_VERTICAL);
        ratioRow.addView(width, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView colon = ui.text(":", 22, true, ui.colorOnSurface);
        colon.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        ratioRow.addView(colon);
        ratioRow.addView(height, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        CheckBox innerOnly = new CheckBox(host);
        innerOnly.setText(R.string.aspect_ratio_inner_only);
        innerOnly.setTextColor(ui.colorOnSurface);
        innerOnly.setChecked(current == null || current.innerOnly);

        LinearLayout container = new LinearLayout(host);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(ui.dp(22), ui.dp(10), ui.dp(22), 0);
        container.addView(ratioRow, ui.matchWrap());
        ui.addSpace(container, 6);
        container.addView(innerOnly, ui.matchWrap());

        new AlertDialog.Builder(host)
                .setTitle(app.label)
                .setMessage(R.string.aspect_ratio_dialog_message)
                .setView(container)
                .setNeutralButton(R.string.action_clear, (dialog, which) -> {
                    setAspectRatioOverride(app.packageName, null, true);
                    refreshRow.run();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    Integer first = parsePositiveInt(width.getText().toString());
                    Integer second = parsePositiveInt(height.getText().toString());
                    if (first == null || second == null) {
                        Toast.makeText(host, R.string.aspect_ratio_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float ratio = (float) Math.max(first, second) / Math.min(first, second);
                    setAspectRatioOverride(app.packageName, ratio, innerOnly.isChecked());
                    refreshRow.run();
                })
                .show();
    }

    private void showBatchAspectRatioDialog(List<AppListPage.AppEntry> apps, Runnable refreshList) {
        EditText width = ratioInput("16");
        EditText height = ratioInput("9");

        LinearLayout ratioRow = new LinearLayout(host);
        ratioRow.setOrientation(LinearLayout.HORIZONTAL);
        ratioRow.setGravity(Gravity.CENTER_VERTICAL);
        ratioRow.addView(width, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView colon = ui.text(":", 22, true, ui.colorOnSurface);
        colon.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        ratioRow.addView(colon);
        ratioRow.addView(height, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        CheckBox innerOnly = new CheckBox(host);
        innerOnly.setText(R.string.aspect_ratio_inner_only);
        innerOnly.setTextColor(ui.colorOnSurface);
        innerOnly.setChecked(true);

        LinearLayout container = new LinearLayout(host);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(ui.dp(22), ui.dp(10), ui.dp(22), 0);
        container.addView(ratioRow, ui.matchWrap());
        ui.addSpace(container, 6);
        container.addView(innerOnly, ui.matchWrap());

        new AlertDialog.Builder(host)
                .setTitle(host.getResources().getQuantityString(
                        R.plurals.aspect_ratio_batch_action, apps.size(), apps.size()))
                .setMessage(R.string.aspect_ratio_batch_message)
                .setView(container)
                .setNeutralButton(R.string.action_clear, (dialog, which) -> {
                    Map<String, AspectOverride> overrides = aspectRatioOverrides();
                    for (AppListPage.AppEntry app : apps) {
                        overrides.remove(app.packageName);
                    }
                    saveAspectRatioOverrides(overrides);
                    Toast.makeText(host, R.string.toast_reset_to_default,
                            Toast.LENGTH_SHORT).show();
                    refreshList.run();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_apply, (dialog, which) -> {
                    Integer first = parsePositiveInt(width.getText().toString());
                    Integer second = parsePositiveInt(height.getText().toString());
                    if (first == null || second == null) {
                        Toast.makeText(host, R.string.aspect_ratio_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float ratio = (float) Math.max(first, second) / Math.min(first, second);
                    Map<String, AspectOverride> overrides = aspectRatioOverrides();
                    for (AppListPage.AppEntry app : apps) {
                        overrides.put(app.packageName, new AspectOverride(ratio, innerOnly.isChecked()));
                    }
                    saveAspectRatioOverrides(overrides);
                    Toast.makeText(host, R.string.toast_saved, Toast.LENGTH_SHORT).show();
                    refreshList.run();
                })
                .show();
    }

    private EditText ratioInput(String hint) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(hint);
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setMinHeight(ui.dp(58));
        return input;
    }

    private Integer parsePositiveInt(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    // Overrides are stored in Settings.Global as pkg:ratio:inner;pkg:ratio:inner, where inner is
    // 1 (apply only on the inner/main display) or 0 (all displays). AspectRatioHook parses the same
    // format. Package names never contain ':', so a plain split is safe.
    private Map<String, AspectOverride> aspectRatioOverrides() {
        Map<String, AspectOverride> map = new LinkedHashMap<>();
        String raw = settings.getGlobal(KEY_ASPECT_RATIO_OVERRIDES, "");
        if (raw.isEmpty()) {
            return map;
        }
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
                    map.put(packageName, new AspectOverride(ratio, innerOnly));
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed entries so the rest of the map stays usable.
            }
        }
        return map;
    }

    private void setAspectRatioOverride(String packageName, Float ratio, boolean innerOnly) {
        Map<String, AspectOverride> map = aspectRatioOverrides();
        if (ratio == null) {
            map.remove(packageName);
        } else {
            map.put(packageName, new AspectOverride(ratio, innerOnly));
        }
        saveAspectRatioOverrides(map);
        Toast.makeText(host, ratio == null
                ? R.string.toast_reset_to_default : R.string.toast_saved,
                Toast.LENGTH_SHORT).show();
    }

    private void saveAspectRatioOverrides(Map<String, AspectOverride> map) {
        StringBuilder value = new StringBuilder();
        for (Map.Entry<String, AspectOverride> entry : map.entrySet()) {
            if (value.length() > 0) {
                value.append(';');
            }
            value.append(entry.getKey()).append(':')
                    .append(String.format(Locale.US, "%.4f", entry.getValue().ratio))
                    .append(':').append(entry.getValue().innerOnly ? '1' : '0');
        }
        settings.putGlobalQuietly(KEY_ASPECT_RATIO_OVERRIDES, value.toString());
    }

    private static final class AspectOverride {
        final float ratio;
        final boolean innerOnly;

        AspectOverride(float ratio, boolean innerOnly) {
            this.ratio = ratio;
            this.innerOnly = innerOnly;
        }
    }
}
