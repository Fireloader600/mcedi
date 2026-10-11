package com.fire.world;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorldTag {

    public static class Biome {
        public String name;
        public final List<String> plants = new ArrayList<>();
        public final Map<String, Integer> density = new HashMap<>();

        public double getProbability(String plant) {
            Integer d = density.get(plant);
            if (d == null) return 0;
            return d / 5000.0;
        }
    }

    private static final Map<String, Biome> BIOMES = new LinkedHashMap<>();
    private static boolean initialized = false;

    public static synchronized void initBuiltin() {
        if (initialized) return;
        initialized = true;
        load("/data/minecraft/biome/plains.json", "plains");
        load("/data/minecraft/biome/forest.json", "forest");
        load("/data/minecraft/biome/desert.json", "desert");
    }

    public static synchronized void register(String name, List<String> plants, Map<String, Integer> density) {
        Biome b = new Biome();
        b.name = name;
        if (plants != null) b.plants.addAll(plants);
        if (density != null) b.density.putAll(density);
        BIOMES.put(name, b);
    }

    public static Biome get(String name) {
        return BIOMES.get(name);
    }

    public static List<String> names() {
        return new ArrayList<>(BIOMES.keySet());
    }

    public static List<Biome> all() {
        return new ArrayList<>(BIOMES.values());
    }

    private static void load(String path, String name) {
        try (InputStream in = WorldTag.class.getResourceAsStream(path)) {
            if (in == null) return;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            register(name, parseStringArray(json, "plant"), parseDensity(json));
        } catch (IOException ignored) {
        }
    }

    private static List<String> parseStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return result;
        int colon = json.indexOf(':', k);
        if (colon < 0) return result;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return result;
        if (json.startsWith("null", i)) return result;
        if (json.charAt(i) != '[') return result;
        int open = i;
        int close = json.indexOf(']', open);
        if (close < 0) return result;
        String content = json.substring(open + 1, close);
        int p = 0;
        while (p < content.length()) {
            int q1 = content.indexOf('"', p);
            if (q1 < 0) break;
            int q2 = content.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            result.add(content.substring(q1 + 1, q2));
            p = q2 + 1;
        }
        return result;
    }

    private static Map<String, Integer> parseDensity(String json) {
        Map<String, Integer> result = new HashMap<>();
        int k = json.indexOf("\"density\"");
        if (k < 0) return result;
        int colon = json.indexOf(':', k);
        if (colon < 0) return result;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return result;
        if (json.startsWith("null", i)) return result;
        if (json.charAt(i) != '{') return result;
        int open = i;
        int close = json.indexOf('}', open);
        if (close < 0) return result;
        String content = json.substring(open + 1, close);
        int p = 0;
        while (p < content.length()) {
            int q1 = content.indexOf('"', p);
            if (q1 < 0) break;
            int q2 = content.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String key = content.substring(q1 + 1, q2);
            int colon2 = content.indexOf(':', q2);
            if (colon2 < 0) break;
            int j = colon2 + 1;
            while (j < content.length() && !Character.isDigit(content.charAt(j)) && content.charAt(j) != '-') j++;
            int j2 = j;
            while (j2 < content.length() && (Character.isDigit(content.charAt(j2)) || content.charAt(j2) == '-')) j2++;
            try { result.put(key, Integer.parseInt(content.substring(j, j2))); } catch (Exception ignored) {}
            p = j2;
        }
        return result;
    }
}