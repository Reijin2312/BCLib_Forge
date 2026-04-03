package org.betterx.datagen.bclib;

import org.betterx.bclib.BCLib;
import org.betterx.datagen.bclib.advancement.BCLAdvancementDataProvider;
import org.betterx.datagen.bclib.advancement.RecipeDataProvider;
import org.betterx.datagen.bclib.integrations.NullscapeBiomes;
import org.betterx.datagen.bclib.tests.TestBiomes;
import org.betterx.datagen.bclib.tests.TestWorldgenProvider;
import org.betterx.datagen.bclib.worldgen.BiomeDatagenProvider;
import org.betterx.datagen.bclib.worldgen.BlockTagProvider;
import org.betterx.datagen.bclib.worldgen.ItemTagProvider;
import org.betterx.worlds.together.WorldsTogether;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = BCLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BCLibDatagen {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> registriesFuture = event.getLookupProvider();

        RegistrySetBuilder registryBuilder = new RegistrySetBuilder();
        BCLRegistrySupplier.INSTANCE.bootstrapRegistries(registryBuilder);

        if (event.includeServer()) {
            DatapackBuiltinEntriesProvider entriesProvider = new DatapackBuiltinEntriesProvider(
                    output,
                    registriesFuture,
                    registryBuilder,
                    Set.of(BCLib.MOD_ID, WorldsTogether.MOD_ID)
            );
            generator.addProvider(true, entriesProvider);
            registriesFuture = entriesProvider.getRegistryProvider();

            BCLib.LOGGER.info("Bootstrap gatherData");
            NullscapeBiomes.ensureStaticallyLoaded();
            if (BCLib.ADD_TEST_DATA) {
                TestBiomes.ensureStaticallyLoaded();

                generator.addProvider(true, new TestWorldgenProvider(output, registriesFuture));
                generator.addProvider(true, new TestBiomes(output, registriesFuture, existingFileHelper));
                RecipeDataProvider.createTestRecipes();
            } else {
                generator.addProvider(true, new BiomeDatagenProvider(output, registriesFuture, existingFileHelper));
            }

            generator.addProvider(true, new BlockTagProvider(output, registriesFuture, existingFileHelper));
            generator.addProvider(true, new ItemTagProvider(output, registriesFuture, existingFileHelper));
            generator.addProvider(true, new RecipeDataProvider(output));
            generator.addProvider(
                    true,
                    new ForgeAdvancementProvider(
                            output,
                            registriesFuture,
                            existingFileHelper,
                            List.of(new BCLAdvancementDataProvider())
                    )
            );
        }
    }
}
