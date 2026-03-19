package com.spinyowl.ccimap.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FluidClientColorHelperImpl {
    private static final Map<ResourceLocation, @Nullable Color4I> TEXTURE_AVERAGES = new ConcurrentHashMap<>();

    private FluidClientColorHelperImpl() {
    }

    @Nullable
    public static Color4I resolveAutoFluidColor(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluidState);
        Color4I base = average(extensions.getStillTexture(fluidState, world, pos), extensions.getFlowingTexture(fluidState, world, pos));
        if (base == null) {
            return null;
        }

        int tint = extensions.getTintColor(fluidState, world, pos);
        return multiply(base, tint);
    }

    @Nullable
    private static Color4I average(@Nullable ResourceLocation still, @Nullable ResourceLocation flowing) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;

        for (ResourceLocation textureId : new ResourceLocation[]{still, flowing}) {
            if (textureId == null) {
                continue;
            }

            Color4I color = averageTexture(textureId);
            if (color == null) {
                continue;
            }

            red += color.redi();
            green += color.greeni();
            blue += color.bluei();
            count++;
        }

        if (count == 0) {
            return null;
        }

        return Color4I.rgb((int) (red / count), (int) (green / count), (int) (blue / count)).withAlpha(255);
    }

    @Nullable
    private static Color4I averageTexture(ResourceLocation textureId) {
        return TEXTURE_AVERAGES.computeIfAbsent(textureId, FluidClientColorHelperImpl::loadAverageTexture);
    }

    @Nullable
    private static Color4I loadAverageTexture(ResourceLocation textureId) {
        ResourceLocation pngId = new ResourceLocation(textureId.getNamespace(), "textures/" + textureId.getPath() + ".png");
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(pngId).orElse(null);
        if (resource == null) {
            return null;
        }

        try (InputStream inputStream = resource.open(); NativeImage image = NativeImage.read(inputStream)) {
            long red = 0L;
            long green = 0L;
            long blue = 0L;
            long count = 0L;

            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int alpha = FastColor.ABGR32.alpha(pixel);
                    if (alpha == 0) {
                        continue;
                    }

                    red += FastColor.ABGR32.red(pixel);
                    green += FastColor.ABGR32.green(pixel);
                    blue += FastColor.ABGR32.blue(pixel);
                    count++;
                }
            }

            if (count == 0L) {
                return null;
            }

            return Color4I.rgb((int) (red / count), (int) (green / count), (int) (blue / count)).withAlpha(255);
        } catch (IOException ex) {
            return null;
        }
    }

    private static Color4I multiply(Color4I color, int tint) {
        int red = color.redi() * ((tint >> 16) & 0xFF) / 255;
        int green = color.greeni() * ((tint >> 8) & 0xFF) / 255;
        int blue = color.bluei() * (tint & 0xFF) / 255;
        return Color4I.rgb(red, green, blue).withAlpha(255);
    }
}
