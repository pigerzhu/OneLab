package io.github.pigerzhu.onelab.feature.window;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class RecentsLayoutScreenContractTest {
    private static final String[] STRING_KEYS = {
            "recents_layout_title",
            "recents_layout_summary",
            "recents_layout_enable_title",
            "recents_layout_enable_summary",
            "recents_layout_main_display",
            "recents_layout_cover_display",
            "recents_layout_list",
            "recents_layout_grid",
            "recents_layout_stack",
            "recents_layout_vertical",
            "recents_layout_slim",
            "recents_layout_tilt_stack"
    };

    @Test
    public void systemUiPageOwnsRecentsLayoutEntry() throws Exception {
        String source = read(Path.of(
                "src/main/java/io/github/pigerzhu/onelab/MainActivity.java"));
        assertTrue(source.contains("new RecentsLayoutScreen(this, ui, settings)"));
        assertTrue(source.contains("root.addView(recentsLayoutScreen.entryCard())"));
    }

    @Test
    public void screenOffersEverySamsungLayoutValue() throws Exception {
        String source = read(Path.of(
                "src/main/java/io/github/pigerzhu/onelab/feature/window/RecentsLayoutScreen.java"));
        List<String> constants = List.of(
                "LAYOUT_LIST",
                "LAYOUT_GRID",
                "LAYOUT_STACK",
                "LAYOUT_VERTICAL",
                "LAYOUT_SLIM",
                "LAYOUT_TILT_STACK");
        for (String constant : constants) {
            assertTrue(constant, source.contains(constant));
        }
    }

    @Test
    public void screenUsesExpandableSwitchAndIndependentMaterialSelectionMenu() throws Exception {
        String screen = read(Path.of(
                "src/main/java/io/github/pigerzhu/onelab/feature/window/RecentsLayoutScreen.java"));
        String menu = read(Path.of(
                "src/main/java/io/github/pigerzhu/onelab/ui/MaterialSelectionMenu.java"));
        assertTrue(screen.contains("ExpandableSwitchGroup"));
        assertTrue(screen.contains("setExpanded(false, false)"));
        assertTrue(screen.contains("MaterialSelectionMenu"));
        assertTrue(!screen.contains("PopupMenu"));
        assertTrue(menu.contains("MaterialCardView"));
        assertTrue(menu.contains("showAtLocation"));
        assertTrue(menu.contains("setRadius"));
    }

    @Test
    public void requiredLocalesContainMatchingStrings() throws Exception {
        Path[] files = {
                Path.of("src/main/res/values/strings.xml"),
                Path.of("src/main/res/values-zh-rTW/strings.xml"),
                Path.of("src/main/res/values-en/strings.xml")
        };
        for (Path file : files) {
            NodeList strings = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file.toFile()).getElementsByTagName("string");
            for (String key : STRING_KEYS) {
                boolean found = false;
                for (int index = 0; index < strings.getLength(); index++) {
                    Element element = (Element) strings.item(index);
                    if (key.equals(element.getAttribute("name"))) {
                        found = true;
                        break;
                    }
                }
                assertTrue(file + " missing " + key, found);
            }
        }
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
