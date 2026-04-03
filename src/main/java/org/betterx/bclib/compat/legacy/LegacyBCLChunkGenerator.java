package org.betterx.bclib.compat.legacy;

import org.betterx.bclib.BCLib;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Compatibility-only codec bridge for legacy worlds that still reference bclib:betterx.
 * New worlds should not use this generator.
 */
@Mod.EventBusSubscriber(modid = BCLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class LegacyBCLChunkGenerator extends NoiseBasedChunkGenerator {
    public static final Codec<LegacyBCLChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(LegacyBCLChunkGenerator::generatorSettings)
            )
            .apply(instance, instance.stable(LegacyBCLChunkGenerator::new))
    );

    public LegacyBCLChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.CHUNK_GENERATOR)) {
            event.register(Registries.CHUNK_GENERATOR, helper -> helper.register(BCLib.makeID("betterx"), CODEC));
        }
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "BCLib - Legacy Chunk Generator (" + Integer.toHexString(hashCode()) + ")";
    }
}
