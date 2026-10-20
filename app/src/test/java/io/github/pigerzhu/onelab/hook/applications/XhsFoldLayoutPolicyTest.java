package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class XhsFoldLayoutPolicyTest {
    @Test
    public void rejectsEnabledVideoLayoutOnCoverScreen() {
        assertFalse(XhsFoldLayoutPolicy.isVideoLayoutEligible(true, 369));
    }

    @Test
    public void acceptsEnabledVideoLayoutOnLargeScreen() {
        assertTrue(XhsFoldLayoutPolicy.isVideoLayoutEligible(true, 600));
        assertTrue(XhsFoldLayoutPolicy.isVideoLayoutEligible(true, 707));
    }

    @Test
    public void rejectsDisabledVideoLayoutOnLargeScreen() {
        assertFalse(XhsFoldLayoutPolicy.isVideoLayoutEligible(false, 707));
    }
}
