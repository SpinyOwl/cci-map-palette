package com.spinyowl.ccimap.api.client;

import com.spinyowl.ccimap.internal.client.RuntimeFluidColorRegistryImpl;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only runtime registry for fluid map color overrides.
 */
public final class RuntimeFluidColorRegistry {
    private RuntimeFluidColorRegistry() {
    }

    public static RuntimeFluidColorHandle register(ResourceLocation fluidId, int priority, RuntimeFluidColorResolver resolver) {
        return RuntimeFluidColorRegistryImpl.register(fluidId, priority, resolver);
    }

    public static RuntimeFluidColorHandle register(Fluid fluid, int priority, RuntimeFluidColorResolver resolver) {
        return register(BuiltInRegistries.FLUID.getKey(fluid), priority, resolver);
    }

    public static void invalidateAll() {
        RuntimeFluidColorRegistryImpl.invalidateAll();
    }

    public static boolean hasRules(ResourceLocation fluidId) {
        return RuntimeFluidColorRegistryImpl.hasRules(fluidId);
    }

    @Nullable
    public static Color4I resolve(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        return RuntimeFluidColorRegistryImpl.resolve(world, pos, fluidState, fluidId);
    }
}
