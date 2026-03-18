package com.spinyowl.ccimap.internal.client;

import com.spinyowl.ccimap.api.client.RuntimeBlockColorHandle;
import com.spinyowl.ccimap.api.client.RuntimeBlockColorResolver;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeBlockColorRegistryImpl {
    private static final ConcurrentHashMap<ResourceLocation, List<RuleEntry>> RULES = new ConcurrentHashMap<>();
    private static final AtomicLong ORDER = new AtomicLong();
    private static final Comparator<RuleEntry> ENTRY_ORDER = Comparator
        .comparingInt((RuleEntry entry) -> entry.priority)
        .reversed()
        .thenComparingLong((RuleEntry entry) -> entry.order)
        .reversed();

    private RuntimeBlockColorRegistryImpl() {
    }

    public static RuntimeBlockColorHandle register(ResourceLocation blockId, int priority, RuntimeBlockColorResolver resolver) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(resolver, "resolver");

        RuleEntry entry = new RuleEntry(blockId, priority, ORDER.incrementAndGet(), resolver);
        RULES.compute(blockId, (ignored, existing) -> appendEntry(existing, entry));
        invalidateAll();
        return new Handle(entry);
    }

    public static boolean hasRules(ResourceLocation blockId) {
        List<RuleEntry> entries = RULES.get(blockId);
        return entries != null && !entries.isEmpty();
    }

    @Nullable
    public static Color4I resolve(BlockAndTintGetter world, BlockPos pos, BlockState state, ResourceLocation blockId) {
        List<RuleEntry> entries = RULES.get(blockId);
        if (entries == null || entries.isEmpty()) {
            return null;
        }

        for (RuleEntry entry : entries) {
            Color4I color = entry.resolver.resolve(world, pos, state, blockId);
            if (color != null) {
                return color.withAlpha(255);
            }
        }

        return null;
    }

    public static void invalidateAll() {
        MapManager.getInstance().ifPresent(manager -> manager.updateAllRegions(false));
    }

    private static List<RuleEntry> appendEntry(@Nullable List<RuleEntry> existing, RuleEntry entry) {
        ArrayList<RuleEntry> updated = new ArrayList<>(existing == null ? 1 : existing.size() + 1);
        if (existing != null) {
            updated.addAll(existing);
        }
        updated.add(entry);
        updated.sort(ENTRY_ORDER);
        return List.copyOf(updated);
    }

    private static void unregister(RuleEntry entry) {
        RULES.computeIfPresent(entry.blockId, (ignored, existing) -> {
            ArrayList<RuleEntry> updated = new ArrayList<>(existing);
            updated.remove(entry);
            return updated.isEmpty() ? null : List.copyOf(updated);
        });
        invalidateAll();
    }

    private static final class Handle implements RuntimeBlockColorHandle {
        private final RuleEntry entry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Handle(RuleEntry entry) {
            this.entry = entry;
        }

        @Override
        public void unregister() {
            if (active.compareAndSet(true, false)) {
                RuntimeBlockColorRegistryImpl.unregister(entry);
            }
        }
    }

    private static final class RuleEntry {
        private final ResourceLocation blockId;
        private final int priority;
        private final long order;
        private final RuntimeBlockColorResolver resolver;

        private RuleEntry(ResourceLocation blockId, int priority, long order, RuntimeBlockColorResolver resolver) {
            this.blockId = blockId;
            this.priority = priority;
            this.order = order;
            this.resolver = resolver;
        }
    }
}
