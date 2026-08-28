package io.github.pigerzhu.onelab.hook.applications;

import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class KuaishouAttachTargetTest {
    @Test
    public void usesAttachedApplicationInsteadOfEarlyBaseContext() {
        Object application = new Object();
        Object earlyBaseContext = new Object();

        assertSame(application,
                KuaishouAttachTarget.select(application, earlyBaseContext));
    }
}
