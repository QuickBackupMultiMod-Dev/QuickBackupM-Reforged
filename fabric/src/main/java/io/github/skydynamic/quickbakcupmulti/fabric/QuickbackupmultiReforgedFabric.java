package io.github.skydynamic.quickbakcupmulti.fabric;

import io.github.skydynamic.quickbakcupmulti.ModEnvType;
import io.github.skydynamic.quickbakcupmulti.ModVersion;
import io.github.skydynamic.quickbakcupmulti.fabric.events.FabricEvents;
import io.github.skydynamic.quickbakcupmulti.ModContainer;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import lombok.Getter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class QuickbackupmultiReforgedFabric implements ModInitializer {
    @Getter
    private static final ModContainer modContainer = new ModContainer();

    @Override
    public void onInitialize() {
        modContainer.setConfigPath(FabricLoader.getInstance().getConfigDir());

        modContainer.setEnvType(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? ModEnvType.CLIENT : ModEnvType.SERVER);

        FabricLoader.getInstance().getModContainer(QuickbakcupmultiReforged.MOD_ID).ifPresent(modContainer1 ->
            modContainer.setModVersion(new ModVersion(modContainer1.getMetadata().getVersion().getFriendlyString())));

        QuickbakcupmultiReforged.init(modContainer);
        FabricEvents.register();
    }
}
