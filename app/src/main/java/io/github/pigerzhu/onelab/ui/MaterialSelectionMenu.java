package io.github.pigerzhu.onelab.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

/** A rounded Material 3 selection popup positioned below the user's touch point. */
public final class MaterialSelectionMenu {
    public static final class Option {
        public final int value;
        public final CharSequence label;

        public Option(int value, CharSequence label) {
            this.value = value;
            this.label = label;
        }
    }

    private final View anchor;
    private final Ui ui;
    private PopupWindow popup;

    public MaterialSelectionMenu(View anchor, Ui ui) {
        this.anchor = anchor;
        this.ui = ui;
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                dismiss();
            }
        });
    }

    public void show(float rawTouchX, List<Option> options, int selectedValue,
            OnOptionSelected listener) {
        if (isShowing()) {
            dismiss();
            return;
        }
        MaterialCardView card = new MaterialCardView(anchor.getContext());
        card.setRadius(ui.dp(20));
        card.setCardElevation(ui.dp(6));
        card.setStrokeWidth(ui.dp(1));
        card.setStrokeColor(ui.colorSurfaceContainer);
        card.setCardBackgroundColor(ui.colorSurfaceContainer);

        LinearLayout list = new LinearLayout(anchor.getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
        card.addView(list, new ViewGroup.LayoutParams(
                ui.dp(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        for (Option option : options) {
            MaterialTextView row = ui.text(option.label.toString(), 16, false,
                    ui.colorOnSurface);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(16), 0, ui.dp(16), 0);
            row.setMinHeight(ui.dp(48));
            row.setBackgroundColor(option.value == selectedValue
                    ? ui.colorPrimaryContainer : Color.TRANSPARENT);
            row.setOnClickListener(view -> {
                listener.onSelected(option.value);
                dismiss();
            });
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(48)));
        }

        card.measure(
                View.MeasureSpec.makeMeasureSpec(ui.dp(220), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        float touchX = rawTouchX > 0 ? rawTouchX : location[0] + anchor.getWidth() / 2f;
        int left = Math.round(touchX - ui.dp(110));
        left = Math.max(ui.dp(8), Math.min(left, screenWidth - ui.dp(228)));
        int anchorOffsetX = left - location[0];
        popup = new PopupWindow(card, ui.dp(220), card.getMeasuredHeight(), false);
        popup.setFocusable(false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(false);
        popup.setTouchModal(false);
        popup.setElevation(ui.dp(6));
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setTranslationY(-ui.dp(8));
        popup.showAsDropDown(anchor, anchorOffsetX, ui.dp(4), Gravity.START);
        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public void dismiss() {
        if (popup != null) popup.dismiss();
        popup = null;
    }

    public boolean isShowing() {
        return popup != null && popup.isShowing();
    }

    public interface OnOptionSelected {
        void onSelected(int value);
    }
}
