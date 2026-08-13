package io.github.pigerzhu.onelab.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.pigerzhu.onelab.R;

public final class ExpandableSwitchGroup extends LinearLayout {
    private static final long ANIMATION_DURATION_MS = 220;
    private static final PathInterpolator ANIMATION_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    private final ImageButton arrow;
    private final View child;
    private boolean expanded;
    private ValueAnimator animator;

    public ExpandableSwitchGroup(
            Context context,
            Ui ui,
            CharSequence title,
            CharSequence subtitle,
            MaterialSwitch parentSwitch,
            View child) {
        super(context);
        this.child = child;
        setOrientation(VERTICAL);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(VERTICAL);
        copy.addView(ui.text(title.toString(), 16, true, ui.colorOnSurface));
        if (subtitle != null) {
            copy.addView(ui.text(subtitle.toString(), 13, false, ui.colorOnSurfaceVariant));
        }
        row.addView(copy, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        arrow = new ImageButton(context);
        arrow.setImageTintList(ColorStateList.valueOf(ui.colorOnSurfaceVariant));
        arrow.setBackgroundColor(Color.TRANSPARENT);
        arrow.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        arrow.setOnClickListener(view -> setExpanded(!expanded, true));
        row.addView(arrow, new LayoutParams(ui.dp(40), ui.dp(48)));
        row.addView(parentSwitch);
        addView(row);
        addView(child);

        setExpanded(false, false);
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded, boolean animate) {
        this.expanded = expanded;
        updateArrow();

        ValueAnimator previousAnimator = animator;
        animator = null;
        if (previousAnimator != null) {
            previousAnimator.cancel();
        }

        if (!animate || !isLaidOut()) {
            settleChild(expanded);
            return;
        }

        int startHeight = child.getVisibility() == View.GONE ? 0 : child.getHeight();
        float startAlpha = child.getVisibility() == View.GONE ? 0f : child.getAlpha();
        int endHeight = expanded ? measureChildHeight() : 0;
        float endAlpha = expanded ? 1f : 0f;

        if (startHeight == endHeight && startAlpha == endAlpha) {
            settleChild(expanded);
            return;
        }

        child.setVisibility(View.VISIBLE);
        setChildHeight(startHeight);
        child.setAlpha(startAlpha);

        ValueAnimator nextAnimator = ValueAnimator.ofFloat(0f, 1f);
        animator = nextAnimator;
        nextAnimator.setDuration(ANIMATION_DURATION_MS);
        nextAnimator.setInterpolator(ANIMATION_INTERPOLATOR);
        nextAnimator.addUpdateListener(valueAnimator -> {
            float progress = (float) valueAnimator.getAnimatedValue();
            setChildHeight(Math.round(startHeight + (endHeight - startHeight) * progress));
            child.setAlpha(startAlpha + (endAlpha - startAlpha) * progress);
        });
        nextAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator != nextAnimator) {
                    return;
                }
                animator = null;
                settleChild(ExpandableSwitchGroup.this.expanded);
            }
        });
        nextAnimator.start();
    }

    private int measureChildHeight() {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        int currentHeight = params.height;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        child.measure(
                MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int measuredHeight = child.getMeasuredHeight();
        params.height = currentHeight;
        return measuredHeight;
    }

    private void setChildHeight(int height) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.height = height;
        child.setLayoutParams(params);
    }

    private void settleChild(boolean expanded) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        child.setLayoutParams(params);
        child.setAlpha(1f);
        child.setVisibility(childVisibility(expanded));
    }

    private void updateArrow() {
        arrow.setImageResource(expansionIcon(expanded));
        int description = expanded ? R.string.menu_collapse : R.string.menu_expand;
        arrow.setContentDescription(getContext().getString(description));
        arrow.setTooltipText(getContext().getString(description));
    }

    static int childVisibility(boolean expanded) {
        return expanded ? View.VISIBLE : View.GONE;
    }

    static int expansionIcon(boolean expanded) {
        return expanded ? R.drawable.ic_expand_more : R.drawable.ic_chevron_right;
    }
}
