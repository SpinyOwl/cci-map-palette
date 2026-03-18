package com.spinyowl.ccimap.forge;

import com.spinyowl.ccimap.CciMapPaletteCommon;
import com.spinyowl.ccimap.CciMapPaletteClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(CciMapPaletteCommon.MOD_ID)
public final class CciMapPaletteForge {
	public CciMapPaletteForge() {
		CciMapPaletteCommon.init();
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CciMapPaletteClient::init);
	}
}
