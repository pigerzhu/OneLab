package io.github.pigerzhu.onelab.hook.samsung;

import java.util.List;

/** Stable Activity Embedding builder APIs that expose a float split ratio. */
final class ActivityEmbeddingRatioTargets {
    private static final List<String> BUILDER_CLASS_NAMES = List.of(
            "androidx.window.embedding.SplitPairRule$Builder",
            "androidx.window.embedding.SplitPlaceholderRule$Builder",
            "androidx.window.extensions.embedding.SplitPairRule$Builder",
            "androidx.window.extensions.embedding.SplitPlaceholderRule$Builder"
    );

    private ActivityEmbeddingRatioTargets() {
    }

    static List<String> builderClassNames() {
        return BUILDER_CLASS_NAMES;
    }
}
