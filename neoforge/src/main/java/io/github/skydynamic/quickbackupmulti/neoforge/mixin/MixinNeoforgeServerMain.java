package io.github.skydynamic.quickbackupmulti.neoforge.mixin;

import io.github.skydynamic.quickbackupmulti.neoforge.QuickbackupmultiReforgedNeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.server.Main.class)
public class MixinNeoforgeServerMain {
    @Inject(
        method = "main",
        at = @At("HEAD")
    )
    private static void injectServerMain(String[] strings, CallbackInfo ci) {
        QuickbackupmultiReforgedNeoForge.setBoostArgs(strings);
    }
}
