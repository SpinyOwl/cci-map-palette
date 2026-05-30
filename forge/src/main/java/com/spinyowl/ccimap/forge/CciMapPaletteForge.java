package com.spinyowl.ccimap.forge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.spinyowl.ccimap.CciMapPaletteCommon;
import com.spinyowl.ccimap.CciMapPaletteClient;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

@Mod(CciMapPaletteCommon.MOD_ID)
public final class CciMapPaletteForge {
	public CciMapPaletteForge() {
		CciMapPaletteCommon.init();
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> CciMapPaletteClient::init);
		DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.addListener(CciMapPaletteForge::registerClientCommands));
	}

	private static void registerClientCommands(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("cci_map_palette")
				.then(Commands.literal("debug")
					.executes(context -> {
						boolean enabled = CciMapPaletteClient.toggleDebug();
						context.getSource().sendSuccess(() -> CciMapPaletteClient.debugFeedback(enabled), false);
						return Command.SINGLE_SUCCESS;
					})
					.then(Commands.literal("on").executes(context -> {
						context.getSource().sendSuccess(() -> CciMapPaletteClient.debugFeedback(CciMapPaletteClient.setDebugEnabled(true)), false);
						return Command.SINGLE_SUCCESS;
					}))
					.then(Commands.literal("off").executes(context -> {
						context.getSource().sendSuccess(() -> CciMapPaletteClient.debugFeedback(CciMapPaletteClient.setDebugEnabled(false)), false);
						return Command.SINGLE_SUCCESS;
					}))
				)
				.then(Commands.literal("auto_override")
					.executes(context -> {
						context.getSource().sendSuccess(() -> CciMapPaletteClient.listAutoOverrides(), false);
						return Command.SINGLE_SUCCESS;
					})
					.then(Commands.literal("list")
						.executes(context -> {
							context.getSource().sendSuccess(() -> CciMapPaletteClient.listAutoOverrides(), false);
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(Commands.literal("unmapped")
						.executes(context -> {
							context.getSource().sendSuccess(() -> CciMapPaletteClient.listUnmappedMods(), false);
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(Commands.literal("list_unmapped")
						.executes(context -> {
							context.getSource().sendSuccess(() -> CciMapPaletteClient.listUnmappedMods(), false);
							return Command.SINGLE_SUCCESS;
						})
					)
					.then(Commands.literal("add")
						.then(Commands.argument("target", StringArgumentType.greedyString())
							.executes(context -> {
								context.getSource().sendSuccess(() -> CciMapPaletteClient.addAutoOverride(StringArgumentType.getString(context, "target")), false);
								return Command.SINGLE_SUCCESS;
							})
						)
					)
					.then(Commands.literal("remove")
						.then(Commands.argument("target", StringArgumentType.greedyString())
							.executes(context -> {
								context.getSource().sendSuccess(() -> CciMapPaletteClient.removeAutoOverride(StringArgumentType.getString(context, "target")), false);
								return Command.SINGLE_SUCCESS;
							})
						)
					)
				)
				.then(Commands.literal("invalidate_map")
					.then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
						.executes(context -> {
							context.getSource().sendSuccess(() -> CciMapPaletteClient.invalidateMapRadius(IntegerArgumentType.getInteger(context, "radius")), false);
							return Command.SINGLE_SUCCESS;
						})
					)
				)
		);
	}
}
