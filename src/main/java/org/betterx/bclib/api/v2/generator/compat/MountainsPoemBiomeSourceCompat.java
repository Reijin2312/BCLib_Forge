package org.betterx.bclib.api.v2.generator.compat;

import org.betterx.bclib.BCLib;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MountainsPoemBiomeSourceCompat {
    private MountainsPoemBiomeSourceCompat() {
    }

    public static List<Holder<Biome>> prepare(ExternalBiomeSourceContext context) {
        if (!LevelStem.NETHER.equals(context.dimensionKey()) || !(context.biomeSource() instanceof MultiNoiseBiomeSource)) {
            return List.of();
        }

        List<Holder<Biome>> addedBiomes = new ArrayList<>();
        try {
            Class.forName("net.mcreator.mountainspoem.init.MountainsPoemModBiomes");

            Climate.ParameterList<Holder<Biome>> parameterList = getTypedParameterList(context);
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters = new ArrayList<>(parameterList.values());

            addPair(
                    context,
                    parameters,
                    addedBiomes,
                    "ancient_battlefields",
                    Climate.Parameter.span(0.4999F, 1.0F),
                    Climate.Parameter.span(0.1F, 0.3F),
                    Climate.Parameter.span(0.3F, 0.6F),
                    Climate.Parameter.span(-0.5F, 0.5F),
                    Climate.Parameter.span(-0.1F, 0.6F)
            );
            addPair(
                    context,
                    parameters,
                    addedBiomes,
                    "under_rock_hill",
                    Climate.Parameter.span(-0.5F, 0.5F),
                    Climate.Parameter.span(0.8F, 1.0F),
                    Climate.Parameter.span(0.7F, 1.1F),
                    Climate.Parameter.span(0.1F, 0.5002F),
                    Climate.Parameter.span(-1.0F, 1.0F)
            );

            if (!addedBiomes.isEmpty()) {
                context.setBiomeSource(
                        MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(parameters)),
                        true
                );
            }

            context.log(
                    "Applied Mountain's Poem direct MultiNoise compatibility for {}: added={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.summarizeBiomeKeys(addedBiomes, 8)
            );
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to apply Mountain's Poem biome compatibility for {}", context.ownerName(), e);
        }

        return List.copyOf(addedBiomes);
    }

    private static void addPair(
            ExternalBiomeSourceContext context,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters,
            List<Holder<Biome>> addedBiomes,
            String biomePath,
            Climate.Parameter temperature,
            Climate.Parameter humidity,
            Climate.Parameter continentalness,
            Climate.Parameter erosion,
            Climate.Parameter weirdness
    ) {
        ResourceKey<Biome> key = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation("mountains_poem", biomePath)
        );
        Optional<Holder.Reference<Biome>> holder = context.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key);
        if (holder.isEmpty()) {
            return;
        }

        Holder<Biome> biome = holder.get();
        addParameter(parameters, biome, temperature, humidity, continentalness, erosion, 0.0F, weirdness);
        addParameter(parameters, biome, temperature, humidity, continentalness, erosion, 1.0F, weirdness);
        if (addedBiomes.stream().noneMatch(existing -> existing.is(key))) {
            addedBiomes.add(biome);
        }
    }

    private static void addParameter(
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters,
            Holder<Biome> biome,
            Climate.Parameter temperature,
            Climate.Parameter humidity,
            Climate.Parameter continentalness,
            Climate.Parameter erosion,
            float depth,
            Climate.Parameter weirdness
    ) {
        Pair<Climate.ParameterPoint, Holder<Biome>> pair = Pair.of(
                Climate.parameters(
                        temperature,
                        humidity,
                        continentalness,
                        erosion,
                        Climate.Parameter.point(depth),
                        weirdness,
                        0.0F
                ),
                biome
        );
        if (!parameters.contains(pair)) {
            parameters.add(pair);
        }
    }

    @SuppressWarnings("unchecked")
    private static Climate.ParameterList<Holder<Biome>> getTypedParameterList(ExternalBiomeSourceContext context)
            throws ReflectiveOperationException {
        return (Climate.ParameterList<Holder<Biome>>) context.invokeNoArgMethod(
                context.biomeSource(),
                "net.minecraft.world.level.biome.Climate$ParameterList",
                "parameters",
                "m_274409_",
                "m_207840_"
        );
    }
}
