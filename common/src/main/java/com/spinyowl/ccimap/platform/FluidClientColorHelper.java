package com.spinyowl.ccimap.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public final class FluidClientColorHelper {
    private FluidClientColorHelper() {
    }

    @ExpectPlatform
    @Nullable
    public static Color4I resolveAutoFluidColor(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        throw new AssertionError();
    }
}
