package io.github.skydynamic.quickbakcupmulti.neoforge;

import io.github.skydynamic.quickbakcupmulti.ModContainer;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import lombok.Getter;
import lombok.Setter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(value = QuickbakcupmultiReforged.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class QuickbakcupmultiReforgedNeoForge {
    @Getter
    private static final ModContainer modContainer = new ModContainer();
    @Setter @Getter
    private static String[] boostArgs = null;

    public QuickbakcupmultiReforgedNeoForge() {
        modContainer.setConfigPath(FMLPaths.CONFIGDIR.get());

        QuickbakcupmultiReforged.init(modContainer);
    }
}
