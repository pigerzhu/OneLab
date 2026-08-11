package io.github.pigerzhu.onelab.feature.applications;

import static org.junit.Assert.assertEquals;

import android.view.View;

import org.junit.Test;

import io.github.pigerzhu.onelab.R;

public class TikTokLargeScreenScreenTest {
    @Test
    public void mapsExpansionStateToChildVisibilityAndArrow() {
        assertEquals(View.GONE, TikTokLargeScreenScreen.childVisibility(false));
        assertEquals(R.drawable.ic_chevron_right,
                TikTokLargeScreenScreen.expansionIcon(false));
        assertEquals(View.VISIBLE, TikTokLargeScreenScreen.childVisibility(true));
        assertEquals(R.drawable.ic_expand_more,
                TikTokLargeScreenScreen.expansionIcon(true));
    }
}
