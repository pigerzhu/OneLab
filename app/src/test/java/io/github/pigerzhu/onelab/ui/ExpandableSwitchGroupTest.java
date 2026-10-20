package io.github.pigerzhu.onelab.ui;

import static org.junit.Assert.assertEquals;

import android.view.View;

import org.junit.Test;

import io.github.pigerzhu.onelab.R;

public class ExpandableSwitchGroupTest {
    @Test
    public void mapsExpansionStateToChildVisibilityAndArrow() {
        assertEquals(View.GONE, ExpandableSwitchGroup.childVisibility(false));
        assertEquals(R.drawable.ic_chevron_right,
                ExpandableSwitchGroup.expansionIcon(false));
        assertEquals(View.VISIBLE, ExpandableSwitchGroup.childVisibility(true));
        assertEquals(R.drawable.ic_expand_more,
                ExpandableSwitchGroup.expansionIcon(true));
    }
}
