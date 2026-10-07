package com.fire.world;

import com.fire.block.Block;

public class WorldGen {
    public static final int SIZE_X = 128;
    public static final int SIZE_Y = 144;   // 9 × 16，索引 0..143；放置上限由 Main 控制为 128
    public static final int SIZE_Z = 128;

    // 超平坦：y=0..61 石头（62 层），y=62 草（1 层），y>62 空气
    public static final int STONE_TOP_Y = 61;
    public static final int GRASS_Y = 62;

    public static byte[][][] generate() {
        byte[][][] world = new byte[SIZE_X][SIZE_Y][SIZE_Z];
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = 0; y <= STONE_TOP_Y; y++) {
                    world[x][y][z] = Block.STONE;
                }
                world[x][GRASS_Y][z] = Block.GRASS;
            }
        }
        return world;
    }
}