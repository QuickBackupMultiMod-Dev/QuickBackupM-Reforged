package io.github.skydynamic.quickbackupmulti.mixin.client;

import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points the mod at the world a client is about to load.
 *
 * <p>{@code doWorldLoad} is the one place every client world load goes through, which is why the hook
 * lives here rather than on the screens that start a load. There are four entry points and they do
 * <em>not</em> share a screen-level choke point:
 * <ul>
 *   <li>{@code CreateWorldScreen} calls {@code WorldOpenFlows.createLevelFromExistingSettings};</li>
 *   <li>{@code WorldSelectionList.WorldListEntry.joinWorld} calls {@code WorldOpenFlows.openWorld};</li>
 *   <li>Quick Play — the launcher's "resume this world" and {@code --quickPlaySingleplayer} — calls
 *       {@code WorldOpenFlows.openWorld} directly, touching neither of the above;</li>
 *   <li>the title screen's demo world also calls {@code openWorld}.</li>
 * </ul>
 * Hooking the first two, as this mod used to, left Quick Play and the demo world with whatever database
 * happened to be open from before — so {@code /qb} either operated on the wrong world or failed
 * outright. {@code createLevelFromExistingSettings} and {@code openWorldDoLoad} both end in
 * {@code doWorldLoad}, so one hook here covers all four.
 *
 * <p>Injecting at {@code HEAD} also fixes the order this used to run in. The integrated server is
 * created inside {@code doWorldLoad}, and {@code MixinIntegratedServer} starts the schedules as soon as
 * it has loaded the level — which is <em>before</em> {@code joinWorld} returns. A scheduled backup that
 * fired in that window ran against the previous world's database. Now the database is in place before
 * the server exists.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void onDoWorldLoad(
        LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository,
        WorldStem worldStem, boolean newWorld, CallbackInfo ci
    ) {
        String worldName = levelStorageAccess.getLevelId();

        QuickbackupmultiReforged.getModContainer().setLevelId(worldName);
        QuickbackupmultiReforged.setNewDataBase(worldName);
        QuickbackupmultiReforged.getModContainer().setCurrentSavePath(
            ((Minecraft) (Object) this).getLevelSource().getLevelPath(worldName));
        if (QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }
    }
}
