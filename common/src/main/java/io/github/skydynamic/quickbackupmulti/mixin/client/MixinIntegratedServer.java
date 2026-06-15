package io.github.skydynamic.quickbackupmulti.mixin.client;

import com.mojang.datafixers.DataFixer;
import io.github.skydynamic.quickbackupmulti.event.OnLoadedWorldHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.util.Optional;

@Mixin(net.minecraft.client.server.IntegratedServer.class)
public abstract class MixinIntegratedServer extends MinecraftServer {


    public MixinIntegratedServer(
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
            target = "Lnet/minecraft/client/server/IntegratedServer;loadLevel()V",
            shift = At.Shift.AFTER
        )
    )
    private void onLoadLevel(CallbackInfoReturnable<Boolean> cir) {
        OnLoadedWorldHandler.handler();
    }
}
