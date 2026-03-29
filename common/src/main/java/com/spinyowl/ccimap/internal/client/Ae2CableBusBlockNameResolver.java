package com.spinyowl.ccimap.internal.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

final class Ae2CableBusBlockNameResolver {
    private static final String AE2_NAMESPACE = "ae2";
    private static final String CABLE_BUS_BLOCK_ENTITY_CLASS = "appeng.blockentity.networking.CableBusBlockEntity";
    private static final String GET_CABLE_BUS_METHOD = "getCableBus";
    private static final String GET_PART_METHOD = "getPart";
    private static final String GET_PART_ITEM_METHOD = "getPartItem";

    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;
    private static Class<?> cableBusBlockEntityClass;
    private static Method getCableBusMethod;
    private static Method getPartMethod;
    private static Method getPartItemMethod;

    private Ae2CableBusBlockNameResolver() {
    }

    @Nullable
    static String resolve(Level level, BlockPos pos, ResourceLocation blockId) {
        if (!AE2_NAMESPACE.equals(blockId.getNamespace()) || !"cable_bus".equals(blockId.getPath())) {
            return null;
        }

        initializeReflection();
        if (!reflectionAvailable) {
            return null;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null || !cableBusBlockEntityClass.isInstance(blockEntity)) {
            return null;
        }

        try {
            Object cableBus = getCableBusMethod.invoke(blockEntity);
            if (cableBus == null) {
                return null;
            }

            Object centerPart = getPartMethod.invoke(cableBus, new Object[] { null });
            if (centerPart == null) {
                return null;
            }

            Object partItem = getPartItemMethod.invoke(centerPart);
            if (!(partItem instanceof net.minecraft.world.level.ItemLike itemLike)) {
                return null;
            }

            return itemLike.asItem().getDescription().getString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void initializeReflection() {
        if (reflectionInitialized) {
            return;
        }

        synchronized (Ae2CableBusBlockNameResolver.class) {
            if (reflectionInitialized) {
                return;
            }

            try {
                cableBusBlockEntityClass = Class.forName(CABLE_BUS_BLOCK_ENTITY_CLASS);
                getCableBusMethod = cableBusBlockEntityClass.getMethod(GET_CABLE_BUS_METHOD);
                Class<?> cableBusClass = getCableBusMethod.getReturnType();
                getPartMethod = cableBusClass.getMethod(GET_PART_METHOD, net.minecraft.core.Direction.class);
                Class<?> partClass = getPartMethod.getReturnType();
                getPartItemMethod = partClass.getMethod(GET_PART_ITEM_METHOD);
                reflectionAvailable = true;
            } catch (ReflectiveOperationException ex) {
                reflectionAvailable = false;
            }

            reflectionInitialized = true;
        }
    }
}
