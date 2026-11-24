package com.example.tunnelcart;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edits the config of the High-Speed Rail mod (config/highspeed-rail.json5) without touching that mod:
 * the speed of the fast blocks (the ones with the highest cartSpeed, e.g. deepslate at 255) is replaced by a new value,
 * everything else in the file (comments, other blocks, layout) stays as it is.
 * High-Speed Rail reads its file only at start, so reload() pushes the new values into the running mod by reflection.
 */
public final class HsrConfig {
    private static final Pattern SPEED = Pattern.compile("(cartSpeed['\"]?\\s*:\\s*)(\\d+)");

    private HsrConfig() {}

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("highspeed-rail.json5"); }
    public static boolean present() { return Files.isRegularFile(file()); }

    /** Highest cartSpeed in the text (commented-out lines are ignored), -1 if there is none. */
    static int maxSpeed(String text) {
        int max = -1;
        for (String line : text.split("\n", -1)) {
            if (line.strip().startsWith("//")) continue;
            Matcher m = SPEED.matcher(line);
            while (m.find()) max = Math.max(max, Integer.parseInt(m.group(2)));
        }
        return max;
    }

    /** Replaces every cartSpeed that equals the current maximum by `speed`. */
    static String withFastSpeed(String text, int speed) {
        int max = maxSpeed(text);
        if (max < 0) return text;
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.strip().startsWith("//")) {
                Matcher m = SPEED.matcher(line);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    int v = Integer.parseInt(m.group(2));
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + (v == max ? speed : v)));
                }
                m.appendTail(sb);
                line = sb.toString();
            }
            out.append(line);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }

    /** Current speed of the fast blocks, -1 if unknown. */
    public static int fastSpeed() {
        try { return maxSpeed(Files.readString(file(), StandardCharsets.UTF_8)); } catch (Exception e) { return -1; }
    }

    /** @return true when the file was changed */
    public static boolean setFastSpeed(int bps) throws java.io.IOException {
        String text = Files.readString(file(), StandardCharsets.UTF_8);
        String patched = withFastSpeed(text, Math.max(1, bps));
        if (patched.equals(text)) return false;
        Files.writeString(file(), patched, StandardCharsets.UTF_8);
        return true;
    }

    /** Makes the running High-Speed Rail use the file again. false = did not work, the game has to be restarted. */
    public static boolean reload() {
        try {
            Class<?> service = Class.forName("net.pcal.highspeed.HighspeedService");
            Object instance = service.getMethod("getInstance").invoke(null);
            Class<?> parser = Class.forName("net.pcal.highspeed.HighspeedConfigParser");
            Method parse = parser.getDeclaredMethod("parse", InputStream.class);
            parse.setAccessible(true);
            Object config;
            try (InputStream in = Files.newInputStream(file())) { config = parse.invoke(null, in); }
            Field field = service.getDeclaredField("config");
            field.setAccessible(true);
            field.set(instance, config);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
