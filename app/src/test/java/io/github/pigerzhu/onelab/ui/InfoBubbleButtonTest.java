package io.github.pigerzhu.onelab.ui;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class InfoBubbleButtonTest {
    private static final Path SOURCE = Path.of("src/main/java/io/github/pigerzhu/onelab/ui/InfoBubbleButton.java");

    @Test
    public void popupIsAnchoredNonFocusableAndSelfDismisses() throws Exception {
        String source = new String(Files.readAllBytes(SOURCE));
        assertTrue(source.contains("DISPLAY_DURATION_MS = 4_000L"));
        assertTrue(source.contains("setFocusable(false)"));
        assertTrue(source.contains("showAsDropDown"));
        assertTrue(source.contains("postDelayed"));
        assertTrue(source.contains("dismiss()"));
        assertTrue(source.contains("onDetachedFromWindow"));
    }

    @Test
    public void qishuiScreenUsesReusableInfoBubble() throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "src/main/java/io/github/pigerzhu/onelab/feature/applications/QishuiMusicScreen.java")));
        assertTrue(source.contains("InfoBubbleButton"));
        assertTrue(source.contains("qishui_music_version_notice"));
    }
}
