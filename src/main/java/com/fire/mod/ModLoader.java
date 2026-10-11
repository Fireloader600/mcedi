package com.fire.mod;

import com.fire.block.BlockRegistry;
import com.fire.world.WorldTag;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ModLoader {

    public static void loadAll(BlockRegistry registry, WorldTag tag) {
        File modsDir = new File("mods");
        if (!modsDir.exists()) {
            modsDir.mkdirs();
            return;
        }

        File[] jars = modsDir.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            //System.out.println("[EdiPack] mods/ 为空，跳过加载");
            return;
        }

        for (File jar : jars) {
            try {
                loadJar(jar, registry, tag);
            } catch (Throwable t) {
                //System.err.println("[EdiPack] 加载失败: " + jar.getName() + " → " + t);
            }
        }
    }

    private static void loadJar(File jar, BlockRegistry registry, WorldTag tag) throws Exception {
        try (JarFile jf = new JarFile(jar)) {
            JarEntry meta = jf.getJarEntry("edimod.json");
            if (meta == null) {
                //System.out.println("[EdiPack] 跳过（无 edimod.json）: " + jar.getName());
                return;
            }
            String json;
            try (InputStream in = jf.getInputStream(meta)) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            String name      = parseField(json, "name");
            String version   = parseField(json, "version");
            String mainClass = parseField(json, "main");

            if (mainClass == null || mainClass.isEmpty()) {
                throw new RuntimeException("edimod.json 缺少 main 字段");
            }

            URLClassLoader loader = new URLClassLoader(
                    new URL[]{ jar.toURI().toURL() },
                    ModLoader.class.getClassLoader()
            );

            Class<?> clazz = loader.loadClass(mainClass);
            Object instance = clazz.getDeclaredConstructor().newInstance();

            if (!(instance instanceof EdiMod)) {
                throw new RuntimeException(mainClass + " 未实现 EdiMod 接口");
            }

            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try {
                EdiMod mod = (EdiMod) instance;
                mod.onInit(registry);
                mod.onWorldTag(tag);
            } finally {
                Thread.currentThread().setContextClassLoader(old);
            }

            //System.out.println("[EdiPack] 已加载: " + name + " v" + version + " (" + jar.getName() + ")");
        }
    }

    private static String parseField(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon);
        if (q1 < 0) return null;
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }
}