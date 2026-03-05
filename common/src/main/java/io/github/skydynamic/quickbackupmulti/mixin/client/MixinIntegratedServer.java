package io.github.skydynamic.quickbackupmulti.mixin.client;

import com.mojang.datafixers.DataFixer;
import io.github.skydynamic.quickbackupmulti.event.OnLoadedWorldHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;

@Mixin(net.minecraft.client.server.IntegratedServer.class)
public abstract class MixinIntegratedServer extends MinecraftServer {
    public MixinIntegratedServer(
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
            target = "Lnet/minecraft/client/server/IntegratedServer;loadLevel()V",
            shift = At.Shift.AFTER
        )
    )
    private void onLoadLevel(CallbackInfoReturnable<Boolean> cir) {
        OnLoadedWorldHandler.handler();
    }
}
