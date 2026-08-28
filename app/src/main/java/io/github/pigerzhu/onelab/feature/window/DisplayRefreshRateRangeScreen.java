package io.github.pigerzhu.onelab.feature.window;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_INNER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_REFRESH_RATE_SCREEN_OUTER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_INNER_MIN;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MAX;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_REFRESH_RATE_SCREEN_OUTER_MIN;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.text.InputType;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import io.github.pigerzhu.onelab.hook.system.RefreshRateScreenRangePolicy;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

/**
 * Screen-level refresh-rate boundaries for the main and cover displays. This page is a
 * boundary for every application; per-app behavior stays in the refresh-rate policy page.
 *
 * Ranges are validated against the refresh rates this device actually exposes, and the
 * two bounds of one panel are always validated and saved together through the explicit
 * apply action so a legal combination such as raising both bounds can be typed.
 */
public final class DisplayRefreshRateRangeScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;
    private ScrollView pageScroll;
    /** Every refresh rate built into this device, ascending; may be empty if unreadable. */
    private float[] deviceRates = new float[0];

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
        deviceRates = collectSupportedRates();
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.refresh_rate_screen_range_title),
                host.getString(R.string.refresh_rate_screen_range_summary), 1);
        pageScroll = root.getParent() instanceof ScrollView ? (ScrollView) root.getParent() : null;
        applyImeInsets();
        root.addView(boundaryCard());
        root.addView(new Section(true).card());
        root.addView(new Section(false).card());
    }

    /**
     * Keyboard avoidance for this page only: the page host owns the status-bar inset,
     * and this listener keeps the navigation-bar spacing while lifting the content above
     * the IME. Attaching it to this page's own scroll view leaves every other page and
     * the large-screen shell untouched.
     */
    private void applyImeInsets() {
        if (pageScroll == null) return;
        pageScroll.setOnApplyWindowInsetsListener((view, insets) -> {
            int imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
            int navigationBottom =
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
            int bottom = Math.max(imeBottom, navigationBottom);
            if (view.getPaddingBottom() != bottom) {
                view.setPadding(0, view.getPaddingTop(), 0, bottom);
            }
            return insets;
        });
        pageScroll.requestApplyInsets();
    }

    private void scrollToInput(View input) {
        if (pageScroll == null) return;
        input.post(() -> {
            if (pageScroll == null) return;
            pageScroll.requestRectangleOnScreen(new Rect(
                    0, 0, input.getWidth(), input.getHeight() + ui.dp(32)), true);
        });
    }

    /** Rates of every built-in panel, so both fold sides can be validated up front. */
    private float[] collectSupportedRates() {
        TreeSet<Integer> unique = new TreeSet<>();
        DisplayManager displayManager = (DisplayManager) host.getSystemService(
                Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            for (Display display : displayManager.getDisplays()) {
                for (Display.Mode mode : display.getSupportedModes()) {
                    float rate = mode.getRefreshRate();
                    if (rate > 0f && Float.isFinite(rate)) {
                        unique.add(Math.round(rate * 100f));
                    }
                }
            }
        }
        float[] rates = new float[unique.size()];
        int index = 0;
        for (int value : unique) {
            rates[index++] = value / 100f;
        }
        return rates;
    }

    /**
     * Device-derived editor prefill: the panel's highest rate as the upper bound and its
     * highest rate at or below 60 Hz as the lower bound. No fixed 48-120 default that
     * other panels may not be able to satisfy.
     */
    private float[] defaultPrefill() {
        if (deviceRates.length == 0) return new float[]{60f, 60f};
        float max = deviceRates[deviceRates.length - 1];
        float min = deviceRates[0];
        for (float rate : deviceRates) {
            if (rate <= 60f) min = rate;
        }
        return new float[]{min, max};
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
        private final MaterialButton applyButton;
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
            applyButton = ui.actionButton(host.getString(R.string.action_apply));
            enabled = "1".equals(settings.getGlobal(enabledKey, "0"));
            float[] prefill = defaultPrefill();
            confirmedMin = parseStored(settings.getGlobal(minKey, ""), prefill[0]);
            confirmedMax = parseStored(settings.getGlobal(maxKey, ""), prefill[1]);
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
            LinearLayout applyRow = new LinearLayout(host);
            applyRow.setGravity(Gravity.CENTER_VERTICAL);
            applyRow.addView(applyButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputRows.addView(applyRow);
            body.addView(inputRows, ui.matchWrap());

            limitSwitch.setOnCheckedChangeListener((button, checked) -> {
                if (updatingUi) return;
                if (!checked) {
                    applyChanges(false, confirmedMin, confirmedMax);
                    return;
                }
                // Enabling saves the pair currently shown, so it must validate as a whole.
                applyInputs();
            });
            applyButton.setOnClickListener(v -> {
                if (!limitSwitch.isChecked()) return;
                applyInputs();
            });
            minInput.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) scrollToInput(view);
            });
            maxInput.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) scrollToInput(view);
            });
            minInput.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    applyInputs();
                    return true;
                }
                return false;
            });
            maxInput.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    applyInputs();
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

        /**
         * Validates and saves both bounds of this panel as one pair. A partial edit such
         * as moving 48-120 to 144-144 can never be rejected halfway, and a range no
         * supported mode can satisfy is refused as a whole.
         */
        private void applyInputs() {
            if (updatingUi) return;
            Float min = parseRate(minInput.getText().toString());
            Float max = parseRate(maxInput.getText().toString());
            if (min == null || max == null || min > max
                    || !RefreshRateScreenRangePolicy.isValidScreenRange(min, max)) {
                rejectInputs(R.string.refresh_rate_invalid_range);
                return;
            }
            if (deviceRates.length > 0 && RefreshRateScreenRangePolicy
                    .intersectWithSupportedRates(min, max, deviceRates) == null) {
                rejectInputs(R.string.refresh_rate_screen_unsupported_range);
                return;
            }
            applyChanges(true, min, max);
        }

        private void applyChanges(boolean nextEnabled, float min, float max) {
            Map<String, String> values = new java.util.LinkedHashMap<>();
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
            applyButton.setEnabled(active);
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
