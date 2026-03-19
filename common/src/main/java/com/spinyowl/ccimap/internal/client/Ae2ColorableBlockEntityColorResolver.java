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

final class Ae2ColorableBlockEntityColorResolver {
    private static final String AE2_NAMESPACE = "ae2";
    private static final String COLORABLE_BLOCK_ENTITY_CLASS = "appeng.api.implementations.blockentities.IColorableBlockEntity";
    private static final String AE_COLOR_CLASS = "appeng.api.util.AEColor";

    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;
    private static Class<?> colorableBlockEntityClass;
    private static Method getColorMethod;
    private static Field mediumVariantField;

    private Ae2ColorableBlockEntityColorResolver() {
    }

    @Nullable
    static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        if (!AE2_NAMESPACE.equals(blockId.getNamespace())) {
            return null;
        }

        initializeReflection();
        if (!reflectionAvailable) {
            return null;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity == null || !colorableBlockEntityClass.isInstance(blockEntity)) {
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

        synchronized (Ae2ColorableBlockEntityColorResolver.class) {
            if (reflectionInitialized) {
                return;
            }

            try {
                colorableBlockEntityClass = Class.forName(COLORABLE_BLOCK_ENTITY_CLASS);
                Class<?> aeColorClass = Class.forName(AE_COLOR_CLASS);

                getColorMethod = colorableBlockEntityClass.getMethod("getColor");
                mediumVariantField = aeColorClass.getField("mediumVariant");
                reflectionAvailable = true;
            } catch (ReflectiveOperationException ex) {
                reflectionAvailable = false;
            }

            reflectionInitialized = true;
        }
    }
}
