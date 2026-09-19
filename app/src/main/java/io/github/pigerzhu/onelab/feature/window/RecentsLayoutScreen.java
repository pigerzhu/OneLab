package io.github.pigerzhu.onelab.feature.window;

import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_RECENTS_LAYOUT_COVER;
import static io.github.pigerzhu.onelab.contract.SettingsKeys.KEY_RECENTS_LAYOUT_MAIN;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_GRID;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_LIST;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_SLIM;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_STACK;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_TILT_STACK;
import static io.github.pigerzhu.onelab.hook.applications.SamsungRecentsLayoutPolicy.LAYOUT_VERTICAL;

import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.ExpandableSwitchGroup;
import io.github.pigerzhu.onelab.ui.MaterialSelectionMenu;
import io.github.pigerzhu.onelab.ui.Ui;

/** User controls for the launcher recent-app layout on each foldable display. */
public final class RecentsLayoutScreen {
    private static final int UNINITIALIZED = -1;

    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    private View mainRow;
    private View coverRow;
    private TextView mainValue;
    private TextView coverValue;
    private boolean syncing;
    private float lastTouchX;
    private ExpandableSwitchGroup group;

    public RecentsLayoutScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View entryCard() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        body.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8));
        card.addView(body);

        MaterialSwitch toggle = new MaterialSwitch(host);
        boolean enabled = settings.getGlobalInt(
                KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY, 0) != 0;
        toggle.setChecked(enabled);

        mainValue = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        coverValue = ui.text("", 14, false, ui.colorOnSurfaceVariant);
        mainRow = selectionRow(R.string.recents_layout_main_display, mainValue,
                KEY_RECENTS_LAYOUT_MAIN);
        coverRow = selectionRow(R.string.recents_layout_cover_display, coverValue,
                KEY_RECENTS_LAYOUT_COVER);
        LinearLayout choices = new LinearLayout(host);
        choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(ui.dp(40), 0, 0, 0);
        choices.addView(mainRow);
        choices.addView(coverRow);
        group = new ExpandableSwitchGroup(
                host, ui, host.getString(R.string.recents_layout_title),
                host.getString(R.string.recents_layout_summary), toggle, choices, 20);
        group.setExpanded(false, false);
        body.addView(group);

        refreshValues();
        observeValues(card);
        setChoicesEnabled(enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            button.setEnabled(false);
            settings.setGlobalAsync(KEY_ENABLE_RECENTS_LAYOUT_PER_DISPLAY,
                    checked ? "1" : "0", saved -> {
                        button.setEnabled(true);
                        if (saved) {
                            setChoicesEnabled(checked);
                            group.setExpanded(checked && group.isExpanded(), true);
                            refreshValues();
                        } else {
                            syncing = true;
                            button.setChecked(!checked);
                            syncing = false;
                            Toast.makeText(host, R.string.toast_save_failed_permission,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
        return card;
    }

    private View selectionRow(int titleRes, TextView valueView, String settingKey) {
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(10), 0, ui.dp(10));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(ui.text(host.getString(titleRes), 16, true, ui.colorOnSurface),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48)));
        row.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                lastTouchX = event.getRawX();
            }
            return false;
        });
        row.setOnClickListener(view -> showLayoutMenu(view, valueView, settingKey));
        return row;
    }

    private void showLayoutMenu(View anchor, TextView valueView, String settingKey) {
        MaterialSelectionMenu menu = new MaterialSelectionMenu(anchor, ui);
        menu.show(lastTouchX, layoutOptions(),
                settings.getGlobalInt(settingKey, UNINITIALIZED), selected -> {
            int previous = settings.getGlobalInt(settingKey, UNINITIALIZED);
            if (previous == selected) return;
            valueView.setText(layoutName(selected));
            anchor.setEnabled(false);
            settings.setGlobalAsync(settingKey, String.valueOf(selected), saved -> {
                anchor.setEnabled(true);
                if (!saved) {
                    valueView.setText(layoutName(previous));
                    Toast.makeText(host, R.string.toast_save_failed_permission,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private java.util.List<MaterialSelectionMenu.Option> layoutOptions() {
        return java.util.Arrays.asList(
                new MaterialSelectionMenu.Option(LAYOUT_LIST,
                        host.getString(R.string.recents_layout_list)),
                new MaterialSelectionMenu.Option(LAYOUT_GRID,
                        host.getString(R.string.recents_layout_grid)),
                new MaterialSelectionMenu.Option(LAYOUT_STACK,
                        host.getString(R.string.recents_layout_stack)),
                new MaterialSelectionMenu.Option(LAYOUT_VERTICAL,
                        host.getString(R.string.recents_layout_vertical)),
                new MaterialSelectionMenu.Option(LAYOUT_SLIM,
                        host.getString(R.string.recents_layout_slim)),
                new MaterialSelectionMenu.Option(LAYOUT_TILT_STACK,
                        host.getString(R.string.recents_layout_tilt_stack)));
    }

    private void refreshValues() {
        mainValue.setText(layoutName(settings.getGlobalInt(
                KEY_RECENTS_LAYOUT_MAIN, UNINITIALIZED)));
        coverValue.setText(layoutName(settings.getGlobalInt(
                KEY_RECENTS_LAYOUT_COVER, UNINITIALIZED)));
    }

    private void observeValues(View page) {
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                refreshValues();
            }
        };
        host.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(KEY_RECENTS_LAYOUT_MAIN), false, observer);
        host.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(KEY_RECENTS_LAYOUT_COVER), false, observer);
        page.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                host.getContentResolver().unregisterContentObserver(observer);
                view.removeOnAttachStateChangeListener(this);
            }
        });
    }

    private String layoutName(int value) {
        switch (value) {
            case LAYOUT_LIST:
                return host.getString(R.string.recents_layout_list);
            case LAYOUT_GRID:
                return host.getString(R.string.recents_layout_grid);
            case LAYOUT_STACK:
                return host.getString(R.string.recents_layout_stack);
            case LAYOUT_VERTICAL:
                return host.getString(R.string.recents_layout_vertical);
            case LAYOUT_SLIM:
                return host.getString(R.string.recents_layout_slim);
            case LAYOUT_TILT_STACK:
                return host.getString(R.string.recents_layout_tilt_stack);
            default:
                return host.getString(R.string.recents_layout_pending_home_up);
        }
    }

    private void setChoicesEnabled(boolean enabled) {
        setEnabled(mainRow, enabled);
        setEnabled(coverRow, enabled);
    }

    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }
}
