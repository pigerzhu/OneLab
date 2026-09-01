package io.github.pigerzhu.onelab.system;

import static org.junit.Assert.*;
import org.junit.Test;

public class QishuiMusicClientTest {
    @Test public void padRecordRecognizedOnlyForFlagFour() {
        assertTrue(QishuiMusicClient.isPadRecord("{\"support_feature\":{\"recognize_flag\":4}}"));
        assertFalse(QishuiMusicClient.isPadRecord("{\"support_feature\":{\"recognize_flag\":0}}"));
    }

    @Test public void enableRecordHasPadMarker() {
        assertTrue(QishuiMusicClient.enableRecord().contains("\"recognize_flag\":4"));
    }
}
