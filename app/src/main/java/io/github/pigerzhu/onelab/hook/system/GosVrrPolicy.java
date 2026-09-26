package io.github.pigerzhu.onelab.hook.system;

/** Policy for applying the optional 120 Hz floor to GOS VRR targets. */
final class GosVrrPolicy {
    static final String PUBG_PACKAGE = "com.tencent.tmgp.pubgmhd";
    static final int REQUIRED_HZ = 120;

    private GosVrrPolicy() {
    }

    static boolean applies(boolean enabled, String packageName, int requestedHz) {
        return enabled && packageName != null && !packageName.isEmpty()
                && requestedHz > 0 && requestedHz < REQUIRED_HZ;
    }

    static int normalize(boolean enabled, String packageName, int requestedHz) {
        return applies(enabled, packageName, requestedHz) ? REQUIRED_HZ : requestedHz;
    }
}
