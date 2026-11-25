package com.example.tunnelcart;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edits the config of the High-Speed Rail mod (config/highspeed-rail.json5) without touching that mod.
 * The mod owns a fixed list of blocks (BLOCKS: deepslate and its variants incl. cobbled deepslate, ores, tuff,
 * bedrock, gravel). Every block of the list gets the speed that was asked for: an entry that exists is changed,
 * a missing one is added. Everything else in the file (comments, other blocks, layout) stays as it is.
 * High-Speed Rail reads its file only at start, so reload() pushes the new values into the running mod by reflection.
 */
public final class HsrConfig {
    /** Blocks the tunnel cart / fast carts need at full speed. */
    public static final String[] BLOCKS = {
            // the tunnel floor and the whole deepslate family
            "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:polished_deepslate",
            "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks",
            "minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles", "minecraft:chiseled_deepslate",
            // ores in deepslate
            "minecraft:deepslate_coal_ore", "minecraft:deepslate_iron_ore", "minecraft:deepslate_copper_ore",
            "minecraft:deepslate_gold_ore", "minecraft:deepslate_redstone_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:deepslate_lapis_ore", "minecraft:deepslate_diamond_ore",
            // ores in stone
            "minecraft:coal_ore", "minecraft:iron_ore", "minecraft:copper_ore", "minecraft:gold_ore",
            "minecraft:redstone_ore", "minecraft:emerald_ore", "minecraft:lapis_ore", "minecraft:diamond_ore",
            // the rest
            "minecraft:tuff", "minecraft:bedrock", "minecraft:gravel"
    };
    /** The block the tunnel cart lays under its rails; used to tell what speed the file has now. */
    public static final String FLOOR = "minecraft:cobbled_deepslate";

