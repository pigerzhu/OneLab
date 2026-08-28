package io.github.pigerzhu.onelab.hook.applications;

final class KuaishouAttachTarget {
    private KuaishouAttachTarget() {
    }

    static Object select(Object attachedApplication, Object earlyBaseContext) {
        return attachedApplication;
    }
}
