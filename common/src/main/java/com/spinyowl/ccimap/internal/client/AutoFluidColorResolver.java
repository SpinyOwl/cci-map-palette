package com.spinyowl.ccimap.internal.client;

import com.spinyowl.ccimap.platform.FluidClientColorHelper;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

final class AutoFluidColorResolver {
    private AutoFluidColorResolver() {
    }

    static boolean hasResolver(ResourceLocation fluidId) {
        return AutoBlockColorConfig.contains(fluidId.getNamespace());
    }

    @Nullable
    static Color4I resolve(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        if (fluidState.isEmpty() || !hasResolver(fluidId)) {
            return null;
        }

        return FluidClientColorHelper.resolveAutoFluidColor(world, pos, fluidState, fluidId);
    }
}
