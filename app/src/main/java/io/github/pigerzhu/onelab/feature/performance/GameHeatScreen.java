package io.github.pigerzhu.onelab.feature.performance;

import io.github.pigerzhu.onelab.R;

import io.github.pigerzhu.onelab.MainActivity;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;

import org.json.JSONObject;

import io.github.pigerzhu.onelab.system.GosClient;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class GameHeatScreen {

    private static final String KEY_ALLOW_MORE_HEAT_VALUE = "allow_more_heat_value";
    private static final int GAME_HEAT_MAX_VALUE = 60;
    private static final String GAME_TOOLS_PACKAGE = "com.samsung.android.game.gametools";
    private static final String GOS_PACKAGE = "com.samsung.android.game.gos";
    private static final String CUSTOMIZE_EACH_GAME_ACTIVITY =
            "com.samsung.android.game.gametools.ui.gamemode.CustomizeEachGameActivity";

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    private TextView eachGameHeatStatus;
    private TextView gameHeatValueLabel;
    private TextView eachGameHeatValueLabel;
    private Slider gameHeatSlider;
    private Slider eachGameHeatSlider;
    private EditText gamePackageInput;
    private int eachGameHeatValue;

    public GameHeatScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    /** Entry card for the Experiments page. Tapping opens {@link #showPage()}. */
    public View entryCard() {
        MaterialCardView card = ui.card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showPage());

        LinearLayout body = ui.cardBody();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.game_heat_title), 20, true,
                        ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView arrow = ui.text("›", 28, false, ui.colorOnSurfaceVariant);
        arrow.setGravity(Gravity.CENTER);
        body.addView(arrow, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(40)));
        return card;
    }

    /** Navigate to the Game Heat sub-page. */
    void showPage() {
        host.setNestedBackAction(() -> host.showExperimentsPage(true));
        LinearLayout root = host.beginSubPage(
                host.getString(R.string.game_heat_title),
                host.getString(R.string.game_heat_page_summary), 1);
        root.addView(gameHeatBudgetCard());
        root.addView(eachGamePolicyCard());
    }

    private View gameHeatBudgetCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.game_heat_global_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(host.getString(R.string.game_heat_global_summary), 14, false,
                ui.colorOnSurfaceVariant));

        ui.addSpace(body, 14);
        gameHeatValueLabel = heatValueLabel();
        gameHeatValueLabel.setOnClickListener(v -> showHeatValueDialog(
                host.getString(R.string.game_heat_global_dialog_title),
                settings.getSecureInt(KEY_ALLOW_MORE_HEAT_VALUE, 0),
                this::setGameHeatBudget
        ));
        body.addView(gameHeatValueLabel, ui.matchWrap());

        gameHeatSlider = heatSlider();
        gameHeatSlider.setValue(nearestHeatSliderValue(settings.getSecureInt(KEY_ALLOW_MORE_HEAT_VALUE, 0)));
        gameHeatSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                updateGlobalHeatValueLabel(Math.round(value));
            }
        });
        gameHeatSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                setGameHeatBudget(Math.round(slider.getValue()));
            }
        });
        body.addView(gameHeatSlider, ui.matchWrap());

        updateGameHeatBudgetSelections();
        return card;
    }

    private View eachGamePolicyCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);

        body.addView(ui.text(host.getString(R.string.game_heat_per_game_title), 20, true,
                ui.colorOnSurface));
        body.addView(ui.text(host.getString(R.string.game_heat_per_game_summary), 14, false,
                ui.colorOnSurfaceVariant));

        ui.addSpace(body, 14);
        gamePackageInput = new EditText(host);
        gamePackageInput.setSingleLine(true);
        gamePackageInput.setTextSize(14);
        gamePackageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        gamePackageInput.setHint(host.getString(R.string.game_heat_package_hint));
        gamePackageInput.setSelectAllOnFocus(true);
        body.addView(gamePackageInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)));

        ui.addSpace(body, 12);
        eachGameHeatValueLabel = heatValueLabel();
        eachGameHeatValueLabel.setOnClickListener(v -> showHeatValueDialog(
                host.getString(R.string.game_heat_per_game_title),
                Math.round(eachGameHeatSlider == null ? 0f : eachGameHeatSlider.getValue()),
                this::setEachGameHeatValueUi
        ));
        body.addView(eachGameHeatValueLabel, ui.matchWrap());

        eachGameHeatSlider = heatSlider();
        eachGameHeatSlider.setValue(0f);
        eachGameHeatSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                setEachGameHeatValueUi(Math.round(value));
            }
        });
        body.addView(eachGameHeatSlider, ui.matchWrap());
        setEachGameHeatValueUi(0);

        ui.addSpace(body, 10);
        MaterialButton writeButton = ui.actionButton(
                host.getString(R.string.game_heat_write));
        writeButton.setOnClickListener(v -> writeEachGameHeatBudget());
        body.addView(writeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(46)));

        ui.addSpace(body, 10);
        MaterialButton readButton = ui.actionButton(
                host.getString(R.string.game_heat_read));
        readButton.setOnClickListener(v -> readEachGameHeatBudget());
        body.addView(readButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(46)));

        ui.addSpace(body, 12);
        eachGameHeatStatus = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        eachGameHeatStatus.setVisibility(View.GONE);
        body.addView(eachGameHeatStatus);
        return card;
    }

    private void setGameHeatBudget(int value) {
        settings.setSecureWithToast(KEY_ALLOW_MORE_HEAT_VALUE, String.valueOf(clampHeatValue(value)));
        updateGameHeatBudgetSelections();
    }

    private void updateGameHeatBudgetSelections() {
        int value = clampHeatValue(settings.getSecureInt(KEY_ALLOW_MORE_HEAT_VALUE, 0));
        if (gameHeatSlider != null) {
            gameHeatSlider.setValue(nearestHeatSliderValue(value));
        }
        updateGlobalHeatValueLabel(value);
    }

    private void readEachGameHeatBudget() {
        if (gamePackageInput == null) {
            return;
        }
        String pkg = gamePackageInput.getText().toString().trim();
        if (pkg.isEmpty()) {
            Toast.makeText(host, R.string.game_heat_package_required,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        setEachGameStatus(host.getString(R.string.game_heat_reading, pkg));
        try {
            JSONObject json = new JSONObject();
            json.put("package_name", pkg);
            requestGos("get_package_data", json.toString(), new GosCallback() {
                @Override
                public void onResult(String result) {
                    handleEachGameReadResult(pkg, result);
                }

                @Override
                public void onError(String message) {
                    setEachGameStatus(message);
                }
            });
        } catch (Exception e) {
            setEachGameStatus(host.getString(R.string.game_heat_read_failed,
                    String.valueOf(e.getMessage())));
        }
    }

    private void writeEachGameHeatBudget() {
        if (gamePackageInput == null) {
            return;
        }
        String pkg = gamePackageInput.getText().toString().trim();
        if (pkg.isEmpty()) {
            Toast.makeText(host, R.string.game_heat_package_required,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Integer value = getCustomEachGameHeatValue();
        if (value == null) {
            return;
        }
        setEachGameStatus(host.getString(R.string.game_heat_writing, pkg, value));
        try {
            JSONObject json = new JSONObject();
            json.put("package_name", pkg);
            json.put("allow_more_heat_value", value);
            requestGos("set_package_data", json.toString(), new GosCallback() {
                @Override
                public void onResult(String result) {
                    handleEachGameWriteResult(pkg, value, result);
                }

                @Override
                public void onError(String message) {
                    setEachGameStatus(message);
                }
            });
        } catch (Exception e) {
            setEachGameStatus(host.getString(R.string.game_heat_write_failed,
                    String.valueOf(e.getMessage())));
        }
    }

    private Integer getCustomEachGameHeatValue() {
        return clampHeatValue(eachGameHeatValue);
    }

    private void handleEachGameReadResult(String pkg, String result) {
        if (result == null || result.isEmpty()) {
            setEachGameStatus(host.getString(R.string.game_heat_read_no_data));
            return;
        }
        try {
            JSONObject json = new JSONObject(result);
            int value = json.optInt("allow_more_heat_value", -1);
            int performanceMode = json.optInt("performance_mode", -1);
            int customSiopMode = json.optInt("custom_siop_mode", -999);
            if (value < 0) {
                setEachGameStatus(host.getString(R.string.game_heat_read_missing_value, result));
                return;
            }
            ui.syncingUi = true;
            if (eachGameHeatSlider != null) {
                eachGameHeatSlider.setValue(nearestHeatSliderValue(value));
            }
            setEachGameHeatValueUi(value);
            ui.syncingUi = false;
            setEachGameStatus(host.getString(R.string.game_heat_read_result, pkg, value,
                    heatText(value), performanceMode,
                    customSiopMode == -999
                            ? host.getString(R.string.game_heat_not_returned)
                            : String.valueOf(customSiopMode)));
        } catch (Exception e) {
            setEachGameStatus(host.getString(R.string.game_heat_parse_failed,
                    String.valueOf(e.getMessage()), result));
        } finally {
            ui.syncingUi = false;
        }
    }

    private void handleEachGameWriteResult(String pkg, int value, String result) {
        if (result == null || result.isEmpty()) {
            setEachGameStatus(host.getString(R.string.game_heat_write_rejected));
            return;
        }
        boolean ok = result.contains("\"allow_more_heat_value\"") || result.contains("allow_more_heat_value");
        setEachGameStatus(host.getString(
                ok ? R.string.game_heat_write_result : R.string.game_heat_write_unconfirmed,
                pkg, value, heatText(value), result));
        if (ok) {
            Toast.makeText(host, R.string.game_heat_write_done, Toast.LENGTH_SHORT).show();
        }
    }

    private void setEachGameStatus(String value) {
        if (eachGameHeatStatus != null) {
            eachGameHeatStatus.setText(value);
            eachGameHeatStatus.setVisibility(View.VISIBLE);
        }
    }

    private void updateGlobalHeatValueLabel(int value) {
        if (gameHeatValueLabel != null) {
            gameHeatValueLabel.setText("+" + heatText(value) + "°C");
        }
    }

    private void updateEachGameHeatValueLabel(int value) {
        if (eachGameHeatValueLabel != null) {
            eachGameHeatValueLabel.setText("+" + heatText(value) + "°C");
        }
    }

    private void setEachGameHeatValueUi(int value) {
        int checkedValue = clampHeatValue(value);
        eachGameHeatValue = checkedValue;
        if (eachGameHeatSlider != null) {
            eachGameHeatSlider.setValue(nearestHeatSliderValue(checkedValue));
        }
        updateEachGameHeatValueLabel(checkedValue);
    }

    private TextView heatValueLabel() {
        TextView label = ui.text("", 28, true, ui.colorOnSurface);
        label.setGravity(Gravity.CENTER);
        label.setClickable(true);
        label.setFocusable(true);
        label.setPadding(0, ui.dp(4), 0, ui.dp(2));
        return label;
    }

    private Slider heatSlider() {
        Slider slider = new Slider(host);
        slider.setValueFrom(0f);
        slider.setValueTo(GAME_HEAT_MAX_VALUE);
        slider.setStepSize(5f);
        slider.setLabelFormatter(value -> "+" + heatText(Math.round(value)) + "°C");
        return slider;
    }

    private void showHeatValueDialog(String title, int currentValue, IntValueConsumer consumer) {
        EditText input = new EditText(host);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(heatText(currentValue));
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setMinHeight(ui.dp(58));
        input.setSelectAllOnFocus(true);
        input.setPadding(0, 0, 0, 0);

        FrameLayout inputContainer = new FrameLayout(host);
        int horizontalPadding = ui.dp(22);
        int verticalPadding = ui.dp(8);
        inputContainer.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);
        inputContainer.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(64)
        ));

        new AlertDialog.Builder(host)
                .setTitle(title)
                .setMessage(R.string.game_heat_dialog_message)
                .setView(inputContainer)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    try {
                        float degrees = Float.parseFloat(input.getText().toString().trim());
                        int value = Math.round(degrees * 10f);
                        if (value < 0 || value > GAME_HEAT_MAX_VALUE) {
                            Toast.makeText(host, R.string.game_heat_range_invalid,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        consumer.accept(value);
                    } catch (NumberFormatException ignored) {
                        Toast.makeText(host, R.string.game_heat_not_a_number,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private int clampHeatValue(int value) {
        return Math.max(0, Math.min(GAME_HEAT_MAX_VALUE, value));
    }

    private float nearestHeatSliderValue(int value) {
        return Math.round(clampHeatValue(value) / 5f) * 5f;
    }

    private String heatText(int value) {
        return String.format(java.util.Locale.US, "%.1f", clampHeatValue(value) / 10.0f);
    }

    private interface IntValueConsumer {
        void accept(int value);
    }

    private interface GosCallback {
        void onResult(String result);

        void onError(String message);
    }

    private void requestGos(String command, String json, GosCallback callback) {
        GosClient.request(host, command, json, new GosClient.Callback() {
            @Override
            public void onResult(String result) {
                host.runOnUiThread(() -> callback.onResult(result));
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> callback.onError(message));
            }
        });
    }
}
