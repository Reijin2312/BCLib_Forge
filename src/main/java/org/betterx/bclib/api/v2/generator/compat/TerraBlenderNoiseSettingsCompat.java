package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.generator.BCLibNetherBiomeSource;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import java.lang.reflect.Method;

public class TerraBlenderNoiseSettingsCompat {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void applyRegionType(
            Holder<NoiseGeneratorSettings> holder,
            ResourceKey<LevelStem> dimensionKey,
            BiomeSource biomeSource
    ) {
        if (!holder.isBound()) {
            return;
        }

        String regionTypeName = null;
        if (dimensionKey != null) {
            if (dimensionKey.equals(LevelStem.NETHER)) {
                regionTypeName = "NETHER";
            } else if (dimensionKey.equals(LevelStem.OVERWORLD)) {
                regionTypeName = "OVERWORLD";
            }
        }

        if (regionTypeName == null && biomeSource instanceof BCLibNetherBiomeSource) {
            regionTypeName = "NETHER";
        }

        if (regionTypeName == null) {
            return;
        }

        try {
            final Class<?> ruleCategoryClass = Class.forName("terrablender.api.SurfaceRuleManager$RuleCategory");
            final Enum ruleCategory = Enum.valueOf((Class<Enum>) ruleCategoryClass.asSubclass(Enum.class), regionTypeName);
            final Method setRuleCategory = holder.value().getClass().getMethod("setRuleCategory", ruleCategoryClass);
            setRuleCategory.invoke(holder.value(), ruleCategory);
        } catch (ReflectiveOperationException primaryError) {
            try {
                final Class<?> regionTypeClass = Class.forName("terrablender.api.RegionType");
                final Enum regionType = Enum.valueOf((Class<Enum>) regionTypeClass.asSubclass(Enum.class), regionTypeName);
                final Method setRegionType = holder.value().getClass().getMethod("setRegionType", regionTypeClass);
                setRegionType.invoke(holder.value(), regionType);
            } catch (ReflectiveOperationException e) {
                BCLib.LOGGER.warning(
                        "Unable to set TerraBlender surface rule category {} for {}",
                        regionTypeName,
                        biomeSource.getClass().getName(),
                        e
                );
            }
        }
    }
}
