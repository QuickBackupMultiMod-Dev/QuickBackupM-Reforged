package io.github.skydynamic.quickbakcupmulti.fabric;

import io.github.skydynamic.quickbakcupmulti.fabric.events.FabricEvents;
import io.github.skydynamic.quickbakcupmulti.ModContainer;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import lombok.Getter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class QuickbakcupmultiReforgedFabric implements DedicatedServerModInitializer {
    @Getter
    private static final ModContainer modContainer = new ModContainer();

    @Override
    public void onInitializeServer() {
        modContainer.setConfigPath(FabricLoader.getInstance().getConfigDir());

        QuickbakcupmultiReforged.init(modContainer);
        FabricEvents.register();
    }
}
