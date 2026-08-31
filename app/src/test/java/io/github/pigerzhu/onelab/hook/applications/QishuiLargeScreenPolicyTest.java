package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QishuiLargeScreenPolicyTest {
    @Test
    public void padGateRequiresEnabledLargeWindow() {
        assertFalse(QishuiLargeScreenPolicy.shouldForcePad(false, 707, 787));
        assertFalse(QishuiLargeScreenPolicy.shouldForcePad(true, 369, 787));
        assertTrue(QishuiLargeScreenPolicy.shouldForcePad(true, 707, 787));
    }

    @Test
    public void playerLayoutRequiresBothSwitchesAndLargeWindow() {
        assertFalse(QishuiLargeScreenPolicy.shouldForcePlayerLayout(false, true, 707, 787));
        assertFalse(QishuiLargeScreenPolicy.shouldForcePlayerLayout(true, false, 707, 787));
        assertFalse(QishuiLargeScreenPolicy.shouldForcePlayerLayout(true, true, 369, 787));
        assertTrue(QishuiLargeScreenPolicy.shouldForcePlayerLayout(true, true, 707, 787));
    }

    @Test
    public void squareSixHundredDpWindowIsLarge() {
        assertTrue(QishuiLargeScreenPolicy.isLargeWindow(600, 600));
    }
}
