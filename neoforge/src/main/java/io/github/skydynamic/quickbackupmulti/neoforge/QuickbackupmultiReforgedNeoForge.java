package io.github.skydynamic.quickbackupmulti.neoforge;

import io.github.skydynamic.quickbackupmulti.ModContainer;
import io.github.skydynamic.quickbackupmulti.ModEnvType;
import io.github.skydynamic.quickbackupmulti.ModVersion;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = QuickbackupmultiReforged.MOD_ID)
public final class QuickbackupmultiReforgedNeoForge {
    @Getter
    private static final ModContainer modContainer = new ModContainer();
    @Setter @Getter
    private static String[] boostArgs = null;

    public QuickbackupmultiReforgedNeoForge() {
        modContainer.setConfigPath(FMLPaths.CONFIGDIR.get());
        FMLLoader instance = FMLLoader.getCurrentOrNull();
        if (instance != null) {
            modContainer.setEnvType(instance.getDist().isClient() ? ModEnvType.CLIENT : ModEnvType.SERVER);
            String version = instance.getLoadingModList().getModFileById(QuickbackupmultiReforged.MOD_ID).getMods().getFirst().getVersion().toString();
            modContainer.setModVersion(new ModVersion(version));
        } else {
            throw new IllegalStateException("There is no current FML Loader");
        }

        QuickbackupmultiReforged.init(modContainer);
    }
}
