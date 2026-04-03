package org.betterx.bclib.client;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.api.v2.ModIntegrationAPI;
import org.betterx.bclib.api.v2.PostInitAPI;
import org.betterx.bclib.api.v2.dataexchange.DataExchangeAPI;
import org.betterx.bclib.client.models.CustomModelBakery;
import org.betterx.bclib.client.textures.AtlasSetManager;
import org.betterx.bclib.client.textures.SpriteLister;
import org.betterx.bclib.config.Configs;
import org.betterx.bclib.interfaces.CustomColorProvider;
import org.betterx.bclib.registry.BaseBlockEntityRenders;
import org.betterx.worlds.together.WorldsTogether;
import org.betterx.worlds.together.client.WorldsTogetherClient;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BCLib.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BCLibClient {
    private static CustomModelBakery modelBakery;

    public static CustomModelBakery lazyModelbakery() {
        if (modelBakery == null) {
            modelBakery = new CustomModelBakery();
        }
        return modelBakery;
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        modelBakery = lazyModelbakery();
        event.enqueueWork(() -> {
            WorldsTogetherClient.onInitializeClient();
            ModIntegrationAPI.registerAll();
            BaseBlockEntityRenders.register();
            DataExchangeAPI.prepareClientside();
            PostInitAPI.postInit(true);

            WorldsTogether.SURPRESS_EXPERIMENTAL_DIALOG = Configs.CLIENT_CONFIG.suppressExperimentalDialog();

            AtlasSetManager.addSource(AtlasSetManager.VANILLA_BLOCKS, new SpriteLister("entity/chest"));
            AtlasSetManager.addSource(AtlasSetManager.VANILLA_BLOCKS, new SpriteLister("blocks"));
        });
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof CustomColorProvider provider) {
                event.register(provider.getProvider(), block);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof CustomColorProvider provider) {
                Item item = block.asItem();
                if (item != Items.AIR) {
                    event.register(provider.getItemProvider(), item);
                }
            }
        }
    }


}
