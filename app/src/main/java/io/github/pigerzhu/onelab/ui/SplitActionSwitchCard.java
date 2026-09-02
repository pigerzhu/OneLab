package io.github.pigerzhu.onelab.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Card with an independent navigation region and trailing master switch. */
public final class SplitActionSwitchCard extends MaterialCardView {
    public SplitActionSwitchCard(
            Context context,
            Ui ui,
            String title,
            String subtitle,
            MaterialSwitch toggle,
            Runnable action
    ) {
        super(context);
        setRadius(ui.dp(16));
        setCardElevation(0);
        setStrokeWidth(ui.dp(1));
        setStrokeColor(MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOutlineVariant,
                0x1F000000));
        setCardBackgroundColor(ui.colorSurfaceContainer);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, ui.dp(10));
        setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actionRegion = new LinearLayout(context);
        actionRegion.setOrientation(LinearLayout.VERTICAL);
        actionRegion.setGravity(Gravity.CENTER_VERTICAL);
        actionRegion.setPadding(ui.dp(18), ui.dp(16), ui.dp(14), ui.dp(16));
        actionRegion.setClickable(true);
        actionRegion.setFocusable(true);
        actionRegion.setOnClickListener(view -> action.run());
        actionRegion.addView(ui.text(title, 20, true, ui.colorOnSurface));
        if (subtitle != null && !subtitle.isEmpty()) {
            actionRegion.addView(ui.text(subtitle, 13, false, ui.colorOnSurfaceVariant));
        }
        row.addView(actionRegion, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        View divider = new View(context);
        GradientDrawable dividerBackground = new GradientDrawable();
        dividerBackground.setColor(MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOutlineVariant,
                0x33000000));
        divider.setBackground(dividerBackground);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ui.dp(1), ui.dp(36));
        row.addView(divider, dividerParams);

        LinearLayout switchRegion = new LinearLayout(context);
        switchRegion.setGravity(Gravity.CENTER);
        switchRegion.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        switchRegion.addView(toggle);
        row.addView(switchRegion);
    }
}
