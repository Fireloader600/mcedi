package com.fire.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockRegistry {

    public static class BlockType {
        public final byte id;
        public final String name;
        public final String topTexturePath;
        public final String sideTexturePath;
        public final String bottomTexturePath;
        public final ClassLoader classLoader;
        public int topTexture = 0;
        public int sideTexture = 0;
        public int bottomTexture = 0;

        public BlockType(byte id, String name,
                         String topTexturePath, String sideTexturePath, String bottomTexturePath,
                         ClassLoader cl) {
            this.id = id;
            this.name = name;
            this.topTexturePath = topTexturePath;
            this.sideTexturePath = sideTexturePath;
            this.bottomTexturePath = bottomTexturePath;
            this.classLoader = cl;
        }

        public BlockType(byte id, String name, String texturePath, ClassLoader cl) {
            this(id, name, texturePath, texturePath, texturePath, cl);
        }

        public int getTopTexture() {
            return topTexture > 0 ? topTexture : sideTexture;
        }
        public int getSideTexture() {
            return sideTexture;
        }
        public int getBottomTexture() {
            return bottomTexture > 0 ? bottomTexture : sideTexture;
        }
    }

    private static final Map<Byte, BlockType> REGISTRY = new HashMap<>();
    private static byte nextId = 13;
    private static boolean builtinInit = false;

    public static synchronized void initBuiltin() {
        if (builtinInit) return;
        builtinInit = true;
        ClassLoader cl = BlockRegistry.class.getClassLoader();
        String T = "/assets/minecraft/textures/block/";

        REGISTRY.put(Block.AIR, new BlockType(Block.AIR, "air", null, cl));
        REGISTRY.put(Block.GRASS, new BlockType(Block.GRASS, "grass",
                T + "grass.png", T + "grass_gradient.png", T + "dirt.png", cl));
        REGISTRY.put(Block.STONE, new BlockType(Block.STONE, "stone", T + "stone.png", cl));
        REGISTRY.put(Block.DIRT, new BlockType(Block.DIRT, "dirt", T + "dirt.png", cl));
        REGISTRY.put(Block.PLANKS, new BlockType(Block.PLANKS, "planks", T + "planks.png", cl));
        REGISTRY.put(Block.LOG, new BlockType(Block.LOG, "log", T + "log.png", cl));
        REGISTRY.put(Block.LEAVES, new BlockType(Block.LEAVES, "leaves", T + "leaves.png", cl));
        REGISTRY.put(Block.COBBLESTONE, new BlockType(Block.COBBLESTONE, "cobblestone", T + "cobblestone.png", cl));
        REGISTRY.put(Block.COAL_ORE, new BlockType(Block.COAL_ORE, "coal_ore", T + "coal_ore.png", cl));
        REGISTRY.put(Block.IRON_ORE, new BlockType(Block.IRON_ORE, "iron_ore", T + "iron_ore.png", cl));
        REGISTRY.put(Block.GOLD_ORE, new BlockType(Block.GOLD_ORE, "gold_ore", T + "gold_ore.png", cl));
        REGISTRY.put(Block.DIAMOND_ORE, new BlockType(Block.DIAMOND_ORE, "diamond_ore", T + "diamond_ore.png", cl));
        REGISTRY.put(Block.SAND, new BlockType(Block.SAND, "sand", T + "sand.png", cl));
    }

    public static synchronized int register(String name, String texturePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return register(name, texturePath, cl);
    }

    public static synchronized int register(String name, String texturePath, ClassLoader cl) {
        if (nextId > 127 || nextId < 0) throw new RuntimeException("方块 id 用尽");
        byte id = nextId++;
        REGISTRY.put(id, new BlockType(id, name, texturePath, cl));
        return id;
    }

    public static BlockType get(byte id) {
        return REGISTRY.get(id);
    }

    public static List<BlockType> all() {
        return new ArrayList<>(REGISTRY.values());
    }
}