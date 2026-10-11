package com.fire.world;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class SaveManager {

    public static class WorldInfo {
        public String name;
        public String type = "flat";
        public String gameMode = "creative";
        public long seed;
        public float playerX = 64.5f, playerY = 64.0f, playerZ = 64.5f;
        public float yaw = 0, pitch = 0;
        public File dir;
    }

    public static File getSavesDir() {
        File dir = new File("saves");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<WorldInfo> listWorlds() {
        List<WorldInfo> list = new ArrayList<>();
        File dir = getSavesDir();
        File[] items = dir.listFiles(File::isDirectory);
        if (items == null) return list;

        for (File item : items) {
            File level = new File(item, "level.json");
            if (!level.exists()) continue;
            try {
                String json = new String(Files.readAllBytes(level.toPath()), StandardCharsets.UTF_8);
                WorldInfo info = new WorldInfo();
                info.dir = item;
                String n = parseString(json, "name");
                info.name = n != null ? n : item.getName();
                String t = parseString(json, "type");
                info.type = t != null ? t : "flat";
                String gm = parseString(json, "gameMode");
                info.gameMode = gm != null ? gm : "creative";
                info.seed = parseLong(json, "seed");
                info.playerX = parseFloat(json, "x");
                info.playerY = parseFloat(json, "y");
                info.playerZ = parseFloat(json, "z");
                info.yaw = parseFloat(json, "yaw");
                info.pitch = parseFloat(json, "pitch");
                list.add(info);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public static WorldInfo createWorld(String name, String type, String gameMode) throws IOException {
        File dir = new File(getSavesDir(), sanitize(name));
        int i = 1;
        while (dir.exists()) {
            dir = new File(getSavesDir(), sanitize(name) + "_" + i);
            i++;
        }
        dir.mkdirs();

        WorldInfo info = new WorldInfo();
        info.name = name;
        info.type = type;
        info.gameMode = gameMode != null ? gameMode : "creative";
        info.seed = System.nanoTime() ^ (long) (Math.random() * Long.MAX_VALUE);
        info.dir = dir;
        info.playerX = 64.5f;
        info.playerZ = 64.5f;

        save(info, null);
        return info;
    }

    public static void save(WorldInfo info, byte[][][] world) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escape(info.name)).append("\",\n");
        sb.append("  \"type\": \"").append(escape(info.type)).append("\",\n");
        sb.append("  \"gameMode\": \"").append(escape(info.gameMode)).append("\",\n");
        sb.append("  \"seed\": ").append(info.seed).append(",\n");
        sb.append("  \"player\": {\n");
        sb.append("    \"x\": ").append(info.playerX).append(",\n");
        sb.append("    \"y\": ").append(info.playerY).append(",\n");
        sb.append("    \"z\": ").append(info.playerZ).append(",\n");
        sb.append("    \"yaw\": ").append(info.yaw).append(",\n");
        sb.append("    \"pitch\": ").append(info.pitch).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
        Files.write(new File(info.dir, "level.json").toPath(),
                sb.toString().getBytes(StandardCharsets.UTF_8));

        if (world != null) {
            File dataFile = new File(info.dir, "world.dat");
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new FileOutputStream(dataFile)))) {
                out.writeInt(WorldGen.SIZE_X);
                out.writeInt(WorldGen.SIZE_Y);
                out.writeInt(WorldGen.SIZE_Z);
                for (int x = 0; x < WorldGen.SIZE_X; x++)
                    for (int y = 0; y < WorldGen.SIZE_Y; y++)
                        for (int z = 0; z < WorldGen.SIZE_Z; z++)
                            out.writeByte(world[x][y][z]);
            }
        }
    }

    public static byte[][][] load(WorldInfo info) throws IOException {
        File dataFile = new File(info.dir, "world.dat");
        if (!dataFile.exists()) return null;
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new FileInputStream(dataFile)))) {
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            byte[][][] world = new byte[sx][sy][sz];
            for (int x = 0; x < sx; x++)
                for (int y = 0; y < sy; y++)
                    for (int z = 0; z < sz; z++)
                        world[x][y][z] = in.readByte();
            return world;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "_");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String parseString(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static long parseLong(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return 0;
        int colon = json.indexOf(':', k);
        if (colon < 0) return 0;
        int i = colon + 1;
        while (i < json.length() && !Character.isDigit(json.charAt(i)) && json.charAt(i) != '-') i++;
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
        try { return Long.parseLong(json.substring(i, j)); } catch (Exception e) { return 0; }
    }

    private static float parseFloat(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return 0;
        int colon = json.indexOf(':', k);
        if (colon < 0) return 0;
        int i = colon + 1;
        while (i < json.length() && !Character.isDigit(json.charAt(i)) && json.charAt(i) != '-' && json.charAt(i) != '.') i++;
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-' || json.charAt(j) == '.')) j++;
        try { return Float.parseFloat(json.substring(i, j)); } catch (Exception e) { return 0; }
    }
}