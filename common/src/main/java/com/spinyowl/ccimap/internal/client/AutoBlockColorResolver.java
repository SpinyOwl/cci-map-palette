package com.spinyowl.ccimap.internal.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoBlockColorResolver {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<ResourceLocation, @Nullable Color4I> SPRITE_AVERAGES = new ConcurrentHashMap<>();
    private static final int CONFIG_POLL_INTERVAL_TICKS = 20;
    private static int ticksUntilPoll = CONFIG_POLL_INTERVAL_TICKS;

    private AutoBlockColorResolver() {
    }

    public static void init() {
        AutoBlockColorConfig.load();
    }

    public static void tick() {
        if (--ticksUntilPoll > 0) {
            return;
        }

        ticksUntilPoll = CONFIG_POLL_INTERVAL_TICKS;
        if (AutoBlockColorConfig.reloadIfChanged()) {
            RuntimeBlockColorRegistryImpl.invalidateAll();
        }
    }

    static boolean hasResolver(ResourceLocation blockId) {
        return AutoBlockColorConfig.contains(blockId.getNamespace());
    }

    @Nullable
    static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        if (!hasResolver(blockId)) {
            return null;
        }

        Color4I specialCase = Ae2ColorableBlockEntityColorResolver.resolve(world, pos, state, blockId);
        if (specialCase != null) {
            if (world instanceof Level level) {
                String resolvedName = Ae2CableBusBlockNameResolver.resolve(level, pos, blockId);
                if (resolvedName != null) {
                    RuntimeBlockNameRegistry.put(blockId, resolvedName);
                }
            }
            return specialCase;
        }

        specialCase = GtceuCableBlockColorResolver.resolve(world, pos, state, blockId);
        if (specialCase != null) {
            return specialCase;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        if (state.getRenderShape() != RenderShape.MODEL) {
            return null;
        }

        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        long seed = state.getSeed(pos);
        ArrayList<Color4I> colors = new ArrayList<>();

        collectQuadColors(colors, model.getQuads(state, null, seededRandom(seed)), minecraft, world, pos, state);
        for (Direction direction : DIRECTIONS) {
            collectQuadColors(colors, model.getQuads(state, direction, seededRandom(seed)), minecraft, world, pos, state);
        }

        if (colors.isEmpty()) {
            return averageSpriteColor(model.getParticleIcon());
        }

        long red = 0L;
        long green = 0L;
        long blue = 0L;
        for (Color4I color : colors) {
            red += color.redi();
            green += color.greeni();
            blue += color.bluei();
        }

        int size = colors.size();
        return Color4I.rgb((int) (red / size), (int) (green / size), (int) (blue / size)).withAlpha(255);
    }

    private static void collectQuadColors(
        List<Color4I> colors,
        List<BakedQuad> quads,
        Minecraft minecraft,
        BlockAndTintGetter world,
        BlockPos pos,
        BlockState state
    ) {
        for (BakedQuad quad : quads) {
            Color4I spriteColor = averageSpriteColor(quad.getSprite());
            if (spriteColor == null) {
                continue;
            }

            if (quad.isTinted()) {
                int tint = minecraft.getBlockColors().getColor(state, world, pos, quad.getTintIndex());
                if (tint != -1) {
                    spriteColor = multiply(spriteColor, tint);
                }
            }

            colors.add(spriteColor.withAlpha(255));
        }
    }

    @Nullable
    private static Color4I averageSpriteColor(TextureAtlasSprite sprite) {
        return SPRITE_AVERAGES.computeIfAbsent(sprite.contents().name(), AutoBlockColorResolver::loadAverageSpriteColor);
    }

    @Nullable
    private static Color4I loadAverageSpriteColor(ResourceLocation spriteId) {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceLocation textureId = new ResourceLocation(spriteId.getNamespace(), "textures/" + spriteId.getPath() + ".png");
        try {
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
            }
        } catch (IOException ex) {
            return null;
        }
    }

    private static RandomSource seededRandom(long seed) {
        RandomSource random = RandomSource.create();
        random.setSeed(seed);
        return random;
    }

    private static Color4I multiply(Color4I color, int tint) {
        int red = color.redi() * ((tint >> 16) & 0xFF) / 255;
        int green = color.greeni() * ((tint >> 8) & 0xFF) / 255;
        int blue = color.bluei() * (tint & 0xFF) / 255;
        return Color4I.rgb(red, green, blue).withAlpha(255);
    }
}
