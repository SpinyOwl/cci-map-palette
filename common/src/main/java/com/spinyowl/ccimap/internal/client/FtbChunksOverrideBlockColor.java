package com.spinyowl.ccimap.internal.client;

import dev.ftb.mods.ftbchunks.client.map.color.BlockColor;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

public final class FtbChunksOverrideBlockColor implements BlockColor {
    private static final ThreadLocal<Color4I> ACTIVE_COLOR = new ThreadLocal<>();
    static final FtbChunksOverrideBlockColor INSTANCE = new FtbChunksOverrideBlockColor();

    private FtbChunksOverrideBlockColor() {
    }

    public static BlockColor install(Color4I color) {
        ACTIVE_COLOR.set(color.withAlpha(255));
        return INSTANCE;
    }

    @Override
    public Color4I getBlockColor(BlockAndTintGetter world, BlockPos pos) {
        Color4I color = ACTIVE_COLOR.get();
        ACTIVE_COLOR.remove();
        return color == null ? Color4I.RED : color;
    }
}
