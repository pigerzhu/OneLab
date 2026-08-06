package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RuntimeCompatibilityReportTest {
    @Test
    public void filterHookLogKeepsOnlyOneLabEvidence() {
        String raw = "unrelated account data\n"
                + "OneLab/SamsungSplitRules: installed One UI 8.5 controller\n"
                + "OneLab: Matched SDHMS profile: One UI 8.5\n"
                + "another unrelated line";

        String filtered = RuntimeCompatibilityReport.filterHookLog(raw);

        assertTrue(filtered.contains("SamsungSplitRules"));
        assertTrue(filtered.contains("Matched SDHMS profile"));
        assertFalse(filtered.contains("account data"));
        assertFalse(filtered.contains("another unrelated"));
    }

    @Test
    public void filterHookLogHandlesUnavailableInput() {
        assertTrue(RuntimeCompatibilityReport.filterHookLog(null).isEmpty());
        assertTrue(RuntimeCompatibilityReport.filterHookLog("").isEmpty());
    }

    @Test
    public void filterHookLogKeepsFailureStackContext() {
        String raw = "OneLab: SDHMS controller hook failed\n"
                + "java.lang.NoSuchMethodError: l5.f5.F(int)\n"
                + "    at de.robv.android.xposed.XposedBridge.invokeOriginalMethod()\n"
                + "unrelated tail after context";

        String filtered = RuntimeCompatibilityReport.filterHookLog(raw);

        assertTrue(filtered.contains("NoSuchMethodError"));
        assertTrue(filtered.contains("XposedBridge.invokeOriginalMethod"));
    }

    @Test
    public void filterHookLogKeepsGpuRangeEvidence() {
        String raw = "OneLab: GPU range DVFS active: 231-770MHz\n"
                + "OneLab: GPU range DVFS minimum unavailable\n";

        String filtered = RuntimeCompatibilityReport.filterHookLog(raw);

        assertTrue(filtered.contains("GPU range DVFS active: 231-770MHz"));
        assertTrue(filtered.contains("GPU range DVFS minimum unavailable"));
    }
}
