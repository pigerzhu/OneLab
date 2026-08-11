package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InstagramMobileConfigPolicyTest {
    @Test
    public void forcesOnlyTwoPaneCommentGates() {
        assertTrue(InstagramMobileConfigPolicy.shouldForce(36325123995030568L));
        assertTrue(InstagramMobileConfigPolicy.shouldForce(36325123993916442L));
        assertFalse(InstagramMobileConfigPolicy.shouldForce(0L));
        assertFalse(InstagramMobileConfigPolicy.shouldForce(36325123995030569L));
    }
}
