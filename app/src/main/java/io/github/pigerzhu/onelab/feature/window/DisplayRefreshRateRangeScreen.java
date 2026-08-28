package io.github.pigerzhu.onelab.feature.window;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_INNER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MIN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MIN;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.pigerzhu.onelab.hook.system.RefreshRateScreenRangePolicy;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

/**
 * Screen-level refresh-rate boundaries for the main and cover displays. This page is a
 * boundary for every application; per-app behavior stays in the refresh-rate policy page.
 */
public final class DisplayRefreshRateRangeScreen {
    private static final float DEFAULT_MIN = 48f;
    private static final float DEFAULT_MAX = 120f;

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public DisplayRefreshRateRangeScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
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
        copy.addView(ui.text(host.getString(R.string.refresh_rate_screen_range_title), 20, true,
                ui.colorOnSurface));
        copy.addView(ui.text(host.getString(R.string.refresh_rate_screen_range_summary), 14,
                false, ui.colorOnSurfaceVariant));

        TextView arrow = ui.text("›", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    private void showPage() {
        host.setNestedBackAction(() -> host.showSystemUiPage(true));
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.refresh_rate_screen_range_title),
                host.getString(R.string.refresh_rate_screen_range_summary), 1);
        root.addView(boundaryCard());
        root.addView(new Section(true).card());
        root.addView(new Section(false).card());
    }

    private View boundaryCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text(host.getString(R.string.refresh_rate_screen_range_boundary), 14,
                false, ui.colorOnSurfaceVariant));
        return card;
    }

    private final class Section {
        private final boolean inner;
        private final String enabledKey;
        private final String minKey;
        private final String maxKey;

        private final MaterialSwitch limitSwitch;
        private final EditText minInput;
        private final EditText maxInput;
        private final LinearLayout inputRows;
        private TextView switchSubtitle;
        private boolean updatingUi;
        private boolean enabled;
        private float confirmedMin;
        private float confirmedMax;

        Section(boolean inner) {
            this.inner = inner;
            enabledKey = inner
                    ? KEY_ENABLE_REFRESH_RATE_SCREEN_INNER : KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER;
            minKey = inner
                    ? KEY_REFRESH_RATE_SCREEN_INNER_MIN : KEY_REFRESH_RATE_SCREEN_OUTER_MIN;
            maxKey = inner
                    ? KEY_REFRESH_RATE_SCREEN_INNER_MAX : KEY_REFRESH_RATE_SCREEN_OUTER_MAX;
            limitSwitch = new MaterialSwitch(host);
            minInput = rateInput();
            maxInput = rateInput();
            inputRows = new LinearLayout(host);
            enabled = "1".equals(settings.getGlobal(enabledKey, "0"));
            confirmedMin = parseStored(settings.getGlobal(minKey, ""), DEFAULT_MIN);
            confirmedMax = parseStored(settings.getGlobal(maxKey, ""), DEFAULT_MAX);
        }

        View card() {
            MaterialCardView card = ui.card();
            LinearLayout body = ui.cardBody();
            card.addView(body);

            body.addView(ui.text(host.getString(inner
                            ? R.string.refresh_rate_screen_inner
                            : R.string.refresh_rate_screen_outer),
                    20, true, ui.colorOnSurface));
            ui.addSpace(body, 10);

            LinearLayout switchRow = new LinearLayout(host);
            switchRow.setGravity(Gravity.CENTER_VERTICAL);
            switchRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout copy = new LinearLayout(host);
            copy.setOrientation(LinearLayout.VERTICAL);
            switchRow.addView(copy, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            copy.addView(ui.text(host.getString(R.string.refresh_rate_screen_limit_switch),
                    16, true, ui.colorOnSurface));
            switchSubtitle = ui.text(statusText(), 13, false, ui.colorOnSurfaceVariant);
            copy.addView(switchSubtitle);
            switchRow.addView(limitSwitch);
            body.addView(switchRow, ui.matchWrap());

            ui.addSpace(body, 14);
            inputRows.setOrientation(LinearLayout.VERTICAL);
            inputRows.addView(labeledRateRow(
                    host.getString(R.string.refresh_rate_screen_min), minInput));
            inputRows.addView(labeledRateRow(
                    host.getString(R.string.refresh_rate_screen_max), maxInput));
            body.addView(inputRows, ui.matchWrap());

            limitSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (updatingUi) return;
                if (!checked) {
                    applyChanges(false, confirmedMin, confirmedMax);
                    return;
                }
                Float min = parseRate(minInput.getText().toString());
                Float max = parseRate(maxInput.getText().toString());
                if (min == null || max == null || min > max) {
                    // The switch must spring back: the last confirmed state is disabled.
                    updatingUi = true;
                    limitSwitch.setChecked(false);
                    updatingUi = false;
                    rejectInputs(R.string.refresh_rate_invalid_range);
                    return;
                }
                applyChanges(true, min, max);
            });
            minInput.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) commitInputs();
            });
            maxInput.setOnFocusChangeListener((view, hasFocus) -> {
                if (!hasFocus) commitInputs();
            });
            minInput.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    commitInputs();
                    return true;
                }
                return false;
            });
            maxInput.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    commitInputs();
                    return true;
                }
                return false;
            });

            updatingUi = true;
            limitSwitch.setChecked(enabled);
            minInput.setText(formatRate(confirmedMin));
            maxInput.setText(formatRate(confirmedMax));
            updatingUi = false;
            updateInteraction();
            return card;
        }

        private String statusText() {
            return enabled
                    ? String.format(Locale.US, "%.0f - %.0f Hz", confirmedMin, confirmedMax)
                    : host.getString(R.string.refresh_rate_screen_follow_system);
        }

        private void commitInputs() {
            if (updatingUi || !limitSwitch.isChecked()) return;
            Float min = parseRate(minInput.getText().toString());
            Float max = parseRate(maxInput.getText().toString());
            if (min == null || max == null || min > max) {
                rejectInputs(R.string.refresh_rate_invalid_range);
                return;
            }
            applyChanges(true, min, max);
        }

        private void applyChanges(boolean nextEnabled, float min, float max) {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(enabledKey, nextEnabled ? "1" : "0");
            values.put(minKey, formatRate(min));
            values.put(maxKey, formatRate(max));
            boolean saved = settings.putGlobalsQuietly(values);
            if (saved) {
                enabled = nextEnabled;
                confirmedMin = min;
                confirmedMax = max;
                Toast.makeText(host, R.string.toast_saved, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(host, R.string.toast_save_failed_permission,
                        Toast.LENGTH_LONG).show();
            }
            updatingUi = true;
            limitSwitch.setChecked(enabled);
            minInput.setText(formatRate(confirmedMin));
            maxInput.setText(formatRate(confirmedMax));
            updatingUi = false;
            updateInteraction();
            refreshSwitchSubtitle();
        }

        private void rejectInputs(int message) {
            Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
            updatingUi = true;
            minInput.setText(formatRate(confirmedMin));
            maxInput.setText(formatRate(confirmedMax));
            updatingUi = false;
            updateInteraction();
            refreshSwitchSubtitle();
        }

        private void updateInteraction() {
            boolean active = limitSwitch.isChecked();
            minInput.setEnabled(active);
            maxInput.setEnabled(active);
            inputRows.setAlpha(active ? 1f : 0.45f);
        }

        private void refreshSwitchSubtitle() {
            switchSubtitle.setText(statusText());
        }

        private LinearLayout labeledRateRow(String label, EditText input) {
            LinearLayout row = new LinearLayout(host);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView prefix = ui.text(label, 15, false, ui.colorOnSurfaceVariant);
            row.addView(prefix, new LinearLayout.LayoutParams(
                    ui.dp(96), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(input, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView unit = ui.text("Hz", 15, false, ui.colorOnSurfaceVariant);
            unit.setPadding(ui.dp(10), 0, 0, 0);
            row.addView(unit);
            return row;
        }

        private EditText rateInput() {
            EditText input = new EditText(host);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setTextSize(20);
            input.setGravity(Gravity.CENTER);
            input.setMinHeight(ui.dp(54));
            return input;
        }

        private float parseStored(String raw, float fallback) {
            Float value = parseRate(raw);
            return value == null ? fallback : value;
        }

        private Float parseRate(String raw) {
            try {
                float value = Float.parseFloat(raw.trim());
                boolean valid = Float.isFinite(value)
                        && value >= 1f
                        && value <= RefreshRateScreenRangePolicy.MAX_RATE;
                return valid ? value : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String formatRate(float value) {
            return String.format(Locale.US, "%.0f", value);
        }
    }
}
