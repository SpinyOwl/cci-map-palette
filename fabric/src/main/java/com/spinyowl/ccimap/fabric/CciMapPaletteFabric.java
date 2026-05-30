package com.spinyowl.ccimap.fabric;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spinyowl.ccimap.CciMapPaletteCommon;
import com.spinyowl.ccimap.CciMapPaletteClient;
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
				.then(ClientCommandManager.literal("auto_override")
					.executes(context -> {
						context.getSource().sendFeedback(CciMapPaletteClient.listAutoOverrides());
						return Command.SINGLE_SUCCESS;
					})
					.then(ClientCommandManager.literal("list")
						.executes(context -> {
							context.getSource().sendFeedback(CciMapPaletteClient.listAutoOverrides());
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(ClientCommandManager.literal("unmapped")
						.executes(context -> {
							context.getSource().sendFeedback(CciMapPaletteClient.listUnmappedMods());
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(ClientCommandManager.literal("list_unmapped")
						.executes(context -> {
							context.getSource().sendFeedback(CciMapPaletteClient.listUnmappedMods());
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("target", StringArgumentType.greedyString())
							.executes(context -> {
								context.getSource().sendFeedback(CciMapPaletteClient.addAutoOverride(StringArgumentType.getString(context, "target")));
								return Command.SINGLE_SUCCESS;
							})
						)
					)
					.then(ClientCommandManager.literal("remove")
						.then(ClientCommandManager.argument("target", StringArgumentType.greedyString())
							.executes(context -> {
								context.getSource().sendFeedback(CciMapPaletteClient.removeAutoOverride(StringArgumentType.getString(context, "target")));
								return Command.SINGLE_SUCCESS;
							})
						)
					)
				)
				.then(ClientCommandManager.literal("invalidate_map")
					.then(ClientCommandManager.argument("radius", IntegerArgumentType.integer(0, 32))
						.executes(context -> {
							context.getSource().sendFeedback(CciMapPaletteClient.invalidateMapRadius(IntegerArgumentType.getInteger(context, "radius")));
							return Command.SINGLE_SUCCESS;
						})
					)
				)
		));
	}
}
