package com.spinyowl.ccimap.fabric;

import com.spinyowl.ccimap.CciMapPaletteCommon;
import net.fabricmc.api.ModInitializer;

public final class CciMapPaletteFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		CciMapPaletteCommon.init();
	}
}
