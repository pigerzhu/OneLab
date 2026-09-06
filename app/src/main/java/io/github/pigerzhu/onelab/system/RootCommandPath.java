package io.github.pigerzhu.onelab.system;

import java.io.File;
import java.util.function.Predicate;

public final class RootCommandPath {
    private static final String[] CANDIDATES = {
            "/product/bin/su", "/system/bin/su", "/system/xbin/su",
            "/sbin/su", "/debug_ramdisk/su"
    };

    private RootCommandPath() {
    }

    public static String resolve() {
        return select(path -> new File(path).canExecute());
    }

    static String select(Predicate<String> executable) {
        for (String candidate : CANDIDATES) {
            if (executable.test(candidate)) return candidate;
        }
        return "su";
    }
}
