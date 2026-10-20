package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InstagramMobileConfigPolicyTest {
    @Test
    public void forcesOnlyTwoPaneCommentGates() {
        assertTrue(InstagramMobileConfigPolicy.shouldForce(
                true, 36325123995030568L));
        assertTrue(InstagramMobileConfigPolicy.shouldForce(
                true, 36325123993916442L));
        assertFalse(InstagramMobileConfigPolicy.shouldForce(true, 0L));
        assertFalse(InstagramMobileConfigPolicy.shouldForce(
                true, 36325123995030569L));
    }

    @Test
    public void preservesInstagramGatesWhenDisabled() {
        assertFalse(InstagramMobileConfigPolicy.shouldForce(
                false, 36325123995030568L));
        assertFalse(InstagramMobileConfigPolicy.shouldForce(
                false, 36325123993916442L));
    }

    @Test
    public void acceptsOnlyInstagramMainProcess() {
        assertTrue(InstagramMobileConfigPolicy.isMainProcess(
                "com.instagram.android", "com.instagram.android"));
        assertFalse(InstagramMobileConfigPolicy.isMainProcess(
                "com.instagram.android", "com.instagram.android:browser"));
        assertFalse(InstagramMobileConfigPolicy.isMainProcess(
                "com.example.other", "com.example.other"));
    }
}
