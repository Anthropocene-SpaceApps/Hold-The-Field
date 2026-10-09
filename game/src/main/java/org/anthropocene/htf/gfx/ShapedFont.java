package org.anthropocene.htf.gfx;

import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.AttributedString;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL33.GL_TEXTURE_SWIZZLE_RGBA;

/**
 * Text for scripts that need shaping (Bengali conjuncts, vowel signs). The JDK's text layout does the
 * shaping; each distinct string is rendered once into a small coverage texture and then drawn as one quad.
 * Latin runs inside a Bengali line use DejaVu Sans, Bengali runs use Noto Sans Bengali.
 */
final class ShapedFont {
    static final float BAKE = 32f;
    private static final int PAD = 3, MAX_ENTRIES = 600;

    record Entry(int tex, float advance, float ascent, int w, int h) {}

    private final java.awt.Font[] latin = new java.awt.Font[2], indic = new java.awt.Font[2];
    private final FontRenderContext frc = new FontRenderContext(null, true, true);
    private final Map<String, Entry> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Entry> e) {
            if (size() <= MAX_ENTRIES) return false;
            glDeleteTextures(e.getValue().tex());
            return true;
        }
    };

    ShapedFont() {
        System.setProperty("java.awt.headless", "true");
        latin[0] = load("/fonts/DejaVuSans.ttf");
        latin[1] = load("/fonts/DejaVuSans-Bold.ttf");
        indic[0] = load("/fonts/NotoSansBengali-Regular.ttf");
        indic[1] = load("/fonts/NotoSansBengali-Bold.ttf");
    }

    private static java.awt.Font load(String res) {
        try (InputStream in = ShapedFont.class.getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException("Missing font " + res);
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in).deriveFont(BAKE);
        } catch (IOException | java.awt.FontFormatException e) {
            throw new IllegalStateException("Could not load font " + res, e);
        }
    }

    static boolean needsShaping(String s) {
        for (int i = 0; i < s.length(); i++) if (isIndic(s.charAt(i))) return true;
        return false;
    }

    private static boolean isIndic(char c) { return (c >= 0x0980 && c <= 0x09FF) || c == 0x0964 || c == 0x0965 || c == 0x200C || c == 0x200D; }

    private AttributedString styled(String s, boolean bold) {
        AttributedString as = new AttributedString(s);
        int face = bold ? 1 : 0, i = 0;
        while (i < s.length()) {
            boolean in = isIndic(s.charAt(i));
            int j = i + 1;
            while (j < s.length() && (isIndic(s.charAt(j)) == in || (!in && s.charAt(j) == ' ' && j + 1 < s.length() && !isIndic(s.charAt(j + 1))))) j++;
            as.addAttribute(TextAttribute.FONT, (in ? indic : latin)[face], i, j);
            i = j;
        }
        return as;
    }

    private Entry entry(String s, boolean bold) {
        String key = (bold ? "b" : "r") + s;
        Entry e = cache.get(key);
        if (e != null) return e;
        AttributedString as = styled(s.isEmpty() ? " " : s, bold);
        TextLayout tl = new TextLayout(as.getIterator(), frc);
        float adv = tl.getAdvance(), asc = tl.getAscent(), desc = tl.getDescent() + tl.getLeading();
        int w = (int) Math.ceil(adv) + PAD * 2 + 2, h = (int) Math.ceil(asc + desc) + PAD * 2;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        tl.draw(g, PAD, PAD + asc);
        g.dispose();
        byte[] px = ((DataBufferByte) im.getRaster().getDataBuffer()).getData();
        ByteBuffer buf = BufferUtils.createByteBuffer(px.length);
        buf.put(px).flip();
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, w, h, 0, GL_RED, GL_UNSIGNED_BYTE, buf);
        glTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_SWIZZLE_RGBA, new int[]{GL_ONE, GL_ONE, GL_ONE, GL_RED});
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        e = new Entry(tex, adv, asc, w, h);
        cache.put(key, e);
        return e;
    }

    float width(String s, float size, boolean bold) { return entry(s, bold).advance() * size / BAKE; }

    /** Draw one string; (x, y) is the top-left of the line box, like {@link Font#emit}. */
    void draw(Renderer2D r, String s, float x, float y, float size, int argb, boolean bold) {
        Entry e = entry(s, bold);
        float k = size / BAKE, baseY = y + size * 0.82f;
        r.bindTexture(e.tex());
        r.glyph(x - PAD * k, baseY - (e.ascent() + PAD) * k, x + (e.w() - PAD) * k, baseY + (e.h() - e.ascent() - PAD) * k, 0, 0, 1, 1, argb);
    }
}
