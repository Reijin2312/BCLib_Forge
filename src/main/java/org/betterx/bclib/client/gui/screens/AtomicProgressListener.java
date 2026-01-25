package org.betterx.bclib.client.gui.screens;

import net.minecraft.network.chat.Component;

public interface AtomicProgressListener {
    void incAtomic(int maxProgress);
    void resetAtomic();
    default void stop() {
    }

    default void progressStage(Component component) {
    }
}
