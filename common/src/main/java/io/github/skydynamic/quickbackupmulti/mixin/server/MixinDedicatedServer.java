package io.github.skydynamic.quickbackupmulti.mixin.server;

import com.mojang.datafixers.DataFixer;
import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.event.OnLoadedWorldHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.Optional;

@Mixin(DedicatedServer.class)
public abstract class MixinDedicatedServer extends MinecraftServer {


    public MixinDedicatedServer(
            Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource,
            PackRepository packRepository, WorldStem worldStem,
            Optional<GameRules> gameRules, Proxy proxy,
            DataFixer fixerUpper, Services services,
            LevelLoadListener levelLoadListener, boolean propagatesCrashes
    ) {
        super(serverThread, storageSource, packRepository, worldStem, gameRules, proxy, fixerUpper, services, levelLoadListener, propagatesCrashes);
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
        QuickbackupmultiReforged.setNewDataBase("server");
        QuickbackupmultiReforged.getModContainer().setCurrentSavePath(this.getWorldPath(LevelResource.ROOT));
        if (QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }

        OnLoadedWorldHandler.handler();
    }
}
