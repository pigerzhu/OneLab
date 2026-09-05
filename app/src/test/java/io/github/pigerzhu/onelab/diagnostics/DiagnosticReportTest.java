package io.github.pigerzhu.onelab.diagnostics;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DiagnosticReportTest {
    @Test
    public void reportFormatReflectsExpandedFeatureContract() {
        assertEquals(5, DiagnosticReport.REPORT_FORMAT);
    }

    @Test
    public void qishuiStateUsesOneLabPreferenceAndInstalledStatus() {
        assertEquals(
                "feature=apps.qishui_music_fold | enabled=true"
                        + " | package=com.luna.music | installed=false\n",
                DiagnosticReport.formatQishuiMusicState(true, false));
    }

    @Test
    public void qishuiPackageIsIncludedInAdditionalPackages() {
        assertArrayEquals(
                new String[] {"com.luna.music"},
                DiagnosticReport.additionalPackages());
    }
}
