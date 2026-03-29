package com.spinyowl.ccimap.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spinyowl.ccimap.internal.client.RuntimeBlockNameRegistry;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftbchunks.util.HeightUtils;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.util.StringUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = LargeMapScreen.class, remap = false)
abstract class LargeMapScreenNameOverlayMixin {
    private static final Color4I BACKGROUND_COLOR = Color4I.rgb(0x202225);
    private static final Field BLOCK_X_FIELD = findField("blockX");
    private static final Field BLOCK_Y_FIELD = findField("blockY");
    private static final Field BLOCK_Z_FIELD = findField("blockZ");
    private static final Field BLOCK_INDEX_FIELD = findField("blockIndex");

    @Shadow(remap = false) private RegionMapPanel regionPanel;
    @Shadow(remap = false) MapDimension dimension;
    @Shadow(remap = false) private int zoom;
    @Shadow(remap = false) private int minZoom;

    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true, remap = false)
    private void cciMapPalette$drawForeground(
        GuiGraphics graphics,
        Theme theme,
        int x,
        int y,
        int w,
        int h,
        CallbackInfo ci
    ) {
        ci.cancel();

        PoseStack poseStack = graphics.pose();
        int blockY = getRegionPanelInt(BLOCK_Y_FIELD);
        int blockX = getRegionPanelInt(BLOCK_X_FIELD);
        int blockZ = getRegionPanelInt(BLOCK_Z_FIELD);
        String coords = "X: " + blockX + ", Y: " + (blockY == HeightUtils.UNKNOWN ? "??" : blockY) + ", Z: " + blockZ;

        if (blockY != HeightUtils.UNKNOWN) {
            int blockIndex = getRegionPanelInt(BLOCK_INDEX_FIELD);
            MapRegion region = dimension.getRegion(XZ.regionFromBlock(blockX, blockZ));
            MapRegionData data = region.getData();

            if (data != null) {
                int waterLightAndBiome = data.waterLightAndBiome[blockIndex] & 0xFFFF;
                ResourceKey<Biome> biome = dimension.getManager().getBiomeKey(waterLightAndBiome);
                Block block = dimension.getManager().getBlock(data.getBlockIndex(blockIndex));
                String blockName = I18n.get(block.getDescriptionId());
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
                if (blockId != null) {
                    String overrideName = RuntimeBlockNameRegistry.get(blockId);
                    if (overrideName != null && !overrideName.isBlank()) {
                        blockName = overrideName;
                    }
                }

                coords = coords + " | " + I18n.get("biome." + biome.location().getNamespace() + "." + biome.location().getPath())
                    + " | " + blockName;

                if ((waterLightAndBiome & (1 << 15)) != 0) {
                    coords += " (in water)";
                }
            }
        }

        int coordsw = theme.getStringWidth(coords) / 2;
        BACKGROUND_COLOR.withAlpha(150).draw(graphics, x + (w - coordsw) / 2, y + h - 6, coordsw + 4, 6);
        poseStack.pushPose();
        poseStack.translate(x + (w - coordsw) / 2F + 2F, y + h - 5, 0F);
        poseStack.scale(0.5F, 0.5F, 1F);
        theme.drawString(graphics, coords, 0, 0, Theme.SHADOW);
        poseStack.popPose();

        if (FTBChunksClientConfig.DEBUG_INFO.get()) {
            long memory = MapManager.getInstance().map(MapManager::estimateMemoryUsage).orElse(0L);
            String memoryUsage = "Estimated Memory Usage: " + StringUtils.formatDouble00(memory / 1024D / 1024D) + " MB";
            int memoryUsagew = theme.getStringWidth(memoryUsage) / 2;

            BACKGROUND_COLOR.withAlpha(150).draw(graphics, x + (w - memoryUsagew) - 2, y, memoryUsagew + 4, 6);

            poseStack.pushPose();
            poseStack.translate(x + (w - memoryUsagew) - 1F, y + 1, 0F);
            poseStack.scale(0.5F, 0.5F, 1F);
            theme.drawString(graphics, memoryUsage, 0, 0, Theme.SHADOW);
            poseStack.popPose();
        }

        if (zoom == minZoom && zoom > 1) {
            Component zoomWarn = Component.translatable("ftbchunks.zoom_warning");
            poseStack.pushPose();
            poseStack.translate(x + w / 2F, y + 1, 0F);
            poseStack.scale(0.5F, 0.5F, 1F);
            theme.drawString(graphics, zoomWarn, 0, 0, Color4I.rgb(0xF0C000), Theme.CENTERED);
            poseStack.popPose();
        }
    }

    private static Field findField(String name) {
        try {
            Field field = RegionMapPanel.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access RegionMapPanel." + name, ex);
        }
    }

    private int getRegionPanelInt(Field field) {
        try {
            return field.getInt(regionPanel);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read RegionMapPanel hover state", ex);
        }
    }
}
