package com.spinyowl.ccimap.internal.client;

import com.spinyowl.ccimap.api.client.RuntimeBlockColorRegistry;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.ftb.mods.ftbchunks.ColorMapLoader;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.FTBChunksClientConfig;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapMode;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.color.BlockColor;
import dev.ftb.mods.ftbchunks.client.map.color.BlockColors;
import dev.ftb.mods.ftbchunks.client.map.color.ColorUtils;
import dev.ftb.mods.ftbchunks.client.map.color.CustomBlockColor;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CciMapPaletteDebugOverlay {
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static final int PANEL_BACKGROUND = 0x90000000;
    private static final int PANEL_TEXT = 0xFFE0E0E0;
    private static final int PANEL_TITLE = 0xFF80D8FF;
    private static final int PANEL_ACCENT = 0xFFBDBDBD;
    private static boolean initialized;

    private CciMapPaletteDebugOverlay() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        ClientGuiEvent.RENDER_HUD.register(CciMapPaletteDebugOverlay::renderHud);
        ClientGuiEvent.DEBUG_TEXT_LEFT.register(CciMapPaletteDebugOverlay::appendDebugText);
    }

    public static boolean toggle() {
        while (true) {
            boolean current = ENABLED.get();
            boolean updated = !current;
            if (ENABLED.compareAndSet(current, updated)) {
                return updated;
            }
        }
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static Component feedback(boolean enabled) {
        return Component.literal("CCI Map Palette debug " + (enabled ? "enabled" : "disabled"))
            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private static void appendDebugText(List<String> lines) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ENABLED.get() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        List<String> debugLines = collectLines(minecraft);
        if (debugLines.isEmpty()) {
            return;
        }

        lines.add("");
        lines.addAll(debugLines);
    }

    private static void renderHud(GuiGraphics graphics, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ENABLED.get() || minecraft.level == null || minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null || minecraft.options.renderDebug) {
            return;
        }

        List<String> lines = collectLines(minecraft);
        if (lines.isEmpty()) {
            return;
        }

        drawPanel(graphics, minecraft.font, lines);
    }

    private static void drawPanel(GuiGraphics graphics, Font font, List<String> lines) {
        int x = 4;
        int y = 4;
        int lineHeight = 9;
        int width = 0;

        for (String line : lines) {
            width = Math.max(width, font.width(line));
        }

        int panelHeight = lines.size() * lineHeight + 6;
        graphics.fill(x - 2, y - 2, x + width + 4, y + panelHeight, PANEL_BACKGROUND);

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? PANEL_TITLE : lines.get(i).startsWith("  ") ? PANEL_TEXT : PANEL_ACCENT;
            graphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
    }

    private static List<String> collectLines(Minecraft minecraft) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("CCI Map Palette Debug");

        if (!(minecraft.hitResult instanceof BlockHitResult hitResult) || hitResult.getType() != HitResult.Type.BLOCK || minecraft.level == null) {
            lines.add("  target: none");
            return lines;
        }

        BlockPos pos = hitResult.getBlockPos();
        Level level = minecraft.level;
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            lines.add("  target: unknown block");
            return lines;
        }

        Color4I runtimeOverride = RuntimeBlockColorRegistry.resolve(level, pos, state, blockId);
        ColorComputation computation = computeFtbColor(level, pos, state, blockId, runtimeOverride);
        MapPixelInfo mapPixelInfo = readRenderedPixel(pos);

        lines.add("  block: " + formatBlockState(blockId, state));
        lines.add("  pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        lines.add("  map_mode: " + FTBChunksClientConfig.MAP_MODE.get().name().toLowerCase(Locale.ROOT));
        lines.add("  ignored: " + yesNo(FTBChunksClient.INSTANCE.skipBlock(state)));
        lines.add("  runtime_override: " + formatColor(runtimeOverride));
        lines.add("  ftb_source: " + computation.source());
        lines.add("  ftb_base_color: " + formatColor(computation.color()));
        lines.add("  map_pixel: " + formatColor(mapPixelInfo.color()) + mapPixelInfo.suffix());
        return lines;
    }

    private static ColorComputation computeFtbColor(Level level, BlockPos pos, BlockState state, ResourceLocation blockId, @Nullable Color4I runtimeOverride) {
        MapMode mapMode = FTBChunksClientConfig.MAP_MODE.get();
        boolean hasWater = !state.getFluidState().isEmpty();

        if (mapMode == MapMode.TOPOGRAPHY) {
            int index = pos.getY() + (hasWater ? 256 : 0);
            Color4I[] palette = ColorUtils.getTopographyPalette();
            return new ColorComputation("topography", index >= 0 && index < palette.length ? palette[index].withAlpha(255) : null);
        }

        if (mapMode == MapMode.BLOCKS) {
            int index = MapDimension.getCurrent()
                .map(MapDimension::getManager)
                .map(manager -> manager.getBlockColorIndex(blockId))
                .orElse(0);
            return new ColorComputation("block_index", Color4I.rgb(index));
        }

        if (mapMode == MapMode.LIGHT_SOURCES) {
            return new ColorComputation("light_sources", ColorUtils.getLightMapPalette()[level.getBrightness(LightLayer.BLOCK, pos)][15].withAlpha(255));
        }

        Color4I color;
        String source;
        if (runtimeOverride != null) {
            color = runtimeOverride.withAlpha(255);
            source = "runtime_override";
        } else {
            BlockColor blockColor = ColorMapLoader.getBlockColor(blockId);
            if (blockColor.isIgnored()) {
                return new ColorComputation("ignored", null);
            } else if (blockColor instanceof CustomBlockColor customBlockColor) {
                color = customBlockColor.getColor().withAlpha(255);
                source = "custom";
            } else if (blockColor == BlockColors.FOLIAGE) {
                color = Color4I.rgb(BiomeColors.getAverageFoliageColor(level, pos))
                    .withAlpha(255)
                    .withTint(Color4I.BLACK.withAlpha(FTBChunksClientConfig.FOLIAGE_DARKNESS.get()));
                source = "foliage";
            } else if (blockColor == BlockColors.GRASS) {
                color = Color4I.rgb(BiomeColors.getAverageGrassColor(level, pos))
                    .withAlpha(255)
                    .withTint(Color4I.BLACK.withAlpha(FTBChunksClientConfig.GRASS_DARKNESS.get()));
                source = "grass";
            } else {
                color = blockColor.getBlockColor(level, pos).withAlpha(255);
                source = "dynamic";
            }
        }

        if (mapMode == MapMode.NIGHT) {
            color = color.withTint(ColorUtils.getLightMapPalette()[level.getBrightness(LightLayer.BLOCK, pos)][15].withAlpha(230));
        }

        if (hasWater) {
            color = color.withTint(Color4I.rgb(BiomeColors.getAverageWaterColor(level, pos)).withAlpha(FTBChunksClientConfig.WATER_VISIBILITY.get()));
        }

        if (FTBChunksClientConfig.REDUCED_COLOR_PALETTE.get()) {
            color = ColorUtils.reduce(color);
        }

        return new ColorComputation(source, color.withAlpha(255));
    }

    private static MapPixelInfo readRenderedPixel(BlockPos pos) {
        return MapDimension.getCurrent()
            .map(mapDimension -> {
                MapRegion region = mapDimension.getRegions().get(XZ.of(Math.floorDiv(pos.getX(), 512), Math.floorDiv(pos.getZ(), 512)));
                if (region == null) {
                    return new MapPixelInfo(null, " (region not loaded)");
                }

                int nativeColor = region.getRenderedMapImage().getPixelRGBA(pos.getX() & 511, pos.getZ() & 511);
                if (nativeColor == 0) {
                    return new MapPixelInfo(null, " (not rendered yet)");
                }

                return new MapPixelInfo(Color4I.rgb(ColorUtils.convertFromNative(nativeColor)).withAlpha(255), "");
            })
            .orElseGet(() -> new MapPixelInfo(null, " (map unavailable)"));
    }

    private static String formatBlockState(ResourceLocation blockId, BlockState state) {
        if (state.getValues().isEmpty()) {
            return blockId.toString();
        }

        StringBuilder builder = new StringBuilder(blockId.toString()).append('[');
        boolean first = true;
        for (var entry : state.getValues().entrySet()) {
            if (!first) {
                builder.append(',');
            }

            first = false;
            builder.append(entry.getKey().getName()).append('=').append(entry.getValue());
        }
        return builder.append(']').toString();
    }

    private static String formatColor(@Nullable Color4I color) {
        if (color == null) {
            return "none";
        }

        return String.format("#%02X%02X%02X", color.redi(), color.greeni(), color.bluei());
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private record ColorComputation(String source, @Nullable Color4I color) {
    }

    private record MapPixelInfo(@Nullable Color4I color, String suffix) {
    }
}
