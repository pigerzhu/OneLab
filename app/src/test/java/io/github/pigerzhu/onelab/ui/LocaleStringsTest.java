package io.github.pigerzhu.onelab.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Keeps the four shipped languages structurally in sync. The default resource set is
 * Simplified Chinese; app_name intentionally exists only there.
 */
public final class LocaleStringsTest {
    private static final String[] LOCALE_DIRS =
            {"values", "values-en", "values-zh-rTW", "values-ko"};
    private static final Set<String> DEFAULT_ONLY_KEYS = Set.of("app_name");
    private static final Pattern FORMAT_ARGUMENT = Pattern.compile("%\\d+\\$[sdf]");

    @Test
    public void everyLocaleShipsTheSameStringKeys() throws IOException {
        File resDirectory = locateResDirectory();
        Map<String, Set<String>> keysByLocale = new TreeMap<>();
        Map<String, Map<String, String>> formatsByLocale = new TreeMap<>();
        for (String localeDir : LOCALE_DIRS) {
            File file = new File(resDirectory, localeDir + "/strings.xml");
            assertTrue("missing " + file, file.isFile());
            LocaleStrings strings = parse(file);
            keysByLocale.put(localeDir, strings.keys);
            formatsByLocale.put(localeDir, strings.formatArguments);
        }
        Set<String> defaultKeys = keysByLocale.get("values");
        defaultKeys.removeAll(DEFAULT_ONLY_KEYS);
        for (String localeDir : LOCALE_DIRS) {
            if (localeDir.equals("values")) continue;
            // app_name may exist in any locale file; every other key must be identical.
            keysByLocale.get(localeDir).removeAll(DEFAULT_ONLY_KEYS);
            Set<String> missing = new HashSet<>(defaultKeys);
            missing.removeAll(keysByLocale.get(localeDir));
            Set<String> extra = new HashSet<>(keysByLocale.get(localeDir));
            extra.removeAll(defaultKeys);
            assertTrue(localeDir + " is missing keys " + missing, missing.isEmpty());
            assertTrue(localeDir + " has unexpected keys " + extra, extra.isEmpty());
        }
    }

    @Test
    public void positionalFormatArgumentsMatchAcrossLocales() throws IOException {
        File resDirectory = locateResDirectory();
        Map<String, Map<String, String>> formatsByLocale = new TreeMap<>();
        for (String localeDir : LOCALE_DIRS) {
            formatsByLocale.put(localeDir, parse(new File(resDirectory,
                    localeDir + "/strings.xml")).formatArguments);
        }
        Map<String, String> defaultFormats = formatsByLocale.get("values");
        for (String localeDir : LOCALE_DIRS) {
            if (localeDir.equals("values")) continue;
            for (Map.Entry<String, String> entry : defaultFormats.entrySet()) {
                String localized = formatsByLocale.get(localeDir).get(entry.getKey());
                assertNotNull(localeDir + " lost format key " + entry.getKey(), localized);
                assertTrue(localeDir + " format mismatch for " + entry.getKey()
                                + ": " + localized + " vs " + entry.getValue(),
                        localized.equals(entry.getValue()));
            }
        }
    }

    @Test
    public void newScreenRangeKeysExistInEveryLocale() throws IOException {
        File resDirectory = locateResDirectory();
        String[] required = {
                "refresh_rate_screen_range_title",
                "refresh_rate_screen_inner",
                "refresh_rate_screen_outer",
                "refresh_rate_screen_limit_switch",
                "refresh_rate_screen_follow_system",
                "refresh_rate_screen_min",
                "refresh_rate_screen_max",
        };
        for (String localeDir : LOCALE_DIRS) {
            Set<String> keys = parse(new File(resDirectory,
                    localeDir + "/strings.xml")).keys;
            for (String key : required) {
                assertTrue(localeDir + " is missing " + key, keys.contains(key));
            }
        }
    }

    private static File locateResDirectory() throws IOException {
        File directory = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        for (int depth = 0; depth < 8 && directory != null; depth++) {
            File candidate = new File(directory, "app/src/main/res");
            if (candidate.isDirectory()) return candidate;
            directory = directory.getParentFile();
        }
        throw new IOException("cannot locate app/src/main/res from "
                + System.getProperty("user.dir"));
    }

    private static LocaleStrings parse(File file) throws IOException {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file);
            NodeList nodes = document.getDocumentElement().getChildNodes();
            LocaleStrings strings = new LocaleStrings();
            for (int index = 0; index < nodes.getLength(); index++) {
                Node node = nodes.item(index);
                if (!(node instanceof Element)) continue;
                Element element = (Element) node;
                String name = element.getAttribute("name");
                if (name.isEmpty()) continue;
                String kind = element.getTagName();
                if ("string".equals(kind)) {
                    strings.keys.add(name);
                    String text = element.getTextContent();
                    Matcher matcher = FORMAT_ARGUMENT.matcher(text);
                    List<String> arguments = new ArrayList<>();
                    while (matcher.find()) arguments.add(matcher.group());
                    if (!arguments.isEmpty()) {
                        arguments.sort(String::compareTo);
                        strings.formatArguments.put(name, String.join(",", arguments));
                    }
                } else if ("plurals".equals(kind)) {
                    strings.keys.add(name);
                }
            }
            return strings;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("cannot parse " + file, error);
        }
    }

    private static final class LocaleStrings {
        final Set<String> keys = new HashSet<>();
        final Map<String, String> formatArguments = new TreeMap<>();
    }
}
