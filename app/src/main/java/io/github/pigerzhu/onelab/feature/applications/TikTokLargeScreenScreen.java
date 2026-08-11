package io.github.pigerzhu.onelab.feature.applications;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;
import io.github.pigerzhu.onelab.contract.SettingsKeys;
import io.github.pigerzhu.onelab.system.SettingsStore;
import io.github.pigerzhu.onelab.ui.Ui;

public final class TikTokLargeScreenScreen {
    private final MainActivity host;
    private final Ui ui;
    private final SettingsStore settings;

    public TikTokLargeScreenScreen(MainActivity host, Ui ui, SettingsStore settings) {
        this.host = host;
        this.ui = ui;
        this.settings = settings;
    }

    public View card() {
        MaterialCardView card = ui.card();
        LinearLayout body = ui.cardBody();
        card.addView(body);
        body.addView(ui.text(host.getString(R.string.tiktok_fold_title), 20, true, ui.colorOnSurface));
        ui.addSpace(body, 8);
        MaterialSwitch portraitToggle = new MaterialSwitch(host);
        portraitToggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN, "0")));
        portraitToggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_PORTRAIT_LARGE_SCREEN, enabled ? "1" : "0"));
        MaterialSwitch toggle = new MaterialSwitch(host);
        toggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS, "0")));
        portraitToggle.setEnabled(toggle.isChecked());
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            settings.setGlobal(SettingsKeys.KEY_ENABLE_TIKTOK_SIDE_COMMENTS,
                    enabled ? "1" : "0");
            portraitToggle.setEnabled(enabled);
        });
        body.addView(commentsGroup(toggle, portraitToggle));
        MaterialSwitch liveToggle = new MaterialSwitch(host);
        liveToggle.setChecked("1".equals(settings.getGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN, "0")));
        liveToggle.setOnCheckedChangeListener((button, enabled) -> settings.setGlobal(
                SettingsKeys.KEY_ENABLE_TIKTOK_LIVE_MULTI_SCREEN, enabled ? "1" : "0"));
        body.addView(ui.switchRow(host.getString(R.string.tiktok_live_multi_screen),
                host.getString(R.string.tiktok_landscape_only), liveToggle));
        return card;
    }

    private View commentsGroup(MaterialSwitch toggle, MaterialSwitch portraitToggle) {
        LinearLayout group = new LinearLayout(host);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));

        LinearLayout copy = new LinearLayout(host);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(ui.text(host.getString(R.string.tiktok_side_comments),
                16, true, ui.colorOnSurface));
        copy.addView(ui.text(host.getString(R.string.tiktok_landscape_only),
                13, false, ui.colorOnSurfaceVariant));
        row.addView(copy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        ImageButton arrow = new ImageButton(host);
        arrow.setImageTintList(ColorStateList.valueOf(ui.colorOnSurfaceVariant));
        arrow.setBackgroundColor(Color.TRANSPARENT);
        arrow.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        row.addView(arrow, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)));
        row.addView(toggle);
        group.addView(row);

        View portraitRow = ui.switchRow(
                host.getString(R.string.tiktok_portrait_large_screen), null, portraitToggle, 15);
        portraitRow.setPadding(ui.dp(40), 0, 0, 0);
        group.addView(portraitRow);

        boolean[] expanded = {false};
        Runnable updateExpansion = () -> {
            arrow.setImageResource(expansionIcon(expanded[0]));
            portraitRow.setVisibility(childVisibility(expanded[0]));
            int description = expanded[0] ? R.string.menu_collapse : R.string.menu_expand;
            arrow.setContentDescription(host.getString(description));
            arrow.setTooltipText(host.getString(description));
        };
        arrow.setOnClickListener(view -> {
            expanded[0] = !expanded[0];
            updateExpansion.run();
        });
        updateExpansion.run();
        return group;
    }

    static int childVisibility(boolean expanded) {
        return expanded ? View.VISIBLE : View.GONE;
    }

    static int expansionIcon(boolean expanded) {
        return expanded ? R.drawable.ic_expand_more : R.drawable.ic_chevron_right;
    }
}
