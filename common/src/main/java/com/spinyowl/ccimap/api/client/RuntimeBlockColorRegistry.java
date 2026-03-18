package com.spinyowl.ccimap.api.client;

import com.spinyowl.ccimap.internal.client.RuntimeBlockColorRegistryImpl;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class RuntimeBlockColorRegistry {
    private RuntimeBlockColorRegistry() {
    }

    public static RuntimeBlockColorHandle register(ResourceLocation blockId, int priority, RuntimeBlockColorResolver resolver) {
        return RuntimeBlockColorRegistryImpl.register(blockId, priority, resolver);
    }

    public static RuntimeBlockColorHandle register(Block block, int priority, RuntimeBlockColorResolver resolver) {
        return register(BuiltInRegistries.BLOCK.getKey(block), priority, resolver);
    }

    public static void invalidateAll() {
        RuntimeBlockColorRegistryImpl.invalidateAll();
    }

    public static boolean hasRules(ResourceLocation blockId) {
        return RuntimeBlockColorRegistryImpl.hasRules(blockId);
    }

    @Nullable
    public static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        return RuntimeBlockColorRegistryImpl.resolve(world, pos, state, blockId);
    }
}
