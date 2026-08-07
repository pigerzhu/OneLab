package io.github.pigerzhu.onelab.hook.core;

import org.luckypray.dexkit.DexKitBridge;

public final class DexKitUtils {
    private DexKitUtils() {
    }

    public static DexKitBridge open(String apkPath) {
        return DexKitBridge.create(apkPath);
    }
}
