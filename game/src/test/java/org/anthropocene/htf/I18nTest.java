package org.anthropocene.htf;

import org.anthropocene.htf.core.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {
    @AfterEach void reset() { I18n.setLanguage(I18n.EN); }

    @Test
    void englishIsLeftAlone() {
        I18n.setLanguage(I18n.EN);
        assertEquals("Done", I18n.t("Done"));
        assertEquals("Day 3 of 120", I18n.f("Day {} of {}", 3, 120));
    }

    @Test
    void exactAndTemplateTranslations() {
        I18n.setLanguage(I18n.BN);
        assertEquals("সম্পন্ন", I18n.t("Done"));
        assertEquals("দিন ১২ / ১২০", I18n.t("Day 12 of 120"));
        assertEquals("দিন ৫", I18n.t("Day 5"), "the most specific template wins, not the first one in the file");
        assertEquals("দিন ৩ / ১২০", I18n.f("Day {} of {}", 3, 120));
    }

    @Test
    void placeholdersCanBeReordered() {
        I18n.setLanguage(I18n.BN);
        assertEquals("৭টির মধ্যে ৩টি অর্জিত", I18n.t("3 of 7 earned"));
    }

    @Test
    void unknownTextStaysEnglishButNumbersBecomeBengali() {
        I18n.setLanguage(I18n.BN);
        assertEquals("No translation here", I18n.t("No translation here"));
        assertEquals("F3 key ১২", I18n.digits("F3 key 12"), "key names such as F3 keep their Latin digit");
    }

    @Test
    void datesUseBengaliMonths() {
        I18n.setLanguage(I18n.BN);
        assertEquals("২৫ জুলাই ২০২২", I18n.t("25 Jul 2022"));
    }

    @Test
    void linesJoinedBySeparatorsAreTranslatedPartByPart() {
        I18n.setLanguage(I18n.BN);
        String s = I18n.t("Scout mode   |   planted 2022-07-25");
        assertTrue(s.contains("স্কাউট মোড"), s);
        assertTrue(s.contains("রোপণ"), s);
    }

    @Test
    void everyEntryIsConsistent() throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(I18nTest.class.getResourceAsStream("/lang/bn.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int cut = line.indexOf(" => ");
                assertTrue(cut > 0, "missing ' => ' in: " + line);
                String key = line.substring(0, cut), value = line.substring(cut + 4);
                int in = count(key, "{}") + count(key, "{#}"), out = count(value, "{}") + (int) java.util.regex.Pattern.compile("\\{[0-9]+}").matcher(value).results().count();
                assertEquals(in, out, "placeholder count differs in: " + line);
                assertTrue(value.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF), "no Bengali in: " + line);
            }
        }
    }

    private static int count(String s, String what) {
        int n = 0;
        for (int i = s.indexOf(what); i >= 0; i = s.indexOf(what, i + what.length())) n++;
        return n;
    }
}
