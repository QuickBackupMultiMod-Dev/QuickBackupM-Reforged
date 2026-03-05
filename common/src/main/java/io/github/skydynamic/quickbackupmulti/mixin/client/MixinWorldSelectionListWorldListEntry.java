package io.github.skydynamic.quickbackupmulti.mixin.client;

import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSelectionList.WorldListEntry.class)
public class MixinWorldSelectionListWorldListEntry {
    @Shadow
    @Final
    private LevelSummary summary;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
        method = "doDeleteWorld",
        at = @At("RETURN")
    )
    private void onDeleteWorld(CallbackInfo ci) {
        String worldName = this.summary.getLevelId();
        BackupManager.deleteWorld(worldName);
    }

    @Inject(
        method = "joinWorld",
        at = @At("RETURN")
    )
    private void onJoinWorld(CallbackInfo ci) {
        String worldName = this.summary.getLevelId();

        QuickbackupmultiReforged.getModContainer().setLevelId(worldName);
        QuickbackupmultiReforged.setNewDataBase(worldName);
        QuickbackupmultiReforged.getModContainer().setCurrentSavePath(this.minecraft.getLevelSource().getLevelPath(worldName));
        if (QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }
    }
}
