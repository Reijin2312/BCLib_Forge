package org.betterx.bclib;

import org.betterx.bclib.api.v2.datafixer.DataFixerAPI;
import org.betterx.bclib.api.v2.datafixer.ForcedLevelPatch;
import org.betterx.bclib.api.v2.datafixer.MigrationProfile;
import org.betterx.bclib.api.v2.datafixer.Patch;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Map;

public final class BCLibPatch {
    public static void register() {
        DataFixerAPI.registerPatch(SignPatch::new);
        DataFixerAPI.registerPatch(LegacyWorldGenPatch::new);
    }
}

class SignPatch extends Patch {
    public SignPatch() {
        super(BCLib.MOD_ID, "3.0.11");
    }

    @Override
    public Map<String, String> getIDReplacements() {
        return Map.ofEntries(
                Map.entry("bclib:sign", "minecraft:sign")
        );
    }
}

class LegacyWorldGenPatch extends ForcedLevelPatch {
    private static final String LEGACY_CHUNK_GENERATOR = "bclib:betterx";
    private static final String LEGACY_NETHER_BIOME_SOURCE = "bclib:nether_biome_source";
    private static final String LEGACY_END_BIOME_SOURCE = "bclib:end_biome_source";
    private static final String LEGACY_AMPLIFIED_NETHER_SETTINGS = "bclib:amplified_nether";

    private static final String VANILLA_NOISE_CHUNK_GENERATOR = "minecraft:noise";
    private static final String VANILLA_NETHER_SETTINGS = "minecraft:nether";
    private static final String VANILLA_NETHER_BIOME_SOURCE = "minecraft:multi_noise";
    private static final String VANILLA_END_BIOME_SOURCE = "minecraft:the_end";
    private static final String VANILLA_NETHER_PRESET = "minecraft:nether";

    LegacyWorldGenPatch() {
        super(BCLib.MOD_ID, "3.0.12");
    }

    @Override
    protected Boolean runLevelDatPatch(CompoundTag root, MigrationProfile profile) {
        if (!root.contains("Data", Tag.TAG_COMPOUND)) {
            return false;
        }
        final CompoundTag data = root.getCompound("Data");
        if (!data.contains("WorldGenSettings", Tag.TAG_COMPOUND)) {
            return false;
        }
        final CompoundTag worldGenSettings = data.getCompound("WorldGenSettings");
        if (!worldGenSettings.contains("dimensions", Tag.TAG_COMPOUND)) {
            return false;
        }

        final CompoundTag dimensions = worldGenSettings.getCompound("dimensions");
        boolean changed = false;
        for (String key : dimensions.getAllKeys()) {
            if (!dimensions.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            final CompoundTag levelStem = dimensions.getCompound(key);
            if (patchGenerator(levelStem)) {
                dimensions.put(key, levelStem);
                changed = true;
            }
        }

        if (changed) {
            worldGenSettings.put("dimensions", dimensions);
            data.put("WorldGenSettings", worldGenSettings);
            root.put("Data", data);
        }
        return changed;
    }

    private static boolean patchGenerator(CompoundTag levelStem) {
        if (!levelStem.contains("generator", Tag.TAG_COMPOUND)) {
            return false;
        }

        boolean changed = false;
        final CompoundTag generator = levelStem.getCompound("generator");

        if (generator.contains("type", Tag.TAG_STRING)
                && LEGACY_CHUNK_GENERATOR.equals(generator.getString("type"))) {
            generator.putString("type", VANILLA_NOISE_CHUNK_GENERATOR);
            changed = true;
        }

        if (generator.contains("settings", Tag.TAG_STRING)
                && LEGACY_AMPLIFIED_NETHER_SETTINGS.equals(generator.getString("settings"))) {
            generator.putString("settings", VANILLA_NETHER_SETTINGS);
            changed = true;
        }

        if (generator.contains("biome_source", Tag.TAG_COMPOUND)) {
            final CompoundTag biomeSource = generator.getCompound("biome_source");
            if (biomeSource.contains("type", Tag.TAG_STRING)) {
                final String biomeSourceType = biomeSource.getString("type");
                if (LEGACY_NETHER_BIOME_SOURCE.equals(biomeSourceType)) {
                    generator.put("biome_source", makeVanillaNetherBiomeSource());
                    changed = true;
                } else if (LEGACY_END_BIOME_SOURCE.equals(biomeSourceType)) {
                    generator.put("biome_source", makeVanillaEndBiomeSource());
                    changed = true;
                }
            }
        }

        if (changed) {
            levelStem.put("generator", generator);
        }
        return changed;
    }

    private static CompoundTag makeVanillaNetherBiomeSource() {
        final CompoundTag biomeSource = new CompoundTag();
        biomeSource.putString("type", VANILLA_NETHER_BIOME_SOURCE);
        biomeSource.putString("preset", VANILLA_NETHER_PRESET);
        return biomeSource;
    }

    private static CompoundTag makeVanillaEndBiomeSource() {
        final CompoundTag biomeSource = new CompoundTag();
        biomeSource.putString("type", VANILLA_END_BIOME_SOURCE);
        return biomeSource;
    }
}
