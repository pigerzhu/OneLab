package io.github.pigerzhu.onelab.hook.applications;

final class HupuEmbeddingStatePolicy {
    private HupuEmbeddingStatePolicy() {
    }

    static boolean isEnabled(String settingValue) {
        return !"0".equals(settingValue);
    }
}
