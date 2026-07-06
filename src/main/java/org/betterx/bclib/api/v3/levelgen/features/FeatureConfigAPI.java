package org.betterx.bclib.api.v3.levelgen.features;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class FeatureConfigAPI {
    private static final Map<String, Predicate<ResourceLocation>> FEATURE_CONFIGS = new ConcurrentHashMap<>();
    private static final Map<PlacedFeature, ResourceLocation> KNOWN_PLACED_FEATURES = new ConcurrentHashMap<>();

    private FeatureConfigAPI() {
    }

    public static void register(String modId, Predicate<ResourceLocation> isEnabled) {
        FEATURE_CONFIGS.put(modId, isEnabled);
    }

    public static boolean isFeatureEnabled(BCLFeature<?, ?> feature) {
        if (feature == null) {
            return false;
        }

        Optional<ResourceKey<PlacedFeature>> key = feature.getPlacedFeature().unwrapKey();
        if (key.isEmpty()) {
            return true;
        }

        ResourceLocation id = key.get().location();
        KNOWN_PLACED_FEATURES.put(feature.getPlacedFeature().value(), id);
        Predicate<ResourceLocation> config = FEATURE_CONFIGS.get(id.getNamespace());
        return config == null || config.test(id);
    }

    public static void registerFeature(BCLFeature<?, ?> feature) {
        if (feature == null) {
            return;
        }

        feature.getPlacedFeature()
               .unwrapKey()
               .map(ResourceKey::location)
               .ifPresent(id -> KNOWN_PLACED_FEATURES.put(feature.getPlacedFeature().value(), id));
    }

    public static boolean isPlacedFeatureEnabled(PlacedFeature feature, RegistryAccess registryAccess) {
        if (feature == null || registryAccess == null) {
            return true;
        }

        Registry<PlacedFeature> registry;
        try {
            registry = registryAccess.registryOrThrow(Registries.PLACED_FEATURE);
        } catch (IllegalStateException ex) {
            return true;
        }

        Optional<ResourceKey<PlacedFeature>> key = registry.getResourceKey(feature);
        ResourceLocation id = key.map(ResourceKey::location).orElseGet(() -> KNOWN_PLACED_FEATURES.get(feature));
        if (id == null) return true;

        Predicate<ResourceLocation> config = FEATURE_CONFIGS.get(id.getNamespace());
        return config == null || config.test(id);
    }
}
