package io.github.skydynamic.quickbackupmulti.fabric;

import io.github.skydynamic.quickbackupmulti.ModEnvType;
import io.github.skydynamic.quickbackupmulti.ModVersion;
import io.github.skydynamic.quickbackupmulti.fabric.events.FabricEvents;
import io.github.skydynamic.quickbackupmulti.ModContainer;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
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

        FabricLoader.getInstance().getModContainer(QuickbackupmultiReforged.MOD_ID).ifPresent(modContainer1 ->
            modContainer.setModVersion(new ModVersion(modContainer1.getMetadata().getVersion().getFriendlyString())));

        QuickbackupmultiReforged.init(modContainer);
        FabricEvents.register();
    }
}
