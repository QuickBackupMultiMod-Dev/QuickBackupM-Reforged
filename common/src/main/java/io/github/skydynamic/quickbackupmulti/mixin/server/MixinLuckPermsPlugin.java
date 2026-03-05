package io.github.skydynamic.quickbackupmulti.mixin.server;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin", remap = false)
public class MixinLuckPermsPlugin {
    @Dynamic
    @Inject(
        method = "enable",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onEnableInject(
        CallbackInfo ci
    ) {
        if (QuickbackupmultiReforged.getModContainer().isAfterRestarting()) {
            QuickbackupmultiReforged.logger.warn("QuickBackupMulti prevented LuckPerms from enabling during backup restoration.");
            QuickbackupmultiReforged.logger.warn("This is an experimental feature. Should you encounter any problems, we kindly ask you to report them as soon as possible.");
            ci.cancel();
        }
    }
}
