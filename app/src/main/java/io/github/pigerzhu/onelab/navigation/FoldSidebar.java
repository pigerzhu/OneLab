package io.github.pigerzhu.onelab.navigation;

import io.github.pigerzhu.onelab.MainActivity;
import io.github.pigerzhu.onelab.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import io.github.pigerzhu.onelab.ui.Ui;

/** Samsung-style navigation rail used only on the unfolded large display. */
public final class FoldSidebar {
    private static final PathInterpolator WIDTH_EASING =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    public interface Listener {
        void onSectionSelected(int section);

        void onAppearanceSelected();
    }

    private static final long WIDTH_ANIMATION_MS = 220L;

    private final MainActivity host;
    private final Ui ui;
    private final Listener listener;
    private final MaterialCardView card;
    private final LinearLayout content;
    private boolean expanded;
    private boolean targetExpanded;
    private ValueAnimator widthAnimator;
    private ImageButton settingsIcon;
    private int selectedSection;

    public FoldSidebar(MainActivity host, Ui ui, int selectedSection, Listener listener) {
        this.host = host;
        this.ui = ui;
        this.listener = listener;
        this.selectedSection = selectedSection;
        expanded = selectedSection < 0;
        targetExpanded = expanded;

        card = new SidebarCard(host);
        card.setRadius(ui.dp(24));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        card.setCardBackgroundColor(ui.colorSurfaceContainer);
        card.setLayoutParams(shellParams(widthFor(expanded)));

        content = new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
        card.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsIcon = iconButton(R.drawable.ic_settings, host.getString(R.string.appearance_settings),
                v -> listener.onAppearanceSelected());
        card.addView(settingsIcon, new ViewGroup.LayoutParams(ui.dp(52), ui.dp(52)));
        card.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) ->
                positionSettingsIcon(right - left));
        rebuild();
    }

    public View view() {
        return card;
    }

    public void setSelectedSection(int section) {
        selectedSection = section;
        rebuild();
    }

    public void expand() {
        setExpanded(true, true);
    }

    public void collapse() {
        setExpanded(false, true);
    }

    public boolean isExpanded() {
        return targetExpanded;
    }

    public void setExpandedImmediately(boolean value) {
        setExpanded(value, false);
    }

    private void setExpanded(boolean value, boolean animate) {
        if (targetExpanded == value) return;
        targetExpanded = value;
        if (widthAnimator != null) {
            widthAnimator.cancel();
            widthAnimator = null;
        }
        int targetWidth = widthFor(value);
        if (!animate) {
            expanded = value;
            rebuild();
            card.getLayoutParams().width = targetWidth;
            card.requestLayout();
            positionSettingsIcon(targetWidth);
            return;
        }
        if (value) {
            expanded = true;
            rebuild();
        }
        int startWidth = card.getWidth() > 0 ? card.getWidth() : card.getLayoutParams().width;
        float startX = settingsIcon.getX();
        float startY = settingsIcon.getY();
        float endX = settingsIconX(value, targetWidth);
        float endY = settingsIconY(value, card.getHeight());
        ValueAnimator animator = ValueAnimator.ofInt(startWidth, targetWidth);
        widthAnimator = animator;
        animator.setDuration(WIDTH_ANIMATION_MS);
        animator.setInterpolator(WIDTH_EASING);
        animator.addUpdateListener(valueAnimator -> {
            card.getLayoutParams().width = (Integer) valueAnimator.getAnimatedValue();
            card.requestLayout();
            float fraction = valueAnimator.getAnimatedFraction();
            settingsIcon.setX(startX + (endX - startX) * fraction);
            settingsIcon.setY(startY + (endY - startY) * fraction);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (widthAnimator == animation) {
                    widthAnimator = null;
                }
                if (!targetExpanded) {
                    expanded = false;
                    rebuild();
                }
                positionSettingsIcon(card.getWidth());
            }
        });
        animator.start();
    }

    private void rebuild() {
        content.removeAllViews();
        content.addView(header());
        addGap(16);
        addSection(R.drawable.ic_home_connectivity,
                host.getString(R.string.section_network), Ui.HOME_NETWORK);
        addSection(R.drawable.ic_home_performance,
                host.getString(R.string.section_performance), Ui.HOME_PERFORMANCE);
        addSection(R.drawable.ic_home_system,
                host.getString(R.string.section_system_ui), Ui.HOME_SYSTEM);
        addSection(R.drawable.ic_home_apps,
                host.getString(R.string.section_apps), Ui.HOME_APPS);
        addSection(R.drawable.ic_home_experiments,
                host.getString(R.string.section_experiments), Ui.HOME_EXPERIMENTS);

        Space spacer = new Space(host);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private View header() {
        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(iconButton(R.drawable.ic_menu, host.getString(expanded ? R.string.menu_collapse : R.string.menu_expand),
                v -> setExpanded(!targetExpanded, true)), iconParams());
        return header;
    }

    private void addSection(int iconRes, String label, int section) {
        boolean selected = selectedSection == section;
        LinearLayout row = new LinearLayout(host);
        row.setGravity(expanded ? Gravity.CENTER_VERTICAL : Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(label);
        row.setOnClickListener(v -> {
            selectedSection = section;
            listener.onSectionSelected(section);
        });
        row.setBackground(rowBackground(selected));
        if (expanded) {
            row.setPadding(ui.dp(14), ui.dp(8), ui.dp(10), ui.dp(8));
        } else {
            row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        }
        ImageView icon = new ImageView(host);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(
                selected ? ui.colorOnPrimaryContainer : ui.colorOnSurfaceVariant));
        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));

        if (expanded) {
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textParams.setMarginStart(ui.dp(18));
            TextView labelView = ui.text(label, 18, selected, selected
                    ? ui.colorOnPrimaryContainer : ui.colorOnSurface);
            labelView.setSingleLine(true);
            labelView.setEllipsize(null);
            labelView.setHorizontallyScrolling(true);
            row.addView(labelView, textParams);
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(58));
        rowParams.setMargins(0, 0, 0, ui.dp(6));
        content.addView(row, rowParams);
    }

    private ImageButton iconButton(int iconRes, String description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(host);
        button.setImageResource(iconRes);
        button.setImageTintList(ColorStateList.valueOf(ui.colorOnSurface));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        button.setContentDescription(description);
        button.setTooltipText(description);
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rowBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? ui.colorPrimaryContainer : Color.TRANSPARENT);
        background.setCornerRadius(ui.dp(12));
        return background;
    }

    private LinearLayout.LayoutParams shellParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(ui.dp(12), statusBarTopMargin(), ui.dp(6), ui.dp(12));
        return params;
    }

    private int statusBarTopMargin() {
        int resourceId = host.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        int statusBarHeight = resourceId == 0
                ? ui.dp(24)
                : host.getResources().getDimensionPixelSize(resourceId);
        return statusBarHeight + ui.dp(8);
    }

    private LinearLayout.LayoutParams iconParams() {
        return new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52));
    }

    private int widthFor(boolean value) {
        return ui.dp(value ? 300 : 84);
    }

    private float settingsIconX(boolean expandedState, int cardWidth) {
        if (expandedState) {
            return cardWidth - ui.dp(12) - ui.dp(52);
        }
        int contentWidth = cardWidth - ui.dp(12) * 2;
        return ui.dp(12) + (contentWidth - ui.dp(52)) / 2f;
    }

    private float settingsIconY(boolean expandedState, int cardHeight) {
        if (expandedState) {
            return ui.dp(14);
        }
        return cardHeight - ui.dp(14) - ui.dp(52);
    }

    private void positionSettingsIcon(int cardWidth) {
        if (settingsIcon == null || widthAnimator != null) {
            return;
        }
        int cardHeight = card.getHeight();
        if (cardWidth <= 0 || cardHeight <= 0) {
            return;
        }
        settingsIcon.setX(settingsIconX(expanded, cardWidth));
        settingsIcon.setY(settingsIconY(expanded, cardHeight));
    }

    private final class SidebarCard extends MaterialCardView {
        private final int touchSlop;
        private float downX;
        private float downY;
        private boolean horizontalSwipe;

        SidebarCard(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
            setClickable(true);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    horizontalSwipe = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isHorizontalSwipe(event)) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    horizontalSwipe = false;
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    isHorizontalSwipe(event);
                    return true;
                case MotionEvent.ACTION_UP:
                    float distance = event.getRawX() - downX;
                    if (horizontalSwipe && Math.abs(distance) >= ui.dp(36)) {
                        setExpanded(distance > 0, true);
                    }
                    horizontalSwipe = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    horizontalSwipe = false;
                    return true;
                default:
                    return true;
            }
        }

        private boolean isHorizontalSwipe(MotionEvent event) {
            float moveX = event.getRawX() - downX;
            float moveY = event.getRawY() - downY;
            if (!horizontalSwipe
                    && Math.abs(moveX) > touchSlop
                    && Math.abs(moveX) > Math.abs(moveY) * 1.25f) {
                horizontalSwipe = true;
            }
            return horizontalSwipe;
        }
    }

    private void addGap(int dp) {
        Space gap = new Space(host);
        content.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(dp)));
    }
}
