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

public class NetherExorcismRebornBiomeSourceCompat {
    public static List<Holder<Biome>> prepare(ExternalBiomeSourceContext context) {
        if (!LevelStem.NETHER.equals(context.dimensionKey()) || !(context.biomeSource() instanceof MultiNoiseBiomeSource)) {
            return List.of();
        }

        final List<Holder<Biome>> addedBiomes = new ArrayList<>();
        try {
            Class.forName("net.mcreator.nethersexorcismreborn.init.NethersExorcismRebornModBiomes");

            final Climate.ParameterList<Holder<Biome>> parameterList = getTypedParameterList(context);
            final List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters = new ArrayList<>(parameterList.values());

            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "dna_forest",
                    Climate.parameters(
                            Climate.Parameter.span(0.1F, 0.2F),
                            Climate.Parameter.span(0.1F, 0.3F),
                            Climate.Parameter.span(0.1F, 0.7F),
                            Climate.Parameter.span(0.1F, 0.3F),
                            Climate.Parameter.point(0.0F),
                            Climate.Parameter.span(0.0F, 0.0001F),
                            0.0F
                    )
            );
            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "dna_forest",
                    Climate.parameters(
                            Climate.Parameter.span(0.1F, 0.2F),
                            Climate.Parameter.span(0.1F, 0.3F),
                            Climate.Parameter.span(0.1F, 0.7F),
                            Climate.Parameter.span(0.1F, 0.3F),
                            Climate.Parameter.point(1.0F),
                            Climate.Parameter.span(0.0F, 0.0001F),
                            0.0F
                    )
            );
            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "biohazardous_delta",
                    Climate.parameters(
                            Climate.Parameter.span(0.6F, 1.5F),
                            Climate.Parameter.span(0.0F, 0.0001F),
                            Climate.Parameter.span(0.0F, 0.0004F),
                            Climate.Parameter.span(0.0F, 0.4F),
                            Climate.Parameter.point(0.0F),
                            Climate.Parameter.span(0.0F, 0.0004F),
                            0.0F
                    )
            );
            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "biohazardous_delta",
                    Climate.parameters(
                            Climate.Parameter.span(0.6F, 1.5F),
                            Climate.Parameter.span(0.0F, 0.0001F),
                            Climate.Parameter.span(0.0F, 0.0004F),
                            Climate.Parameter.span(0.0F, 0.4F),
                            Climate.Parameter.point(1.0F),
                            Climate.Parameter.span(0.0F, 0.0004F),
                            0.0F
                    )
            );
            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "turquoise_plains",
                    Climate.parameters(
                            Climate.Parameter.span(0.2F, 0.5F),
                            Climate.Parameter.span(0.1F, 0.9F),
                            Climate.Parameter.span(0.1F, 1.2F),
                            Climate.Parameter.span(0.2F, 0.2001F),
                            Climate.Parameter.point(0.0F),
                            Climate.Parameter.span(0.0F, 0.0002F),
                            0.0F
                    )
            );
            addParameter(
                    context,
                    parameters,
                    addedBiomes,
                    "turquoise_plains",
                    Climate.parameters(
                            Climate.Parameter.span(0.2F, 0.5F),
                            Climate.Parameter.span(0.1F, 0.9F),
                            Climate.Parameter.span(0.1F, 1.2F),
                            Climate.Parameter.span(0.2F, 0.2001F),
                            Climate.Parameter.point(1.0F),
                            Climate.Parameter.span(0.0F, 0.0002F),
                            0.0F
                    )
            );

            if (!addedBiomes.isEmpty()) {
                final MultiNoiseBiomeSource patchedSource = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(parameters));
                context.setBiomeSource(patchedSource, true);
            }

            context.log(
                    "Applied Nether Exorcism Reborn direct MultiNoise compatibility for {}: added={}",
                    context.ownerName(),
                    ExternalBiomeSourceContext.summarizeBiomeKeys(addedBiomes, 16)
            );
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException e) {
            BCLib.LOGGER.warning("Unable to apply Nether Exorcism Reborn biome compatibility for {}", context.ownerName(), e);
        }

        return List.copyOf(addedBiomes);
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

    private static void addParameter(
            ExternalBiomeSourceContext context,
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> parameters,
            List<Holder<Biome>> addedBiomes,
            String biomePath,
            Climate.ParameterPoint parameterPoint
    ) {
        final ResourceKey<Biome> key = ResourceKey.create(
                Registries.BIOME,
                new ResourceLocation("nethers_exorcism_reborn", biomePath)
        );
        final Optional<Holder.Reference<Biome>> holder = context.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key);
        if (holder.isEmpty()) {
            return;
        }

        final Holder<Biome> biomeHolder = holder.get();
        final Pair<Climate.ParameterPoint, Holder<Biome>> pair = Pair.of(parameterPoint, biomeHolder);
        if (!parameters.contains(pair)) {
            parameters.add(pair);
        }
        if (addedBiomes.stream().noneMatch(existing -> existing.is(key))) {
            addedBiomes.add(biomeHolder);
        }
    }
}
