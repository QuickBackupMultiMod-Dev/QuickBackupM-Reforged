package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.restore.RestoreTimer;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.skydynamic.quickbakcupmulti.translate.Translate.tr;

public class RestoreCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> restoreCmd = Commands.literal("restore")
        .requires(it -> PermissionManager.hasPermission(it, 4, PermissionType.ADMIN))
        .then(Commands.argument("target", StringArgumentType.string())
            .suggests(((context, builder) -> {
                List<StorageInfo> backups = BackupManager.getSortedBackups();
                String remaining = builder.getRemaining();
                int index = 1;
                for (StorageInfo info : backups) {
                    String name = info.getName();
                    if (name.contains(remaining)) {
                        builder.suggest(name);
                    }
                    String idx = String.valueOf(index++);
                    if (idx.startsWith(remaining)) {
                        builder.suggest(idx);
                    }
                }
                return builder.buildFuture();
            }))
            .executes(it ->
                restoreBackup(it.getSource(), StringArgumentType.getString(it, "target"))
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

    @Getter
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> restoreDataMap = new ConcurrentHashMap<>();

    private static int restoreBackup(CommandSourceStack commandSource, String target) {
        String name = resolveBackupName(target);
        if (name == null || !QuickbakcupmultiReforged.getDatabase().storageExists(name)) {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.fail")));
            return 0;
        }
        ConcurrentHashMap<String, Object> restoreMap = new ConcurrentHashMap<>();
        restoreMap.put("Slot", name);
        restoreMap.put("Timer", new Timer());
        restoreMap.put("Countdown", Executors.newSingleThreadScheduledExecutor());
        synchronized (restoreDataMap) {
            restoreDataMap.put("QBM", restoreMap);
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.confirm_hint")));
        }
        return 1;
    }

    private static void executeRestore(CommandSourceStack commandSource) {
        synchronized (restoreDataMap) {
            if (restoreDataMap.containsKey("QBM")) {
                if (!QuickbakcupmultiReforged.getDatabase().storageExists(restoreDataMap.get("QBM").get("Slot").toString())) {
                    commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.fail")));
                    restoreDataMap.clear();
                    return;
                }
                String executePlayerName;
                if (commandSource.getPlayer() != null) {
                    executePlayerName = commandSource.getPlayer().getGameProfile().getName();
                } else {
                    executePlayerName = "Console";
                }
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.abort_hint")));
                MinecraftServer server = commandSource.getServer();
                List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
                for (ServerPlayer player : players) {
                    player.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.countdown.intro", executePlayerName)));
                }
                String slot = (String) restoreDataMap.get("QBM").get("Slot");
                QuickbakcupmultiReforged.getModContainer().setCurrentSelectionBackup(slot);
                Timer timer = (Timer) restoreDataMap.get("QBM").get("Timer");
                ScheduledExecutorService countdown = (ScheduledExecutorService) restoreDataMap.get("QBM").get("Countdown");
                AtomicInteger countDown = new AtomicInteger(11);
                countdown.scheduleAtFixedRate(() -> {
                    int remaining = countDown.decrementAndGet();
                    if (remaining >= 1) {
                        MutableComponent content = Component.literal(tr("quickbackupmulti.restore.countdown.text", remaining, slot))
                            .append(Component.literal(tr("quickbackupmulti.restore.countdown.hover"))
                                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/qb cancel"))));
                        for (ServerPlayer player : players) {
                            player.sendSystemMessage(content, false);
                        }
                        ModCommand.getLogger().info(content.getString());
                    } else {
                        countdown.shutdown();
                    }
                }, 0, 1, TimeUnit.SECONDS);
                timer.schedule(new RestoreTimer(QuickbakcupmultiReforged.getModContainer().getEnvType(), players), 10000);
            } else {
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.confirm_restore.nothing_to_confirm")));
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
                commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.restore.abort")));
            }
        } else {
            commandSource.sendSystemMessage(Component.nullToEmpty(tr("quickbackupmulti.confirm_restore.nothing_to_confirm")));
        }
        return 1;
    }

    private static String resolveBackupName(String target) {
        String trimmed = target.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                int index = Integer.parseInt(trimmed);
                StorageInfo info = BackupManager.getBackupByIndex(index);
                if (info != null) {
                    return info.getName();
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return trimmed;
    }
}
