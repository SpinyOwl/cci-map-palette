package com.spinyowl.ccimap;

import dev.architectury.event.events.client.ClientTickEvent;
import com.spinyowl.ccimap.internal.client.CciMapPaletteDebugOverlay;
import com.spinyowl.ccimap.internal.client.AutoBlockColorResolver;
import com.spinyowl.ccimap.internal.client.AutoBlockColorConfig;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.map.ChunkUpdateTask;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

public final class CciMapPaletteClient {
    private static boolean initialized;

    private CciMapPaletteClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        initialized = true;
        AutoBlockColorResolver.init();
        ClientTickEvent.CLIENT_PRE.register(minecraft -> AutoBlockColorResolver.tick());
        CciMapPaletteDebugOverlay.init();
    }

    /**
     * Toggles the client-side debug overlay state.
     */
    public static boolean toggleDebug() {
        return CciMapPaletteDebugOverlay.toggle();
    }

    /**
     * Sets the client-side debug overlay state explicitly.
     */
    public static boolean setDebugEnabled(boolean enabled) {
        CciMapPaletteDebugOverlay.setEnabled(enabled);
        return enabled;
    }

    /**
     * Returns whether the client-side debug overlay is enabled.
     */
    public static boolean isDebugEnabled() {
        return CciMapPaletteDebugOverlay.isEnabled();
    }

    /**
     * Builds a standard feedback message for the debug command.
     */
    public static Component debugFeedback(boolean enabled) {
        return CciMapPaletteDebugOverlay.feedback(enabled);
    }

    public static Component listAutoOverrides() {
        return AutoBlockColorConfig.list();
    }

    public static Component listUnmappedMods() {
        return AutoBlockColorConfig.listUnmappedMods();
    }

    public static Component addAutoOverride(String input) {
        return AutoBlockColorConfig.add(input);
    }

    public static Component removeAutoOverride(String input) {
        return AutoBlockColorConfig.remove(input);
    }

    /**
     * Invalidates map data around the local player and schedules chunk rescans.
     */
    public static Component invalidateMapRadius(int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return Component.literal("Map invalidation requires an active client world.").withStyle(ChatFormatting.RED);
        }

        MapManager manager = MapManager.getInstance().orElse(null);
        if (manager == null) {
            return Component.literal("FTB Chunks map manager is not available.").withStyle(ChatFormatting.RED);
        }

        int clampedRadius = Math.max(0, Math.min(32, radius));
        ChunkPos center = minecraft.player.chunkPosition();
        int scheduled = 0;

        for (int chunkZ = center.z - clampedRadius; chunkZ <= center.z + clampedRadius; chunkZ++) {
            for (int chunkX = center.x - clampedRadius; chunkX <= center.x + clampedRadius; chunkX++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                ChunkAccess chunkAccess = minecraft.level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunkAccess == null) {
                    continue;
                }

                FTBChunksClient.INSTANCE.queueOrExecute(new ChunkUpdateTask(manager, minecraft.level, chunkAccess, chunkPos));
                scheduled++;
            }
        }

        if (scheduled == 0) {
            return Component.literal("No loaded chunks found within " + clampedRadius + " chunk(s).").withStyle(ChatFormatting.YELLOW);
        }

        return Component.literal("Scheduled map invalidation for " + scheduled + " loaded chunk(s) within radius " + clampedRadius + ".")
            .withStyle(ChatFormatting.GREEN);
    }
}
