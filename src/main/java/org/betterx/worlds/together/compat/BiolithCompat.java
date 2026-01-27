package org.betterx.worlds.together.compat;

import org.betterx.worlds.together.WorldsTogether;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class BiolithCompat {
    private static final String BIOLITH_COORDINATOR = "com.terraformersmc.biolith.impl.biome.BiomeCoordinator";
    private static boolean warned;

    private BiolithCompat() {
    }

    public static void ensureRegistryManager(LayeredRegistryAccess<RegistryLayer> registries) {
        if (!FabricLoader.getInstance().isModLoaded("biolith")) return;

        try {
            Class<?> coordinator = Class.forName(BIOLITH_COORDINATOR);
            Method getRegistryManager = coordinator.getMethod("getRegistryManager");
            Object current = getRegistryManager.invoke(null);
            if (current != null) return;

            Method setRegistryManager = findSetRegistryManager(coordinator);
            if (setRegistryManager != null) {
                setRegistryManager.invoke(null, registries);
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Exception ex = (t instanceof Exception) ? (Exception) t : new Exception(t);
                WorldsTogether.LOGGER.error("Biolith compat: failed to prime registry manager", ex);
            }
        }
    }

    private static Method findSetRegistryManager(Class<?> coordinator) {
        for (Method method : coordinator.getMethods()) {
            if (method.getName().equals("setRegistryManager") && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }
}
