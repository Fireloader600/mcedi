package com.fire.world;

import com.fire.block.Block;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WorldGen {
    public static final int SIZE_X = 128;
    public static final int SIZE_Y = 64;
    public static final int SIZE_Z = 128;

    public static final int STONE_TOP_Y = 61;
    public static final int GRASS_Y = 62;

    private static final Map<String, Structure> STRUCTURE_CACHE = new HashMap<>();

    public static class Structure {
        public int sizeX, sizeY, sizeZ;
        public byte[] blocks;
    }

    public static byte[][][] generate(long seed, String type) {
        if ("infinite".equals(type)) return generateInfinite(seed);
        return generateFlat();
    }

    public static byte[][][] generateFlat() {
        byte[][][] world = new byte[SIZE_X][SIZE_Y][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = 0; y <= STONE_TOP_Y; y++) world[x][y][z] = Block.STONE;
                world[x][GRASS_Y][z] = Block.GRASS;
            }
        }
        return world;
    }

    public static byte[][][] generateInfinite(long seed) {
        byte[][][] world = new byte[SIZE_X][SIZE_Y][SIZE_Z];

        int[] heightMap = new int[SIZE_X * SIZE_Z];
        String[] biomeMap = new String[SIZE_X * SIZE_Z];

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                int h = (int) baseHeight(x, z, seed);
                if (h < 5) h = 5;
                if (h >= SIZE_Y - 12) h = SIZE_Y - 12;
                heightMap[x * SIZE_Z + z] = h;
                biomeMap[x * SIZE_Z + z] = pickBiome(x, z, seed);
            }
        }

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                int h = heightMap[x * SIZE_Z + z];
                boolean desert = "desert".equals(biomeMap[x * SIZE_Z + z]);
                for (int y = 0; y <= h; y++) {
                    if (y == h) {
                        world[x][y][z] = desert ? Block.SAND : Block.GRASS;
                    } else if (y >= h - 3) {
                        world[x][y][z] = desert ? Block.SAND : Block.DIRT;
                    } else {
                        world[x][y][z] = Block.STONE;
                    }
                }
            }
        }

        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                String bName = biomeMap[x * SIZE_Z + z];
                WorldTag.Biome biome = WorldTag.get(bName);
                if (biome == null) continue;

                for (String plant : biome.plants) {
                    Structure s = getStructure(plant);
                    if (s == null) continue;
                    double p = biome.getProbability(plant);
                    if (p <= 0) continue;
                    float r = hash(x, z, seed ^ 0xDEADBEEFL);
                    if (r < p) {
                        int baseY = heightMap[x * SIZE_Z + z] + 1;
                        placeStructure(world, s,
                                x - s.sizeX / 2,
                                baseY,
                                z - s.sizeZ / 2);
                    }
                }
            }
        }

        return world;
    }

    private static void placeStructure(byte[][][] world, Structure s, int baseX, int baseY, int baseZ) {
        for (int y = 0; y < s.sizeY; y++) {
            for (int z = 0; z < s.sizeZ; z++) {
                for (int x = 0; x < s.sizeX; x++) {
                    byte b = s.blocks[(y * s.sizeZ + z) * s.sizeX + x];
                    if (b == Block.AIR) continue;
                    int wx = baseX + x;
                    int wy = baseY + y;
                    int wz = baseZ + z;
                    if (wx < 0 || wx >= SIZE_X || wy < 0 || wy >= SIZE_Y || wz < 0 || wz >= SIZE_Z) continue;
                    if (world[wx][wy][wz] == Block.AIR) {
                        world[wx][wy][wz] = b;
                    }
                }
            }
        }
    }

    // ---------------- 结构加载 ----------------

    private static Structure getStructure(String name) {
        Structure cached = STRUCTURE_CACHE.get(name);
        if (cached != null) return cached;
        Structure s = loadStructure(name);
        if (s != null) STRUCTURE_CACHE.put(name, s);
        return s;
    }

    private static Structure loadStructure(String name) {
        String path = "/data/minecraft/structures/" + name + ".json";
        try (InputStream in = WorldGen.class.getResourceAsStream(path)) {
            if (in == null) return null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parseStructure(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static Structure parseStructure(String json) {
        Structure s = new Structure();

        // size
        int sizeIdx = json.indexOf("\"size\"");
        if (sizeIdx < 0) return null;
        int openBracket = json.indexOf('[', sizeIdx);
        if (openBracket < 0) return null;
        int closeBracket = json.indexOf(']', openBracket);
        if (closeBracket < 0) return null;
        String sizeStr = json.substring(openBracket + 1, closeBracket);
        String[] sizeParts = sizeStr.split(",");
        if (sizeParts.length < 3) return null;
        try {
            s.sizeX = Integer.parseInt(sizeParts[0].trim());
            s.sizeY = Integer.parseInt(sizeParts[1].trim());
            s.sizeZ = Integer.parseInt(sizeParts[2].trim());
        } catch (Exception e) {
            return null;
        }
        if (s.sizeX <= 0 || s.sizeY <= 0 || s.sizeZ <= 0) return null;

        // palette
        Map<Character, Byte> palette = new HashMap<>();
        int paletteIdx = json.indexOf("\"palette\"");
        if (paletteIdx < 0) return null;
        int paletteOpen = json.indexOf('{', paletteIdx);
        if (paletteOpen < 0) return null;
        int paletteClose = json.indexOf('}', paletteOpen);
        if (paletteClose < 0) return null;
        String paletteStr = json.substring(paletteOpen + 1, paletteClose);
        int p = 0;
        while (p < paletteStr.length()) {
            int k1 = paletteStr.indexOf('"', p);
            if (k1 < 0) break;
            int k2 = paletteStr.indexOf('"', k1 + 1);
            if (k2 < 0) break;
            String key = paletteStr.substring(k1 + 1, k2);
            int v1 = paletteStr.indexOf('"', k2 + 1);
            if (v1 < 0) break;
            int v2 = paletteStr.indexOf('"', v1 + 1);
            if (v2 < 0) break;
            String value = paletteStr.substring(v1 + 1, v2);
            byte blockId = nameToBlockId(value);
            if (!key.isEmpty()) palette.put(key.charAt(0), blockId);
            p = v2 + 1;
        }

        // layers
        int layersIdx = json.indexOf("\"layers\"");
        if (layersIdx < 0) return null;
        int layersOpen = json.indexOf('[', layersIdx);
        if (layersOpen < 0) return null;

        s.blocks = new byte[s.sizeX * s.sizeY * s.sizeZ];
        int pos = layersOpen + 1;
        for (int y = 0; y < s.sizeY; y++) {
            int layerOpen = json.indexOf('[', pos);
            if (layerOpen < 0) break;
            int layerClose = json.indexOf(']', layerOpen);
            if (layerClose < 0) break;
            String layerStr = json.substring(layerOpen + 1, layerClose);
            int lp = 0;
            for (int z = 0; z < s.sizeZ; z++) {
                int strOpen = layerStr.indexOf('"', lp);
                if (strOpen < 0) break;
                int strClose = layerStr.indexOf('"', strOpen + 1);
                if (strClose < 0) break;
                String row = layerStr.substring(strOpen + 1, strClose);
                for (int x = 0; x < s.sizeX && x < row.length(); x++) {
                    Byte b = palette.get(row.charAt(x));
                    if (b != null) {
                        s.blocks[(y * s.sizeZ + z) * s.sizeX + x] = b;
                    }
                }
                lp = strClose + 1;
            }
            pos = layerClose + 1;
        }
        return s;
    }

    private static byte nameToBlockId(String name) {
        switch (name) {
            case "grass":       return Block.GRASS;
            case "stone":       return Block.STONE;
            case "dirt":        return Block.DIRT;
            case "planks":      return Block.PLANKS;
            case "log":         return Block.LOG;
            case "leaves":      return Block.LEAVES;
            case "cobblestone": return Block.COBBLESTONE;
            case "coal_ore":    return Block.COAL_ORE;
            case "iron_ore":    return Block.IRON_ORE;
            case "gold_ore":    return Block.GOLD_ORE;
            case "diamond_ore": return Block.DIAMOND_ORE;
            case "sand":        return Block.SAND;
            default:            return Block.AIR;
        }
    }

    public static int getSurfaceY(byte[][][] world, int x, int z) {
        for (int y = SIZE_Y - 1; y >= 0; y--) {
            if (world[x][y][z] != Block.AIR) return y + 1;
        }
        return 0;
    }

    // ---------------- 地形噪声 ----------------

    private static float baseHeight(float x, float z, long seed) {
        float n1 = valueNoise(x * 0.04f, z * 0.04f, seed);
        float n2 = valueNoise(x * 0.12f, z * 0.12f, seed + 7);
        float n3 = valueNoise(x * 0.3f, z * 0.3f, seed + 13);
        return 28f + n1 * 10f + n2 * 4f + n3 * 2f;
    }

    private static String pickBiome(float x, float z, long seed) {
        java.util.List<String> names = WorldTag.names();
        if (names.isEmpty()) return null;
        float b = valueNoise(x * 0.015f, z * 0.015f, seed + 777);
        int idx = (int) (b * names.size());
        if (idx >= names.size()) idx = names.size() - 1;
        if (idx < 0) idx = 0;
        return names.get(idx);
    }

    private static float valueNoise(float x, float z, long seed) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        float xf = x - xi;
        float zf = z - zi;
        float u = xf * xf * (3 - 2 * xf);
        float v = zf * zf * (3 - 2 * zf);
        float a = hash(xi, zi, seed);
        float b = hash(xi + 1, zi, seed);
        float c = hash(xi, zi + 1, seed);
        float d = hash(xi + 1, zi + 1, seed);
        float ab = a + (b - a) * u;
        float cd = c + (d - c) * u;
        return ab + (cd - ab) * v;
    }

    private static float hash(int x, int z, long seed) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L;
        h = h * 0x9E3779B97F4A7C15L + z * 0x94D049BB133111EBL;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return ((h >>> 8) & 0xFFFFFF) / (float) 0x1000000;
    }
}