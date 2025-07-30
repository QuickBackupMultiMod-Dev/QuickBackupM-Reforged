package io.github.skydynamic.quickbakcupmulti.neoforge;

import io.github.skydynamic.quickbakcupmulti.ModContainer;
import io.github.skydynamic.quickbakcupmulti.ModEnvType;
import io.github.skydynamic.quickbakcupmulti.ModVersion;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = QuickbakcupmultiReforged.MOD_ID)
public final class QuickbakcupmultiReforgedNeoForge {
    @Getter
    private static final ModContainer modContainer = new ModContainer();
    @Setter @Getter
    private static String[] boostArgs = null;

    public QuickbakcupmultiReforgedNeoForge() {
        modContainer.setConfigPath(FMLPaths.CONFIGDIR.get());
        modContainer.setEnvType(FMLLoader.getDist().isClient() ? ModEnvType.CLIENT : ModEnvType.SERVER);

        String version = FMLLoader.getLoadingModList().getModFileById(QuickbakcupmultiReforged.MOD_ID).getMods().getFirst().getVersion().toString();
        modContainer.setModVersion(new ModVersion(version));

        QuickbakcupmultiReforged.init(modContainer);
    }
}
