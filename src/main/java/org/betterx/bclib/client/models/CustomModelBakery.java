package org.betterx.bclib.client.models;

import org.betterx.bclib.BCLib;
import org.betterx.bclib.interfaces.BlockModelProvider;
import org.betterx.bclib.interfaces.ItemModelProvider;
import org.betterx.bclib.models.RecordItemModelProvider;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Map;

public class CustomModelBakery {
    private final Map<ResourceLocation, UnbakedModel> models = Maps.newConcurrentMap();

    public UnbakedModel getBlockModel(ResourceLocation location) {
        return models.get(location);
    }

    public UnbakedModel getItemModel(ResourceLocation location) {
        return models.get(location);
    }

    public void loadCustomModels(ResourceManager resourceManager) {
        BuiltInRegistries.BLOCK.stream()
                               .filter(block -> block instanceof BlockModelProvider)
                               .forEach(block -> {
                                   ResourceLocation blockID = BuiltInRegistries.BLOCK.getKey(block);
                                   if (blockID == null) {
                                       BCLib.LOGGER.warning("Skip runtime block model: missing registry key for {}", block);
                                       return;
                                   }
                                   try {
                                       ResourceLocation blockStateStorage = new ResourceLocation(
                                               blockID.getNamespace(),
                                               "blockstates/" + blockID.getPath() + ".json"
                                       );
                                       ResourceLocation blockModelStorage = new ResourceLocation(
                                               blockID.getNamespace(),
                                               "models/block/" + blockID.getPath() + ".json"
                                       );
                                       if (resourceManager.getResource(blockStateStorage).isEmpty()
                                               || resourceManager.getResource(blockModelStorage).isEmpty()) {
                                           addBlockModel(blockID, block);
                                       }
                                       ResourceLocation storageID = new ResourceLocation(
                                               blockID.getNamespace(),
                                               "models/item/" + blockID.getPath() + ".json"
                                       );
                                       if (resourceManager.getResource(storageID).isEmpty()) {
                                           addItemModel(blockID, (ItemModelProvider) block);
                                       }
                                   } catch (RuntimeException ex) {
                                       BCLib.LOGGER.error("Failed to build runtime block model for {}", blockID, ex);
                                   }
                               });

        BuiltInRegistries.ITEM.stream()
                              .filter(item -> item instanceof ItemModelProvider || RecordItemModelProvider.has(item))
                              .forEach(item -> {
                                  ResourceLocation registryID = BuiltInRegistries.ITEM.getKey(item);
                                  if (registryID == null) {
                                      BCLib.LOGGER.warning("Skip runtime item model: missing registry key for {}", item);
                                      return;
                                  }
                                  ResourceLocation storageID = new ResourceLocation(
                                          registryID.getNamespace(),
                                          "models/item/" + registryID.getPath() + ".json"
                                  );
                                  final ItemModelProvider provider = (item instanceof ItemModelProvider)
                                          ? (ItemModelProvider) item
                                          : RecordItemModelProvider.get(item);

                                  if (provider == null) {
                                      BCLib.LOGGER.warning("Skip runtime item model: missing provider for {}", registryID);
                                      return;
                                  }

                                  try {
                                      if (resourceManager.getResource(storageID).isEmpty()) {
                                          addItemModel(registryID, provider);
                                      }
                                  } catch (RuntimeException ex) {
                                      BCLib.LOGGER.error("Failed to build runtime item model for {}", registryID, ex);
                                  }
                                  });
    }

    private void addBlockModel(ResourceLocation blockID, Block block) {
        BlockModelProvider provider = (BlockModelProvider) block;
        ImmutableList<BlockState> states = block.getStateDefinition().getPossibleStates();
        BlockState defaultState = block.defaultBlockState();

        ResourceLocation defaultStateID = BlockModelShaper.stateToModelLocation(blockID, defaultState);
        UnbakedModel defaultModel = provider.getModelVariant(defaultStateID, defaultState, models);
        if (defaultModel == null) {
            BCLib.LOGGER.warning("Skip runtime block model: missing default model for {}", blockID);
            return;
        }

        if (defaultModel instanceof MultiPart) {
            states.forEach(blockState -> {
                ResourceLocation stateID = BlockModelShaper.stateToModelLocation(blockID, blockState);
                models.put(stateID, defaultModel);
            });
        } else {
            states.forEach(blockState -> {
                ResourceLocation stateID = BlockModelShaper.stateToModelLocation(blockID, blockState);
                UnbakedModel model = stateID.equals(defaultStateID)
                        ? defaultModel
                        : provider.getModelVariant(stateID, blockState, models);
                if (model == null) {
                    BCLib.LOGGER.warning("Skip runtime block model: missing model for {}", stateID);
                    model = defaultModel;
                }
                models.put(stateID, model);
            });
        }
    }

    private void addItemModel(ResourceLocation itemID, ItemModelProvider provider) {
        if (itemID == null) {
            BCLib.LOGGER.warning("Item model skipped because registry id is null.");
            return;
        }
        if (provider == null) {
            BCLib.LOGGER.warning("Item model skipped because provider is null: {}", itemID);
            return;
        }
        ModelResourceLocation modelLocation = new ModelResourceLocation(
                itemID.getNamespace(),
                itemID.getPath(),
                "inventory"
        );
        if (models.containsKey(modelLocation)) {
            return;
        }
        BlockModel model = provider.getItemModel(modelLocation);
        if (model == null) {
            BCLib.LOGGER.warning("Item model is null for {}", modelLocation);
            return;
        }
        models.put(modelLocation, model);
        models.put(new ResourceLocation(itemID.getNamespace(), "item/" + itemID.getPath()), model);
    }
}
