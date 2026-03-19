package com.spinyowl.ccimap.internal.client;

import com.spinyowl.ccimap.api.client.RuntimeFluidColorHandle;
import com.spinyowl.ccimap.api.client.RuntimeFluidColorResolver;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeFluidColorRegistryImpl {
    private static final ConcurrentHashMap<ResourceLocation, List<RuleEntry>> RULES = new ConcurrentHashMap<>();
    private static final AtomicLong ORDER = new AtomicLong();
    private static final Comparator<RuleEntry> ENTRY_ORDER = Comparator
        .comparingInt((RuleEntry entry) -> entry.priority)
        .reversed()
        .thenComparingLong((RuleEntry entry) -> entry.order)
        .reversed();

    private RuntimeFluidColorRegistryImpl() {
    }

    public static RuntimeFluidColorHandle register(ResourceLocation fluidId, int priority, RuntimeFluidColorResolver resolver) {
        Objects.requireNonNull(fluidId, "fluidId");
        Objects.requireNonNull(resolver, "resolver");

        RuleEntry entry = new RuleEntry(fluidId, priority, ORDER.incrementAndGet(), resolver);
        RULES.compute(fluidId, (ignored, existing) -> appendEntry(existing, entry));
        invalidateAll();
        return new Handle(entry);
    }

    public static boolean hasRules(ResourceLocation fluidId) {
        List<RuleEntry> entries = RULES.get(fluidId);
        return (entries != null && !entries.isEmpty()) || AutoFluidColorResolver.hasResolver(fluidId);
    }

    @Nullable
    public static Color4I resolve(BlockAndTintGetter world, BlockPos pos, FluidState fluidState, ResourceLocation fluidId) {
        List<RuleEntry> entries = RULES.get(fluidId);
        if (entries != null && !entries.isEmpty()) {
            for (RuleEntry entry : entries) {
                Color4I color = entry.resolver.resolve(world, pos, fluidState, fluidId);
                if (color != null) {
                    return color.withAlpha(255);
                }
            }
        }

        return AutoFluidColorResolver.resolve(world, pos, fluidState, fluidId);
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
        RULES.computeIfPresent(entry.fluidId, (ignored, existing) -> {
            ArrayList<RuleEntry> updated = new ArrayList<>(existing);
            updated.remove(entry);
            return updated.isEmpty() ? null : List.copyOf(updated);
        });
        invalidateAll();
    }

    private static final class Handle implements RuntimeFluidColorHandle {
        private final RuleEntry entry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Handle(RuleEntry entry) {
            this.entry = entry;
        }

        @Override
        public void unregister() {
            if (active.compareAndSet(true, false)) {
                RuntimeFluidColorRegistryImpl.unregister(entry);
            }
        }
    }

    private static final class RuleEntry {
        private final ResourceLocation fluidId;
        private final int priority;
        private final long order;
        private final RuntimeFluidColorResolver resolver;

        private RuleEntry(ResourceLocation fluidId, int priority, long order, RuntimeFluidColorResolver resolver) {
            this.fluidId = fluidId;
            this.priority = priority;
            this.order = order;
            this.resolver = resolver;
        }
    }
}
