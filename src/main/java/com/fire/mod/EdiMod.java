package com.fire.mod;

import com.fire.block.BlockRegistry;
import com.fire.world.WorldTag;

public interface EdiMod {
    void onInit(BlockRegistry registry);
    default void onWorldTag(WorldTag tag) {}
}