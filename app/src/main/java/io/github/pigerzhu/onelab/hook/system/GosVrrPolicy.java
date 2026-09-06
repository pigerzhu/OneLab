package io.github.pigerzhu.onelab.hook.system;

/** Policy for keeping PUBG Mobile at the user's required 120 Hz floor. */
final class GosVrrPolicy {
    static final String PUBG_PACKAGE = "com.tencent.tmgp.pubgmhd";
    static final int REQUIRED_HZ = 120;

    private GosVrrPolicy() {
    }

    static boolean applies(String packageName, int requestedHz) {
        return PUBG_PACKAGE.equals(packageName) && requestedHz < REQUIRED_HZ;
    }

    static int normalize(String packageName, int requestedHz) {
        return applies(packageName, requestedHz) ? REQUIRED_HZ : requestedHz;
    }
}
