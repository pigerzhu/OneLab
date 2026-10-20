package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class SettingsStoreTest {
    @Test
    public void shellQuotePreservesStructuredValues() {
        assertEquals(
                "'{\"settings\":{\"item\":\"a;b\"}}'",
                SettingsStore.shellQuote("{\"settings\":{\"item\":\"a;b\"}}")
        );
    }

    @Test
    public void shellQuoteEscapesSingleQuotes() {
        assertEquals("'a'\\''b'", SettingsStore.shellQuote("a'b"));
    }

    @Test
    public void globalWriteCommandGrantsPermissionAndWritesBatchInOneShell() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("onelab_gpu_range_min_mhz", "80");
        values.put("onelab_gpu_range_max_mhz", "1000");

        assertEquals(
                "(pm grant 'io.github.pigerzhu.onelab' "
                        + "'android.permission.WRITE_SECURE_SETTINGS' >/dev/null 2>&1 || true)"
                        + " && settings put global 'onelab_gpu_range_min_mhz' '80'"
                        + " && settings put global 'onelab_gpu_range_max_mhz' '1000'",
                SettingsStore.globalWriteCommand("io.github.pigerzhu.onelab", values));
    }
}
