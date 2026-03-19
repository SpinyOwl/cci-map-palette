package com.spinyowl.ccimap.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.spinyowl.ccimap.api.client.RuntimeBlockColorRegistry;
import com.spinyowl.ccimap.api.client.RuntimeFluidColorRegistry;
import com.spinyowl.ccimap.internal.client.FtbChunksOverrideBlockColor;
import dev.ftb.mods.ftbchunks.client.map.MapMode;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftbchunks.client.map.RenderMapImageTask;
import dev.ftb.mods.ftbchunks.client.map.color.BlockColor;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderMapImageTask.class, remap = false)
abstract class RenderMapImageTaskMixin {
    @Shadow(remap = false) public MapRegion region;

    @ModifyExpressionValue(
        method = "runMapTask",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftbchunks/client/map/MapManager;getBlockColor(I)Ldev/ftb/mods/ftbchunks/client/map/color/BlockColor;",
            remap = false
        ),
        remap = false
    )
    private BlockColor cciMapPalette$overrideRuntimeColor(
        BlockColor original,
        @Local(name = "data") MapRegionData data,
        @Local(name = "index") int index,
        @Local(name = "ax") int ax,
        @Local(name = "az") int az,
        @Local(name = "mapMode") MapMode mapMode,
        @Local(name = "waterHeightFactor") int waterHeightFactor,
        @Local(name = "world") Level world
    ) {
        if (world == null || mapMode == MapMode.TOPOGRAPHY || mapMode == MapMode.BLOCKS || mapMode == MapMode.LIGHT_SOURCES) {
            return original;
        }

        try {
            int blockY = getHeight(mapMode, waterHeightFactor, data.waterLightAndBiome[index], data.height[index]);
            int blockX = region.pos.x() * 512 + ax;
            int blockZ = region.pos.z() * 512 + az;
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(blockX, blockY, blockZ);
            BlockState state = world.getBlockState(pos);
            FluidState fluidState = state.getFluidState();
            if (!fluidState.isEmpty()) {
                Fluid fluid = fluidState.getType();
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
                if (fluidId != null && RuntimeFluidColorRegistry.hasRules(fluidId)) {
                    Color4I override = RuntimeFluidColorRegistry.resolve(world, pos, fluidState, fluidId);
                    if (override != null) {
                        return FtbChunksOverrideBlockColor.install(override);
                    }
                }
            }

            Block block = region.dimension.getManager().getBlock(data.getBlockIndex(index));
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            if (blockId == null || !RuntimeBlockColorRegistry.hasRules(blockId)) {
                return original;
            }

            Color4I override = RuntimeBlockColorRegistry.resolve(world, pos, state, blockId);
            return override == null ? original : FtbChunksOverrideBlockColor.install(override);
        } catch (Exception ex) {
            return original;
        }
    }

    private static int getHeight(MapMode mode, int waterHeightFactor, short waterLightAndBiome, short height) {
        if ((((waterLightAndBiome & 0xFFFF) >> 15) & 1) != 0 && mode != MapMode.TOPOGRAPHY) {
            if (waterHeightFactor == 0) {
                return 62;
            }

            return ((int) height / waterHeightFactor) * waterHeightFactor + waterHeightFactor - 1;
        }

        return height;
    }
}
