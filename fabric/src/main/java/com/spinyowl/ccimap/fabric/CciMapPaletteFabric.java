package com.spinyowl.ccimap.fabric;

import com.spinyowl.ccimap.CciMapPaletteCommon;
import com.spinyowl.ccimap.CciMapPaletteClient;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public final class CciMapPaletteFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CciMapPaletteCommon.init();
		CciMapPaletteClient.init();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
			ClientCommandManager.literal("cci_map_palette")
				.then(ClientCommandManager.literal("debug")
					.executes(context -> {
						boolean enabled = CciMapPaletteClient.toggleDebug();
						context.getSource().sendFeedback(CciMapPaletteClient.debugFeedback(enabled));
						return Command.SINGLE_SUCCESS;
					})
					.then(ClientCommandManager.literal("on").executes(context -> {
						context.getSource().sendFeedback(CciMapPaletteClient.debugFeedback(CciMapPaletteClient.setDebugEnabled(true)));
						return Command.SINGLE_SUCCESS;
					}))
					.then(ClientCommandManager.literal("off").executes(context -> {
						context.getSource().sendFeedback(CciMapPaletteClient.debugFeedback(CciMapPaletteClient.setDebugEnabled(false)));
						return Command.SINGLE_SUCCESS;
					}))
				)
		));
	}
}
