package com.fire.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockRegistry {

    public static class BlockType {
        public final byte id;
        public final String name;
        public final String texturePath;   // classpath 路径，air 为 null
        public final ClassLoader classLoader;
        public int texture = 0;            // 运行时 GL 纹理 id

        public BlockType(byte id, String name, String texturePath, ClassLoader cl) {
            this.id = id;
            this.name = name;
            this.texturePath = texturePath;
            this.classLoader = cl;
        }
    }

    private static final Map<Byte, BlockType> REGISTRY = new HashMap<>();
    private static byte nextId = 3;
    private static boolean builtinInit = false;

    public static synchronized void initBuiltin() {
        if (builtinInit) return;
        builtinInit = true;
        ClassLoader cl = BlockRegistry.class.getClassLoader();
        REGISTRY.put(Block.AIR,   new BlockType(Block.AIR,   "air",   null, cl));
        REGISTRY.put(Block.GRASS, new BlockType(Block.GRASS, "grass", "/assets/minecraft/textures/block/grass.png", cl));
        REGISTRY.put(Block.STONE, new BlockType(Block.STONE, "stone", "/assets/minecraft/textures/block/stone.png", cl));
    }

    /** Mod 调用，用当前线程上下文 ClassLoader 作为资源来源 */
    public static synchronized int register(String name, String texturePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return register(name, texturePath, cl);
    }

    public static synchronized int register(String name, String texturePath, ClassLoader cl) {
        if (nextId > 127 || nextId < 0) throw new RuntimeException("方块 id 用尽");
        byte id = nextId++;
        REGISTRY.put(id, new BlockType(id, name, texturePath, cl));
        System.out.println("[EdiPack] 注册方块: " + name + " (id=" + id + ")");
        return id;
    }

    public static BlockType get(byte id) {
        return REGISTRY.get(id);
    }

    public static List<BlockType> all() {
        return new ArrayList<>(REGISTRY.values());
    }
}