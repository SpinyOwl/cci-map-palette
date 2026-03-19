package com.spinyowl.ccimap.mixin;

import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MapRegion.class, remap = false)
public interface MapRegionAccessor {
    @Accessor("updateRenderedMapImage")
    boolean cciMapPalette$shouldUpdateRenderedMapImage();

    @Accessor("renderingMapImage")
    boolean cciMapPalette$isRenderingMapImage();
}
