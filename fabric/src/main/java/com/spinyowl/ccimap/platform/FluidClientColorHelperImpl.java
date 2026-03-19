package com.spinyowl.ccimap.platform;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FluidClientColorHelperImpl {
    private static final Map<ResourceLocation, @Nullable Color4I> SPRITE_AVERAGES = new ConcurrentHashMap<>();

    private FluidClientColorHelperImpl() {
    }

    @Nullable
    public static Color4I resolveAutoFluidColor(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluidState.getType());
        if (handler == null) {
            return null;
        }

        TextureAtlasSprite[] sprites = handler.getFluidSprites(world, pos, fluidState);
        if (sprites == null || sprites.length == 0) {
            return null;
        }

        Color4I base = averageSprites(sprites);
        if (base == null) {
            return null;
        }

        int tint = handler.getFluidColor(world, pos, fluidState);
        return multiply(base, tint);
    }

    @Nullable
    private static Color4I averageSprites(TextureAtlasSprite[] sprites) {
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;

        for (TextureAtlasSprite sprite : sprites) {
            Color4I color = averageSpriteColor(sprite);
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
    private static Color4I averageSpriteColor(TextureAtlasSprite sprite) {
        return SPRITE_AVERAGES.computeIfAbsent(sprite.contents().name(), FluidClientColorHelperImpl::loadAverageSpriteColor);
    }

    @Nullable
    private static Color4I loadAverageSpriteColor(ResourceLocation spriteId) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation textureId = new ResourceLocation(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        Resource resource = minecraft.getResourceManager().getResource(textureId).orElse(null);
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
