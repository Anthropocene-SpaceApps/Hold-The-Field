package org.anthropocene.htf.gfx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads GLSL from /shaders, expanding {@code #include "file.glsl"} lines. */
public final class ShaderLoader {
    private ShaderLoader() {}

    private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);

    public static Shader load(String name) {
        return new Shader(source(name + ".vert"), source(name + ".frag"));
    }

    /** Different vertex and fragment base names (e.g. the shared fullscreen vertex shader). */
    public static Shader load2(String vert, String frag) {
        return new Shader(source(vert + ".vert"), source(frag + ".frag"));
    }

    public static String source(String file) {
        String text = read(file);
        Matcher m = INCLUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(source(m.group(1))));
        m.appendTail(out);
        return out.toString();
    }

    private static String read(String file) {
        try (InputStream in = ShaderLoader.class.getResourceAsStream("/shaders/" + file)) {
            if (in == null) throw new IllegalStateException("Missing shader resource: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read shader " + file, e);
        }
    }
}
