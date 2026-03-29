package com.spinyowl.ccimap.internal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class RuntimeBlockNameResolver {
    private RuntimeBlockNameResolver() {
    }

    @Nullable
    public static String resolve(Level level, BlockPos pos, BlockState state, Block block, ResourceLocation blockId) {
        String specialCase = Ae2CableBusBlockNameResolver.resolve(level, pos, blockId);
        if (specialCase != null) {
            return specialCase;
        }

        specialCase = GtceuCableBlockNameResolver.resolve(block, blockId);
        if (specialCase != null) {
            return specialCase;
        }

        return null;
    }

    @Nullable
    public static String resolveAtCurrentLevel(int x, int y, int z, Block block, ResourceLocation blockId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = minecraft.level.getBlockState(pos);
        return resolve(minecraft.level, pos, state, block, blockId);
    }

    static String componentToString(Component component) {
        return component.getString();
    }
}
