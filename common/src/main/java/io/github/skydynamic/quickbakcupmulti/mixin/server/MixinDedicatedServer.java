package io.github.skydynamic.quickbakcupmulti.mixin.server;

import com.mojang.datafixers.DataFixer;
import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.event.OnLoadedWorldHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;

@Mixin(DedicatedServer.class)
public abstract class MixinDedicatedServer extends MinecraftServer {
    public MixinDedicatedServer(
        Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess,
        PackRepository packRepository, WorldStem worldStem,
        Proxy proxy, DataFixer dataFixer, Services services,
        LevelLoadListener levelLoadListener
    ) {
        super(thread, levelStorageAccess, packRepository, worldStem, proxy, dataFixer, services, levelLoadListener);
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
        QuickbakcupmultiReforged.setNewDataBase("server");
        QuickbakcupmultiReforged.getModContainer().setCurrentSavePath(this.getWorldPath(LevelResource.ROOT));
        if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }

        OnLoadedWorldHandler.handler();
    }
}
