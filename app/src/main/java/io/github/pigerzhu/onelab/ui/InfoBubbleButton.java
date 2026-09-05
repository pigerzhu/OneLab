package io.github.pigerzhu.onelab.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;

/** Reusable non-modal information button with an anchored, timed bubble. */
public final class InfoBubbleButton extends AppCompatImageButton {
    static final long DISPLAY_DURATION_MS = 4_000L;
    private final Ui ui;
    private final CharSequence message;
    private PopupWindow popup;

    public InfoBubbleButton(Context context, Ui ui, CharSequence message,
            CharSequence contentDescription) {
        super(context);
        this.ui = ui;
        this.message = message;
        setImageDrawable(ContextCompat.getDrawable(context, io.github.pigerzhu.onelab.R.drawable.ic_info));
        setImageTintList(android.content.res.ColorStateList.valueOf(ui.colorOnSurfaceVariant));
        setBackgroundColor(Color.TRANSPARENT);
        setContentDescription(contentDescription);
        setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        setOnClickListener(view -> toggleBubble());
    }

    private void toggleBubble() {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return;
        }
        TextView text = new TextView(getContext());
        text.setText(message);
        text.setTextSize(14);
        text.setTextColor(ui.colorOnSurface);
        text.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(ui.colorSurfaceContainer);
        background.setCornerRadius(ui.dp(12));
        text.setBackground(background);
        popup = new PopupWindow(text, ui.dp(260), ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setFocusable(false);
        popup.setOutsideTouchable(true);
        popup.setElevation(ui.dp(6));
        popup.setBackgroundDrawable(background);
        popup.setOnDismissListener(() -> removeCallbacks(dismissRunnable));
        popup.showAsDropDown(this, -ui.dp(210), ui.dp(4), Gravity.TOP | Gravity.START);
        postDelayed(dismissRunnable, DISPLAY_DURATION_MS);
    }

    private final Runnable dismissRunnable = () -> {
        if (popup != null && popup.isShowing()) popup.dismiss();
    };

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(dismissRunnable);
        if (popup != null) popup.dismiss();
        popup = null;
        super.onDetachedFromWindow();
    }
}
