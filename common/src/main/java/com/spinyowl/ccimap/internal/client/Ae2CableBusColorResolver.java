package com.spinyowl.ccimap.internal.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Ae2CableBusColorResolver {
    private static final ResourceLocation CABLE_BUS_ID = new ResourceLocation("ae2", "cable_bus");
    private static final String CABLE_BUS_BLOCK_ENTITY_CLASS = "appeng.blockentity.networking.CableBusBlockEntity";
    private static final String AE_COLOR_CLASS = "appeng.api.util.AEColor";

    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;
    private static Method getColorMethod;
    private static Field mediumVariantField;

    private Ae2CableBusColorResolver() {
    }

    @Nullable
    static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        if (!CABLE_BUS_ID.equals(blockId)) {
            return null;
        }

        initializeReflection();
        if (!reflectionAvailable) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null || !CABLE_BUS_BLOCK_ENTITY_CLASS.equals(blockEntity.getClass().getName())) {
            return null;
        }

        try {
            Object aeColor = getColorMethod.invoke(blockEntity);
            if (aeColor == null) {
                return null;
            }

            if ("TRANSPARENT".equals(((Enum<?>) aeColor).name())) {
                return null;
            }

            int mediumVariant = mediumVariantField.getInt(aeColor);
            return Color4I.rgb(mediumVariant).withAlpha(255);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        synchronized (Ae2CableBusColorResolver.class) {
            if (reflectionInitialized) {
                return;
            }

            try {
                Class<?> cableBusBlockEntityClass = Class.forName(CABLE_BUS_BLOCK_ENTITY_CLASS);
                Class<?> aeColorClass = Class.forName(AE_COLOR_CLASS);

                getColorMethod = cableBusBlockEntityClass.getMethod("getColor");
                mediumVariantField = aeColorClass.getField("mediumVariant");
                reflectionAvailable = true;
            } catch (ReflectiveOperationException ex) {
                reflectionAvailable = false;
            }

            reflectionInitialized = true;
        }
    }
}
