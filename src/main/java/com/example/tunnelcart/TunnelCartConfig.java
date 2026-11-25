package com.example.tunnelcart;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Mod settings (config/tunnelcart.json). Speeds are in blocks per second, as the High-Speed Rail speedometer shows them. */
public final class TunnelCartConfig {
    public static final double MIN_SPEED = 0.1, MAX_SPEED = 300.0, RECOMMENDED_MAX = 75.0;

    private static volatile double buildSpeed = 75.0;     // tunnel cart on straight stretches
    private static volatile double vanillaSpeed = 300.0;  // ordinary minecarts; 300 = no limit
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TunnelCartConfig() {}

    public static double buildSpeed() { return buildSpeed; }
    public static double vanillaSpeed() { return vanillaSpeed; }
    public static boolean vanillaLimited() { return vanillaSpeed < MAX_SPEED - 1e-9; }
    /** What goes into the High-Speed Rail file: the block must allow the faster of the two kinds of cart; each kind is capped separately by this mod. */
    public static int hsrSpeed() { return Math.max(1, (int) Math.round(Math.max(buildSpeed, vanillaSpeed))); }

    public static void set(double build, double vanilla) {
        buildSpeed = clamp(build);
        vanillaSpeed = clamp(vanilla);
    }

    private static double clamp(double v) { return Math.max(MIN_SPEED, Math.min(MAX_SPEED, v)); }

    /**
     * Length of every slowing zone (turn, climb, descent, bypass) for a given speed.
     * The cart has to spend at least one tick on the slow blocks, so the zone grows with the distance covered
     * per tick. Fitted to the measured points 10 b/s -> 1 block and 75 b/s -> 15 blocks (straight line), which gives
     * 64 blocks at 300 b/s.
     */
    public static int brakeFor(double bps) {
        double perTick = bps / 20.0;
        double slope = (15.0 - 1.0) / (75.0 / 20.0 - 10.0 / 20.0);
        double l = slope * perTick - (slope * (10.0 / 20.0) - 1.0);
        return Math.max(1, (int) Math.ceil(l - 1e-9));
    }

    public static int brake() { return brakeFor(buildSpeed); }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("tunnelcart.json"); }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) { save(); return; }
            JsonObject o = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
            if (o == null) return;
            double b = o.has("tunnelCartSpeed") ? o.get("tunnelCartSpeed").getAsDouble() : buildSpeed;
            double v = o.has("vanillaMinecartSpeed") ? o.get("vanillaMinecartSpeed").getAsDouble() : vanillaSpeed;
            set(b, v);
        } catch (Exception ignored) { /* keep defaults */ }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("tunnelCartSpeed", buildSpeed);
            o.addProperty("vanillaMinecartSpeed", vanillaSpeed);
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception ignored) { /* not fatal */ }
    }
}
