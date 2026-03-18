package com.spinyowl.ccimap.api.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface RuntimeBlockColorResolver {
    @Nullable
    Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId);
}
