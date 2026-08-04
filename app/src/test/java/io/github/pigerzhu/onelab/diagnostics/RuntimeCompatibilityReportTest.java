package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RuntimeCompatibilityReportTest {
    @Test
    public void filterHookLogKeepsOnlyOneLabEvidence() {
        String raw = "unrelated account data\n"
                + "OneLab/SamsungSplitRules: installed One UI 8.5 controller\n"
                + "OneLab/SamsungSplitRules: snapshot snapshot_wechat=true\n"
                + "OneLab/ActivityEmbeddingRatio: installed for com.tencent.mm\n"
                + "OneLab: Matched SDHMS profile: One UI 8.5\n"
                + "another unrelated line";

        String filtered = RuntimeCompatibilityReport.filterHookLog(raw);

        assertTrue(filtered.contains("SamsungSplitRules"));
        assertTrue(filtered.contains("snapshot_wechat=true"));
        assertTrue(filtered.contains("installed for com.tencent.mm"));
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
}
