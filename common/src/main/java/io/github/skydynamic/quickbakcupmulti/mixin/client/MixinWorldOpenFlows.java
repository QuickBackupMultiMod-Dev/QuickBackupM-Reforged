package io.github.skydynamic.quickbakcupmulti.mixin.client;

import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class MixinWorldOpenFlows {
    @Final
    @Shadow
    private Minecraft minecraft;

    @Inject(
        method = "createLevelFromExistingSettings",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/repository/ServerPacksSource;createPackRepository(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;)Lnet/minecraft/server/packs/repository/PackRepository;"
        )
    )
    private void onCreateLevelFromExistingSettings$createPackRepository(
        LevelStorageSource.LevelStorageAccess levelStorageAccess, ReloadableServerResources reloadableServerResources,
        LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, WorldData worldData,
        CallbackInfo ci
    ) {
        String worldName = levelStorageAccess.getLevelId();

        QuickbakcupmultiReforged.getModContainer().setLevelId(worldName);
        QuickbakcupmultiReforged.setNewDataBase(worldName);
        QuickbakcupmultiReforged.getModContainer().setCurrentSavePath(this.minecraft.getLevelSource().getLevelPath(worldName));
        if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }
    }
}
