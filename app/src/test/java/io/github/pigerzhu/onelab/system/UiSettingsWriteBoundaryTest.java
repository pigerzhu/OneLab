package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

public class UiSettingsWriteBoundaryTest {
    private static final String[] BLOCKED = {
            "settings.setGlobal(",
            "settings.putGlobalQuietly(",
            "settings.putGlobalsQuietly(",
            "settings.setSecure(",
            "settings.setSecureWithToast(",
            "settings.putSystemQuietly("
    };

    @Test
    public void uiDoesNotCallSynchronousSettingsWrites() throws Exception {
        List<String> violations = new ArrayList<>();
        scan(Path.of("src/main/java/io/github/pigerzhu/onelab/feature"), violations);
        scan(Path.of("src/main/java/io/github/pigerzhu/onelab/navigation"), violations);

        assertTrue(String.join("\n", violations), violations.isEmpty());
    }

    private static void scan(Path root, List<String> violations) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (int index = 0; index < lines.size(); index++) {
                        for (String blocked : BLOCKED) {
                            if (lines.get(index).contains(blocked)) {
                                violations.add(path + ":" + (index + 1) + " " + blocked);
                            }
                        }
                    }
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }
}
