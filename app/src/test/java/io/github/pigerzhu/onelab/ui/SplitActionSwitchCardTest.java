package io.github.pigerzhu.onelab.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class SplitActionSwitchCardTest {
    @Test
    public void keepsNavigationAndSwitchInSeparateRegions() throws Exception {
        Path source = Path.of("src/main/java/io/github/pigerzhu/onelab/ui/"
                + "SplitActionSwitchCard.java");
        String text = new String(Files.readAllBytes(source));

        assertTrue(text.contains("actionRegion.setOnClickListener"));
        assertTrue(text.contains("divider"));
        assertTrue(text.contains("switchRegion.addView(toggle)"));
        assertFalse(text.contains("card.setOnClickListener"));
    }
}
