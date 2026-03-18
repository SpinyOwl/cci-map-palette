package com.spinyowl.ccimap.fabric;

import com.spinyowl.ccimap.CciMapPaletteCommon;
import net.fabricmc.api.ClientModInitializer;

public final class CciMapPaletteFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CciMapPaletteCommon.init();
		com.spinyowl.ccimap.CciMapPaletteClient.init();
	}
}
