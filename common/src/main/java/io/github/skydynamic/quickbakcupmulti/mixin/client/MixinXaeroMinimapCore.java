package io.github.skydynamic.quickbakcupmulti.mixin.client;

import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "xaero.common.core.XaeroMinimapCore",
    remap = false
)
public class MixinXaeroMinimapCore {
    @Dynamic
    @Inject(
        method = "onDeleteWorld",
        at =@At("HEAD"),
        cancellable = true
    )
    private static void onDeleteWorldInject(
        LevelStorageSource.LevelStorageAccess levelStorageAccess,
        CallbackInfo ci
    ) {
        if (QuickbakcupmultiReforged.getModContainer().isRestoringBackup()) {
            ci.cancel();
        }
    }
}
