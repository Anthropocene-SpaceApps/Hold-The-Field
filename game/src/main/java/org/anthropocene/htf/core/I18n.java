package org.anthropocene.htf.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interface language. English strings are the keys: {@code t("Done")} returns the Bengali text when Bengali is
 * selected and a translation exists, otherwise the English text. Keys may contain {@code {}} placeholders, which
 * lets finished strings such as "Day 12 of 120" be matched against the template "Day {} of {}".
 */
public final class I18n {
    public static final String EN = "en", BN = "bn";
    public static final String[] LANGUAGES = {EN, BN};
    public static final String[] LANGUAGE_NAMES = {"English", "বাংলা"};

    private static final String[] MONTHS_EN = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] MONTHS_BN = {"জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"};
    private static final char[] BN_DIGITS = {'০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'};
    private static final Pattern DATE = Pattern.compile("(\\d{1,2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");
    private static final Pattern SEPARATOR = Pattern.compile("(   \\|   |  /  |\\s{2,}|\n)");

    private record Template(Pattern pattern, String target, int literal) {}

    private static final Map<String, String> exact = new HashMap<>();
    private static final List<Template> templates = new ArrayList<>();
    private static final Map<String, String> memo = new HashMap<>();
    private static String language = EN;
    private static final java.util.Set<String> missing = new java.util.LinkedHashSet<>();     // dev aid: -Dhtf.i18n.missing=file
    private static final String MISSING_FILE = System.getProperty("htf.i18n.missing");

    static {
        if (MISSING_FILE != null) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { java.nio.file.Files.write(java.nio.file.Path.of(MISSING_FILE), missing, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (IOException e) { /* dev aid only */ }
        }));
    }

    private static void noteMissing(String s) {
        if (MISSING_FILE == null) return;
        boolean letters = false;
        for (int i = 0; i < s.length() && !letters; i++) letters = s.charAt(i) < 128 && Character.isLetter(s.charAt(i));
        if (letters && s.length() > 1) missing.add(s.replace("\n", "\\n"));
    }

    static { load(); }

    private I18n() {}

    private static void load() {
        try (InputStream in = I18n.class.getResourceAsStream("/lang/bn.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int cut = line.indexOf(" => ");
                if (cut < 0) continue;
                String key = unescape(line.substring(0, cut)), value = unescape(line.substring(cut + 4));
                String plain = key.replace("{#}", "{}");
                exact.put(plain, value);
                if (plain.contains("{}")) {
                    StringBuilder rx = new StringBuilder("^");
                    int literal = 0, from = 0;
                    for (int i; (i = key.indexOf("{", from)) >= 0; from = i + (key.startsWith("{#}", i) ? 3 : 2)) {
                        String part = key.substring(from, i);
                        literal += part.length();
                        rx.append(Pattern.quote(part)).append(key.startsWith("{#}", i) ? "([-+]?[0-9][0-9.,]*)" : "(.+?)");
                    }
                    String tail = key.substring(from);
                    literal += tail.length();
                    rx.append(Pattern.quote(tail)).append("$");
                    templates.add(new Template(Pattern.compile(rx.toString(), Pattern.DOTALL), value, literal));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read translations", e);
        }
        templates.sort((x, y) -> Integer.compare(y.literal(), x.literal()));     // the most specific template wins
    }

    private static String unescape(String s) { return s.strip().replace("\\n", "\n"); }

    public static void setLanguage(String lang) {
        language = BN.equals(lang) ? BN : EN;
        memo.clear();
    }

    /** Headings drawn in capitals: Latin text is upper-cased, Bengali (which has no case) is just translated. */
    public static String caps(String s) { return bengali() ? t(s) : s.toUpperCase(); }

    public static String language() { return language; }

    public static boolean bengali() { return BN.equals(language); }

    public static String languageName(String code) { return LANGUAGE_NAMES[BN.equals(code) ? 1 : 0]; }

    /** Translate a string (exact text, template, or each part of a "a   |   b" line). */
    public static String t(String s) {
        if (s == null || s.isEmpty() || !bengali()) return s;
        String hit = memo.get(s);
        if (hit != null) return hit;
        String out = translate(s);
        if (memo.size() > 4000) memo.clear();
        memo.put(s, out);
        return out;
    }

    private static String translate(String s) {
        String direct = lookup(s);
        if (direct != null) return finish(direct);
        Matcher m = SEPARATOR.matcher(s);
        if (m.find()) {
            StringBuilder b = new StringBuilder();
            int last = 0;
            m.reset();
            while (m.find()) {
                b.append(partOf(s.substring(last, m.start()))).append(m.group());
                last = m.end();
            }
            b.append(partOf(s.substring(last)));
            return finish(b.toString());
        }
        noteMissing(s);
        return finish(s);
    }

    private static String partOf(String part) {
        String hit = lookup(part);
        if (hit == null) noteMissing(part);
        return hit != null ? hit : part;
    }

    private static String lookup(String s) {
        String v = exact.get(s);
        if (v != null) return v;
        for (Template t : templates) {
            Matcher m = t.pattern().matcher(s);
            if (m.matches()) {
                String[] vals = new String[m.groupCount()];
                for (int i = 0; i < vals.length; i++) vals[i] = partOfValue(m.group(i + 1));
                return fill(t.target(), vals);
            }
        }
        return null;
    }

    /** Fill {} (in order) or {1}, {2}... (by position, for languages that reorder) with the values. */
    private static String fill(String target, String[] vals) {
        StringBuilder b = new StringBuilder();
        int next = 0, from = 0;
        for (int i; (i = target.indexOf('{', from)) >= 0; ) {
            int close = target.indexOf('}', i);
            if (close < 0) break;
            String tag = target.substring(i + 1, close);
            b.append(target, from, i);
            if (tag.isEmpty()) b.append(next < vals.length ? vals[next++] : "{}");
            else if (tag.chars().allMatch(Character::isDigit) && Integer.parseInt(tag) >= 1 && Integer.parseInt(tag) <= vals.length) b.append(vals[Integer.parseInt(tag) - 1]);
            else b.append(target, i, close + 1);
            from = close + 1;
        }
        return b.append(target.substring(from)).toString();
    }

    /** A value captured by a template may itself be a translatable phrase (a plan label, a variety name). */
    private static String partOfValue(String v) {
        String hit = exact.get(v);
        return hit != null ? hit : v;
    }

    private static String finish(String s) { return digits(months(s)); }

    private static String months(String s) {
        Matcher m = DATE.matcher(s);
        if (!m.find()) return s;
        StringBuilder b = new StringBuilder();
        m.reset();
        while (m.find()) {
            int i = java.util.Arrays.asList(MONTHS_EN).indexOf(m.group(2));
            m.appendReplacement(b, Matcher.quoteReplacement(m.group(1) + " " + MONTHS_BN[i]));
        }
        return m.appendTail(b).toString();
    }

    public static String digits(String s) {
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean attached = i > 0 && (Character.isLetter(s.charAt(i - 1)) && s.charAt(i - 1) < 128 || s.charAt(i - 1) == '_');   // F3, T2M_MAX stay as they are
            if (c >= '0' && c <= '9' && !attached) {
                if (b == null) { b = new StringBuilder(s.length()); b.append(s, 0, i); }
                b.append(BN_DIGITS[c - '0']);
            } else if (b != null) b.append(c);
        }
        return b == null ? s : b.toString();
    }

    /** Format with a translatable template: {@code f("Day {} of {}", 3, 120)}. */
    public static String f(String template, Object... args) {
        String t = bengali() ? exact.getOrDefault(template, template) : template;
        String[] vals = new String[args.length];
        for (int i = 0; i < vals.length; i++) vals[i] = bengali() ? t(String.valueOf(args[i])) : String.valueOf(args[i]);
        String out = fill(t, vals);
        return bengali() ? finish(out) : out;
    }
}
