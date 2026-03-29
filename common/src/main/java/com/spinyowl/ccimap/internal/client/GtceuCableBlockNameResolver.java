package com.spinyowl.ccimap.internal.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class GtceuCableBlockNameResolver {
    private static final String GTCEU_NAMESPACE = "gtceu";
    private static final String TAGPREFIX_WIRE_PREFIX = "tagprefix.wire_gt_";
    private static final String TAGPREFIX_CABLE_PREFIX = "tagprefix.cable_gt_";
    private static final String MATERIAL_LOCALIZED_NAME_METHOD = "getLocalizedName";

    private static volatile boolean reflectionInitialized;
    private static volatile boolean reflectionAvailable;
    private static Field materialField;
    private static Method getLocalizedNameMethod;

    private GtceuCableBlockNameResolver() {
    }

    @Nullable
    public static String resolve(Block block, ResourceLocation blockId) {
        if (!GTCEU_NAMESPACE.equals(blockId.getNamespace())) {
            return null;
        }

        String descriptionId = block.getDescriptionId();
        if (!descriptionId.startsWith(TAGPREFIX_WIRE_PREFIX) && !descriptionId.startsWith(TAGPREFIX_CABLE_PREFIX)) {
            return null;
        }

        initializeReflection(block.getClass());
        if (!reflectionAvailable) {
            return null;
        }

        try {
            Object material = materialField.get(block);
            if (material == null) {
                return null;
            }

            Object localizedName = getLocalizedNameMethod.invoke(material);
            if (!(localizedName instanceof Component materialName)) {
                return null;
            }

            return Component.translatable(descriptionId, materialName).getString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static void initializeReflection(Class<?> cableBlockClass) {
        if (reflectionInitialized) {
            return;
        }

        synchronized (GtceuCableBlockNameResolver.class) {
            if (reflectionInitialized) {
                return;
            }

            try {
                materialField = cableBlockClass.getField("material");
                getLocalizedNameMethod = materialField.getType().getMethod(MATERIAL_LOCALIZED_NAME_METHOD);
                reflectionAvailable = true;
            } catch (ReflectiveOperationException ex) {
                reflectionAvailable = false;
            }

            reflectionInitialized = true;
        }
    }
}
