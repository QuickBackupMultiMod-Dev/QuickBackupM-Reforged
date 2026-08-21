package io.github.skydynamic.quickbackupmulti.mixin.client;

import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

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
        LevelStorageSource.LevelStorageAccess levelSourceAccess,
        ReloadableServerResources serverResources,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        LevelDataAndDimensions.WorldDataAndGenSettings worldDataAndGenSettings,
        Optional<GameRules> gameRules,
        CallbackInfo ci
    ) {
        String worldName = levelSourceAccess.getLevelId();

        QuickbackupmultiReforged.getModContainer().setLevelId(worldName);
        QuickbackupmultiReforged.setNewDataBase(worldName);
        QuickbackupmultiReforged.getModContainer().setCurrentSavePath(this.minecraft.getLevelSource().getLevelPath(worldName));
        if (QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }
    }
}
