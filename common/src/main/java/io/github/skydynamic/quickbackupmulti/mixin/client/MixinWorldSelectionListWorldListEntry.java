package io.github.skydynamic.quickbackupmulti.mixin.client;

import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a world's backups when the world itself is deleted, so the blob directory and its database rows
 * do not outlive the save they belong to.
 *
 * <p>There is deliberately no {@code joinWorld} hook here any more: opening the database is
 * {@link MixinMinecraft}'s job, on the one path every world load shares. Doing it here covered only the
 * world-list button — not Quick Play — and ran after {@code MixinIntegratedServer} had already started
 * the schedules.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public class MixinWorldSelectionListWorldListEntry {
    @Shadow
    @Final
    LevelSummary summary;

    @Inject(
        method = "doDeleteWorld",
        at = @At("RETURN")
    )
    private void onDeleteWorld(CallbackInfo ci) {
        String worldName = this.summary.getLevelId();
        BackupManager.deleteWorld(worldName);
    }
}
