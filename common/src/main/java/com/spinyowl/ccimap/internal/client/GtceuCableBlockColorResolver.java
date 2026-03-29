package com.spinyowl.ccimap.internal.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class GtceuCableBlockColorResolver {
    private static final String GTCEU_NAMESPACE = "gtceu";
    private static final String CABLE_BLOCK_CLASS = "com.gregtechceu.gtceu.common.block.CableBlock";
    private static final String PIPE_BLOCK_ENTITY_CLASS = "com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity";
    private static final String MATERIAL_CLASS = "com.gregtechceu.gtceu.api.data.chemical.material.Material";

    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;
    private static Class<?> cableBlockClass;
    private static Class<?> pipeBlockEntityClass;
    private static Field materialField;
    private static Method getMaterialRgbMethod;
    private static Method getPaintingColorMethod;
    private static Method getDefaultPaintingColorMethod;

    private GtceuCableBlockColorResolver() {
    }

    @Nullable
    static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        if (!GTCEU_NAMESPACE.equals(blockId.getNamespace())) {
            return null;
        }

        initializeReflection();
        if (!reflectionAvailable) {
            return null;
        }

        Block block = state.getBlock();
        if (!cableBlockClass.isInstance(block)) {
            return null;
        }

        Color4I paintedColor = resolvePaintedColor(world.getBlockEntity(pos));
        if (paintedColor != null) {
            return paintedColor;
        }

        try {
            Object material = materialField.get(block);
            if (material == null) {
                return null;
            }

            int rgb = (int) getMaterialRgbMethod.invoke(material);
            return Color4I.rgb(rgb).withAlpha(255);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    @Nullable
    private static Color4I resolvePaintedColor(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !pipeBlockEntityClass.isInstance(blockEntity)) {
            return null;
        }

        try {
            int paintingColor = (int) getPaintingColorMethod.invoke(blockEntity);
            int defaultPaintingColor = (int) getDefaultPaintingColorMethod.invoke(blockEntity);
            if (paintingColor == defaultPaintingColor || paintingColor < 0) {
                return null;
            }

            return Color4I.rgb(paintingColor).withAlpha(255);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        synchronized (GtceuCableBlockColorResolver.class) {
            if (reflectionInitialized) {
                return;
            }

            try {
                cableBlockClass = Class.forName(CABLE_BLOCK_CLASS);
                pipeBlockEntityClass = Class.forName(PIPE_BLOCK_ENTITY_CLASS);
                Class<?> materialClass = Class.forName(MATERIAL_CLASS);

                materialField = cableBlockClass.getField("material");
                getMaterialRgbMethod = materialClass.getMethod("getMaterialRGB");
                getPaintingColorMethod = pipeBlockEntityClass.getMethod("getPaintingColor");
                getDefaultPaintingColorMethod = pipeBlockEntityClass.getMethod("getDefaultPaintingColor");
                reflectionAvailable = true;
            } catch (ReflectiveOperationException ex) {
                reflectionAvailable = false;
            }

            reflectionInitialized = true;
        }
    }
}
