package io.github.pigerzhu.onelab.feature.window;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.util.Locale;

import io.github.pigerzhu.onelab.system.CoverEdgeClient;
import io.github.pigerzhu.onelab.ui.Ui;

/** Controls the Fold cover display grip zone exposed by Samsung's TSP controller. */
public final class CoverEdgeScreen {
    private static final String PREFS = "onelab_cover_edge";
    private static final String PREF_ACTIVE = "active";
    private static final String PREF_WIDTH = "width_percent";
    private static final String PREF_ORIGINAL_CAPTURED = "original_captured";
    private static final String PREF_ORIGINAL_EXISTS = "original_exists";
    private static final String PREF_ORIGINAL_VALUE = "original_value";
    private static final float DEFAULT_WIDTH = 2.0f;

    private final MainActivity host;
    private final Ui ui;
    private final CoverEdgeClient client;
    private MaterialSwitch enabledSwitch;
    private Slider widthSlider;
    private TextView widthValue;
    private View leftEdgePreview;
    private View rightEdgePreview;
    private boolean updatingUi;

    public CoverEdgeScreen(MainActivity host, Ui ui) {
        this.host = host;
        this.ui = ui;
        this.client = new CoverEdgeClient(host);
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

        body.addView(ui.text(host.getString(R.string.cover_edge_title), 20, true,
                        ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = ui.text("›", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    private void showPage() {
        host.setNestedBackAction(() -> host.showExperimentsPage(true));
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.cover_edge_title),
                host.getString(R.string.cover_edge_page_summary), 1);
        root.addView(controlCard());
    }

    private View controlCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        LinearLayout switchRow = new LinearLayout(host);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(switchRow, ui.matchWrap());

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        switchRow.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        copy.addView(ui.text(host.getString(R.string.cover_edge_enable), 20, true,
                ui.colorOnSurface));
        copy.addView(ui.text(host.getString(R.string.cover_edge_enable_summary), 14, false,
                ui.colorOnSurfaceVariant));

        enabledSwitch = new MaterialSwitch(host);
        boolean active = prefs().getBoolean(PREF_ACTIVE, false)
                && client.read() != null;
        enabledSwitch.setChecked(active);
        switchRow.addView(enabledSwitch);

        ui.addSpace(body, 18);
        LinearLayout valueRow = new LinearLayout(host);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(valueRow, ui.matchWrap());
        valueRow.addView(ui.text(host.getString(R.string.cover_edge_width), 17, true,
                        ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        widthValue = ui.text("", 16, true, ui.colorPrimary);
        valueRow.addView(widthValue);

        widthSlider = new Slider(host);
        widthSlider.setValueFrom(0.5f);
        widthSlider.setValueTo(10.0f);
        widthSlider.setStepSize(0.25f);
        widthSlider.setLabelFormatter(this::formatWidth);
        widthSlider.setValue(clampWidth(prefs().getFloat(PREF_WIDTH, DEFAULT_WIDTH)));
        widthSlider.setEnabled(active);
        widthSlider.addOnChangeListener((slider, value, fromUser) -> {
            widthValue.setText(formatWidth(value));
            if (fromUser) {
                prefs().edit().putFloat(PREF_WIDTH, value).apply();
                updateEdgePreview(value);
            }
        });
        widthSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                showEdgePreview(slider.getValue());
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                hideEdgePreview();
                if (enabledSwitch.isChecked()) {
                    applyWidth(slider.getValue());
                }
            }
        });
        body.addView(widthSlider, ui.matchWrap());
        widthValue.setText(formatWidth(widthSlider.getValue()));

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) return;
            enabledSwitch.setEnabled(false);
            widthSlider.setEnabled(false);
            if (checked) {
                captureOriginalIfNeeded();
                applyWidth(widthSlider.getValue());
            } else {
                restoreOriginal();
            }
        });
        return card;
    }

    private void applyWidth(float width) {
        float safeWidth = clampWidth(width);
        String percent = String.format(Locale.US, "%.2f%%", safeWidth);
        String command = percent + ',' + percent + ",0%," + percent
                + ',' + percent + ',' + percent + ",0%,0%,0%,0%";
        writeSetting(command, true, host.getString(R.string.cover_edge_applied));
    }

    private void restoreOriginal() {
        SharedPreferences prefs = prefs();
        String original = prefs.getBoolean(PREF_ORIGINAL_EXISTS, false)
                ? prefs.getString(PREF_ORIGINAL_VALUE, null)
                : null;
        writeSetting(original, false, host.getString(R.string.cover_edge_restored));
    }

    private void writeSetting(String value, boolean active, String successMessage) {
        new Thread(() -> {
            boolean success = client.write(value);
            host.runOnUiThread(() -> {
                boolean persistedActive = prefs().getBoolean(PREF_ACTIVE, false);
                updatingUi = true;
                enabledSwitch.setChecked(success ? active : persistedActive);
                enabledSwitch.setEnabled(true);
                widthSlider.setEnabled(success ? active : persistedActive);
                updatingUi = false;
                if (success) {
                    SharedPreferences.Editor editor = prefs().edit().putBoolean(PREF_ACTIVE, active);
                    if (!active) {
                        editor.remove(PREF_ORIGINAL_CAPTURED)
                                .remove(PREF_ORIGINAL_EXISTS)
                                .remove(PREF_ORIGINAL_VALUE);
                    }
                    editor.apply();
                    Toast.makeText(host, successMessage, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(host, R.string.toast_write_failed_root,
                            Toast.LENGTH_LONG).show();
                }
            });
        }, "cover-edge-setting").start();
    }

    private void captureOriginalIfNeeded() {
        SharedPreferences prefs = prefs();
        if (prefs.getBoolean(PREF_ORIGINAL_CAPTURED, false)) return;
        String original = client.read();
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(PREF_ORIGINAL_CAPTURED, true)
                .putBoolean(PREF_ORIGINAL_EXISTS, original != null);
        if (original != null) editor.putString(PREF_ORIGINAL_VALUE, original);
        editor.apply();
    }

    private SharedPreferences prefs() {
        return host.getSharedPreferences(PREFS, MainActivity.MODE_PRIVATE);
    }

    private float clampWidth(float value) {
        return Math.max(0.5f, Math.min(10.0f, Math.round(value * 4f) / 4f));
    }

    private String formatWidth(float value) {
        return String.format(Locale.US, "%.2f%%", value);
    }

    private void showEdgePreview(float widthPercent) {
        hideEdgePreview();
        ViewGroup content = host.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;

        int color = (ui.colorPrimary & 0x00FFFFFF) | 0x66000000;
        leftEdgePreview = edgePreviewView(color);
        rightEdgePreview = edgePreviewView(color);
        content.addView(leftEdgePreview, edgePreviewParams(Gravity.START, widthPercent));
        content.addView(rightEdgePreview, edgePreviewParams(Gravity.END, widthPercent));
    }

    private View edgePreviewView(int color) {
        View preview = new View(host);
        preview.setBackgroundColor(color);
        preview.setClickable(false);
        preview.setFocusable(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.setElevation(ui.dp(24));
        return preview;
    }

    private FrameLayout.LayoutParams edgePreviewParams(int gravity, float widthPercent) {
        int screenWidth = host.getResources().getDisplayMetrics().widthPixels;
        int width = Math.max(1, Math.round(screenWidth * widthPercent / 100f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = gravity;
        return params;
    }

    private void updateEdgePreview(float widthPercent) {
        if (leftEdgePreview == null || rightEdgePreview == null) return;
        leftEdgePreview.setLayoutParams(edgePreviewParams(Gravity.START, widthPercent));
        rightEdgePreview.setLayoutParams(edgePreviewParams(Gravity.END, widthPercent));
    }

    private void hideEdgePreview() {
        ViewGroup content = host.findViewById(android.R.id.content);
        if (content != null) {
            if (leftEdgePreview != null) content.removeView(leftEdgePreview);
            if (rightEdgePreview != null) content.removeView(rightEdgePreview);
        }
        leftEdgePreview = null;
        rightEdgePreview = null;
    }

    public void onDestroy() {
        hideEdgePreview();
    }
}
