package com.fire.pack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ResourcePackLoader {

    public static class Pack {
        public final File root;
        public final String name;
        public final int packFormat;

        public Pack(File root, String name, int packFormat) {
            this.root = root;
            this.name = name;
            this.packFormat = packFormat;
        }
    }

    public static List<Pack> loadAll() {
        List<Pack> packs = new ArrayList<>();
        File dir = new File("resourcepacks");
        if (!dir.exists()) {
            if (dir.mkdirs()) System.out.println("[EdiPack] 已创建 resourcepacks/ 文件夹");
            return packs;
        }

        File[] items = dir.listFiles(File::isDirectory);
        if (items == null) return packs;

        for (File item : items) {
            File metaFile = new File(item, "package.edimeta");
            if (!metaFile.exists()) continue;

            try {
                String json = new String(Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8);
                String desc = parseField(json, "description");
                String fmt  = parseField(json, "pack_format");
                int format = 1;
                try { if (fmt != null) format = Integer.parseInt(fmt.trim()); } catch (Exception ignored) {}

                Pack p = new Pack(item, desc != null ? desc : item.getName(), format);
                packs.add(p);
                System.out.println("[EdiPack] 资源包: " + item.getName() + " - " + p.name);
            } catch (Exception e) {
                System.err.println("[EdiPack] 资源包读取失败: " + item.getName() + " → " + e.getMessage());
            }
        }
        return packs;
    }

    /**
     * 按加载顺序反向查找纹理文件。返回 null 表示全部未命中。
     * relPath 形如 "assets/minecraft/textures/block/grass.png"
     */
    public static byte[] findTexture(List<Pack> packs, String relPath) {
        for (int i = packs.size() - 1; i >= 0; i--) {
            File f = new File(packs.get(i).root, relPath);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readAllBytes(f.toPath());
                } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static String parseField(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;
        if (json.charAt(i) == '"') {
            int q2 = json.indexOf('"', i + 1);
            if (q2 < 0) return null;
            return json.substring(i + 1, q2);
        } else {
            int end = i;
            while (end < json.length() && ",} \n\r\t".indexOf(json.charAt(end)) < 0) end++;
            return json.substring(i, end);
        }
    }
}