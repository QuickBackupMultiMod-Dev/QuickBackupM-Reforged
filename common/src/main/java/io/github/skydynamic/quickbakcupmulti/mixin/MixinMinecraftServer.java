package io.github.skydynamic.quickbakcupmulti.mixin;

import com.mojang.datafixers.DataFixer;
import io.github.skydynamic.increment.storage.lib.database.Database;
import io.github.skydynamic.increment.storage.lib.util.IndexUtil;
import io.github.skydynamic.increment.storage.lib.util.Storager;
import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.database.DatabaseManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.UUID;

@Mixin(DedicatedServer.class)
public abstract class MixinMinecraftServer extends MinecraftServer {
    public MixinMinecraftServer(
        Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess,
        PackRepository packRepository, WorldStem worldStem,
        Proxy proxy, DataFixer dataFixer, Services services,
        ChunkProgressListenerFactory chunkProgressListenerFactory
    ) {
        super(thread, levelStorageAccess, packRepository, worldStem, proxy, dataFixer, services, chunkProgressListenerFactory);
    }

    @Inject(
        method = "initServer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/dedicated/DedicatedServer;loadLevel()V",
            shift = At.Shift.AFTER
        )
    )
    private void onLoadLevel(CallbackInfoReturnable<Boolean> cir) {
        DatabaseManager databaseManager = new DatabaseManager(
            "QuickBakcupMulti",
            QuickbakcupmultiReforged.getModConfig().getStoragePath(),
            UUID.nameUUIDFromBytes("server".getBytes())
        );
        QuickbakcupmultiReforged.getModContainer().setCurrentSavePath(this.getWorldPath(LevelResource.ROOT));
        QuickbakcupmultiReforged.setDatabase(new Database(databaseManager));
        QuickbakcupmultiReforged.setStorager(new Storager(QuickbakcupmultiReforged.getDatabase()));
        IndexUtil.setConfig(QuickbakcupmultiReforged.getModConfig());
        IndexUtil.setDatabase(QuickbakcupmultiReforged.getDatabase());
        if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }
    }
}
