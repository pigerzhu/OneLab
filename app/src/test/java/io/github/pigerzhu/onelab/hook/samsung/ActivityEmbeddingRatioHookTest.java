package io.github.pigerzhu.onelab.hook.samsung;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import io.github.pigerzhu.onelab.hook.core.HookConstants;

public class ActivityEmbeddingRatioHookTest {
    @Test
    public void routesRatioOverridesThroughJetpackAndWindowExtensionsBuilders()
            throws Exception {
        Class<?> targets;
        try {
            targets = Class.forName(
                    "io.github.pigerzhu.onelab.hook.samsung.ActivityEmbeddingRatioTargets");
        } catch (ClassNotFoundException missingRoute) {
            fail("Activity Embedding ratio target catalog is missing");
            return;
        }
        Method method = targets.getDeclaredMethod("builderClassNames");
        method.setAccessible(true);

        assertEquals(List.of(
                "androidx.window.embedding.SplitPairRule$Builder",
                "androidx.window.embedding.SplitPlaceholderRule$Builder",
                "androidx.window.extensions.embedding.SplitPairRule$Builder",
                "androidx.window.extensions.embedding.SplitPlaceholderRule$Builder"
        ), method.invoke(null));
    }

    @Test
    public void recognizesWeComAsAnActivityEmbeddingCandidate() {
        assertEquals(true,
                HookConstants.isActivityEmbeddingCandidate("com.tencent.wework"));
    }
}