    private static final Pattern OBJ = Pattern.compile("\\{[^{}]*\\}");
    private static final Pattern ID = Pattern.compile("blockId['\"]?\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern SPEED = Pattern.compile("(cartSpeed['\"]?\\s*:\\s*)(\\d+)");
    private static final Pattern BLOCKS_OPEN = Pattern.compile("blocks['\"]?\\s*:\\s*\\[");

    private HsrConfig() {}

    public static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("highspeed-rail.json5"); }

    /** High-Speed Rail is in the game (its classes are there), whether or not its file exists yet. */
    public static boolean installed() {
        try { Class.forName("net.pcal.highspeed.HighspeedService", false, HsrConfig.class.getClassLoader()); return true; }
        catch (Throwable t) { return false; }
    }

    public static boolean present() { return Files.isRegularFile(file()) || installed(); }

    // ------------------------------------------------------------------ pure text part

    /** Same text with comments (// and block comments) replaced by spaces, so that indices still match. */
    static String mask(String text) {
        char[] c = text.toCharArray();
        char quote = 0;
        for (int i = 0; i < c.length; i++) {
            char ch = c[i];
            if (quote != 0) {
                if (ch == '\\') i++;
                else if (ch == quote) quote = 0;
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '/' && i + 1 < c.length && c[i + 1] == '/') {
                while (i < c.length && c[i] != '\n') c[i++] = ' ';
                i--;
            } else if (ch == '/' && i + 1 < c.length && c[i + 1] == '*') {
                int j = i;
                while (j < c.length && !(c[j] == '*' && j + 1 < c.length && c[j + 1] == '/')) { if (c[j] != '\n') c[j] = ' '; j++; }
                if (j < c.length) { c[j] = ' '; if (j + 1 < c.length) c[j + 1] = ' '; }
                i = j + 1;
            }
        }
        return new String(c);
    }

    /** cartSpeed of one block in the text, -1 if it is not listed. */
    static int speedOf(String text, String blockId) {
        String masked = mask(text);
        Matcher m = OBJ.matcher(masked);
        while (m.find()) {
            String obj = m.group();
            Matcher id = ID.matcher(obj);
            if (!id.find() || !id.group(1).equals(blockId)) continue;
            Matcher sp = SPEED.matcher(obj);
            if (sp.find()) return Integer.parseInt(sp.group(2));
        }
        return -1;
    }

    private static String entry(String block, int speed) {
        return "    { 'blockId': '" + block + "', 'cartSpeed': " + speed + " }";
    }

    private static String template(int speed) {
        StringBuilder sb = new StringBuilder("{\n  'blocks' : [\n");
        for (int i = 0; i < BLOCKS.length; i++) sb.append(entry(BLOCKS[i], speed)).append(i < BLOCKS.length - 1 ? ",\n" : "\n");
        sb.append("  ],\n  'isSpeedometerEnabled'     : true,\n  'isTrueSpeedometerEnabled' : false,\n  'isIceBoatsEnabled'        : false\n}\n");
        return sb.toString();
    }

    /** Every block of BLOCKS gets `speed`: existing entries are changed, missing ones are added. */
    static String ensure(String text, int speed) {
        if (text == null || text.isBlank() || !mask(text).contains("{")) return template(speed);
        Set<String> want = new HashSet<>(List.of(BLOCKS));
        Set<String> have = new HashSet<>();

        // 1) change the speed of the entries that exist
        String masked = mask(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        Matcher m = OBJ.matcher(masked);
        while (m.find()) {
            String obj = m.group();
            Matcher id = ID.matcher(obj);
            if (!id.find() || !want.contains(id.group(1))) continue;
            Matcher sp = SPEED.matcher(obj);
            if (!sp.find()) continue;
            have.add(id.group(1));
            out.append(text, last, m.start() + sp.start(2)).append(speed);
            last = m.start() + sp.end(2);
        }
        out.append(text, last, text.length());
        String patched = out.toString();

        // 2) add the ones that are missing, at the start of the blocks list
        List<String> missing = new ArrayList<>();
        for (String b : BLOCKS) if (!have.contains(b)) missing.add(b);
        if (missing.isEmpty()) return patched;

        masked = mask(patched);
        Matcher open = BLOCKS_OPEN.matcher(masked);
        StringBuilder add = new StringBuilder();
        if (open.find()) {
            int at = open.end();
            int close = masked.indexOf(']', at);
            boolean empty = close < 0 || masked.substring(at, close).indexOf('{') < 0;
            for (int i = 0; i < missing.size(); i++) {
                add.append('\n').append(entry(missing.get(i), speed));
                if (i < missing.size() - 1 || !empty) add.append(',');
            }
            return patched.substring(0, at) + add + patched.substring(at);
        }
        // no 'blocks' list at all: put one right after the first brace
        int brace = masked.indexOf('{');
        add.append("\n  'blocks' : [");
        for (int i = 0; i < missing.size(); i++) add.append('\n').append(entry(missing.get(i), speed)).append(i < missing.size() - 1 ? "," : "");
        add.append("\n  ],");
        return patched.substring(0, brace + 1) + add + patched.substring(brace + 1);
    }

    // ------------------------------------------------------------------ file / running game

    /** Current speed of the tunnel floor block in the file, -1 if the file has none. */
    public static int fastSpeed() {
        try { return speedOf(Files.readString(file(), StandardCharsets.UTF_8), FLOOR); } catch (Exception e) { return -1; }
    }

    /** Writes `bps` for every block of BLOCKS (creating the file if High-Speed Rail has not made it yet). @return true when the file was changed */
    public static boolean setSpeed(int bps) throws java.io.IOException {
        Path f = file();
        String text = Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
        String patched = ensure(text, Math.max(1, bps));
        if (patched.equals(text)) return false;
        Files.writeString(f, patched, StandardCharsets.UTF_8);
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

    /** At world start: make the file match the settings of this mod (the file may have been deleted or recreated by High-Speed Rail). */
    public static void syncAtStart() {
        if (!present()) return;
        try {
            if (setSpeed(TunnelCartConfig.hsrSpeed())) reload();
        } catch (Exception ignored) { /* not fatal */ }
    }
}
