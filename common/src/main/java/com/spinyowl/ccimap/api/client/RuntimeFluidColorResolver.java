package com.spinyowl.ccimap.api.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves a runtime map color for a sampled fluid state.
 */
@FunctionalInterface
public interface RuntimeFluidColorResolver {
    /**
     * Resolve the color for a fluid at a sampled map position.
     */
    @Nullable
    Color4I resolve(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId);
}
