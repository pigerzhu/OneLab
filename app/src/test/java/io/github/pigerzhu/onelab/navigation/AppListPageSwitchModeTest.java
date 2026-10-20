package io.github.pigerzhu.onelab.navigation;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class AppListPageSwitchModeTest {
    @Test
    public void switchModeUsesOnlyTheTrailingSwitchAsAnAction() throws Exception {
        Path source = Path.of("src/main/java/io/github/pigerzhu/onelab/navigation/AppListPage.java");
        String text = new String(Files.readAllBytes(source));

        assertTrue(text.contains("interface AppSwitchProvider"));
        assertTrue(text.contains("holder.card.setOnClickListener(null)"));
        assertTrue(text.contains("holder.toggle.setOnCheckedChangeListener(null)"));
        assertTrue(text.contains("holder.toggle.setChecked(previous)"));
    }
}
