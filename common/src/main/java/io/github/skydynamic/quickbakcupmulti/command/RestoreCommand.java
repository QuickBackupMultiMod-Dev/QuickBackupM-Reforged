package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionType;
import lombok.Getter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RestoreCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> restoreCmd = Commands.literal("restore")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(it ->
                restoreBackup(it.getSource(), StringArgumentType.getString(it, "name"))
            )
        );

    public static final LiteralArgumentBuilder<CommandSourceStack> confirmCmd = Commands.literal("confirm")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
        .executes(it -> {
            try {
                executeRestore(it.getSource());
            } catch (Exception e) {
                ModCommand.getLogger().error("Restore failed", e);
            }
            return 0;
        });

    public static final LiteralArgumentBuilder<CommandSourceStack> cancelCmd = Commands.literal("cancel")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
                    .executes(it -> cancelRestore(it.getSource()));

    private static class RestoreThread extends TimerTask {
        private final Runnable executor;

        public RestoreThread(Runnable executor) {
            this.executor = executor;
        }

        @Override
        public void run() {
            ModCommand.getLogger().info("Restore thread started...");
            executor.run();
            ModCommand.getLogger().info("Restore thread close");
        }
    }

    @Getter
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> restoreDataMap = new ConcurrentHashMap<>();

    private static int restoreBackup(CommandSourceStack commandSource, String name) {
        if (!QuickbakcupmultiReforged.getStorager().storageExists(name)) {
            commandSource.sendSystemMessage(Component.nullToEmpty("Restore backup failed"));
            return 0;
        }
        ConcurrentHashMap<String, Object> restoreMap = new ConcurrentHashMap<>();
        restoreMap.put("Slot", name);
        restoreMap.put("Timer", new Timer());
        restoreMap.put("Countdown", Executors.newSingleThreadScheduledExecutor());
        synchronized (restoreDataMap) {
            restoreDataMap.put("QBM", restoreMap);
            commandSource.sendSystemMessage(Component.nullToEmpty("Type /qb confirm to execute restore"));
            return 1;
        }
    }

    private static void executeRestore(CommandSourceStack commandSource) {
        synchronized (restoreDataMap) {
            if (restoreDataMap.containsKey("QBM")) {
                if (!QuickbakcupmultiReforged.getStorager().storageExists(restoreDataMap.get("QBM").get("Slot").toString())) {
                    commandSource.sendSystemMessage(Component.nullToEmpty("Restore backup failed"));
                    restoreDataMap.clear();
                    return;
                }
                String executePlayerName;
                if (commandSource.getPlayer() != null) {
                    executePlayerName = commandSource.getPlayer().getGameProfile().getName();
                } else {
                    executePlayerName = "Console";
                }
                commandSource.sendSystemMessage(Component.nullToEmpty("Confirmed restore, If you want to abort, please enter §7/qb cancel§r"));
                MinecraftServer server = commandSource.getServer();
                List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
                for (ServerPlayer player : players) {
                    player.sendSystemMessage(Component.nullToEmpty("%s execute restore backup, §cRestore§r after 10 second".formatted(executePlayerName)));
                }
                String slot = (String) restoreDataMap.get("QBM").get("Slot");
                QuickbakcupmultiReforged.getModContainer().setCurrentSelectionBackup(slot);
                Timer timer = (Timer) restoreDataMap.get("QBM").get("Timer");
                ScheduledExecutorService countdown = (ScheduledExecutorService) restoreDataMap.get("QBM").get("Countdown");
                AtomicInteger countDown = new AtomicInteger(11);
                countdown.scheduleAtFixedRate(() -> {
                    int remaining = countDown.decrementAndGet();
                    if (remaining >= 1) {
                        for (ServerPlayer player : players) {
                            MutableComponent content = Component.literal("%s second later the world will be §crestored§r to slot §6%s§r, ".formatted(remaining, slot))
                                .append(Component.literal("Click to ABORT restore!")
                                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/qb cancel"))));
                            player.sendSystemMessage(content, false);
                            ModCommand.getLogger().info(content.getString());
                        }
                    } else {
                        countdown.shutdown();
                    }
                }, 0, 1, TimeUnit.SECONDS);
                timer.schedule(new RestoreThread(() -> {
                    restoreDataMap.clear();
                    for (ServerPlayer player : players) {
                        player.connection.disconnect(Component.literal("Server restore backup"));
                    }
                    QuickbakcupmultiReforged.getModContainer().setRestoringBackup(true);
                    QuickbakcupmultiReforged.getServerManager().stopServer();
                }), 10000);
            } else {
                commandSource.sendSystemMessage(Component.nullToEmpty("Nothing to confirm"));
            }
        }
    }

    private static int cancelRestore(CommandSourceStack commandSource) {
        if (restoreDataMap.containsKey("QBM")) {
            synchronized (restoreDataMap) {
                Timer timer = (Timer) restoreDataMap.get("QBM").get("Timer");
                ScheduledExecutorService countdown = (ScheduledExecutorService) restoreDataMap.get("QBM").get("Countdown");
                timer.cancel();
                countdown.shutdown();
                restoreDataMap.clear();
                QuickbakcupmultiReforged.getModContainer().setRestoringBackup(false);
                commandSource.sendSystemMessage(Component.nullToEmpty("Restore canceled"));
            }
        } else {
            commandSource.sendSystemMessage(Component.nullToEmpty("Nothing to cancel"));
        }
        return 1;
    }
}
