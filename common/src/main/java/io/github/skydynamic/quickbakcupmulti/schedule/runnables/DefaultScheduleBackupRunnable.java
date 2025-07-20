package io.github.skydynamic.quickbakcupmulti.schedule.runnables;

import io.github.skydynamic.quickbakcupmulti.DatabaseCache;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.ScheduleBackupConfig;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DefaultScheduleBackupRunnable implements Runnable {
    public static final Class<?> CARPET_PLAYER_CLASS;

    @Override
    public void run() {
        ScheduleBackupConfig scheduleBackupConfig = QuickbakcupmultiReforged.getModConfig().getScheduleBackupConfig();
        List<ServerPlayer> players = new ArrayList<>(QuickbakcupmultiReforged.getServerManager().getPlayers());

        if (scheduleBackupConfig.isRequireOnlinePlayers()) {
            if (scheduleBackupConfig.isRequireOnlinePlayersIgnoreCarpetFakePlayer()) {
                players.removeIf(DefaultScheduleBackupRunnable::isCarpetPlayer);
            }

            if (!scheduleBackupConfig.getRequireOnlinePlayersBlacklist().isEmpty()) {
                players = filterBlacklistedPlayers(players, scheduleBackupConfig.getRequireOnlinePlayersBlacklist());
            }

            if (players.isEmpty()) {
                QuickbakcupmultiReforged.logger.warn("No online player meets the requirements, skip schedule backup");
                return;
            }
        }

        String scheduleName = "ScheduleBackup-" + QuickbakcupmultiReforged.formatTimestamp(System.currentTimeMillis());

        if (QuickbakcupmultiReforged.getDatabase().storageExists(scheduleName)) {
            QuickbakcupmultiReforged.logger.warn("Schedule backup name already exists: {}", scheduleName);
            return;
        }

        BackupManager.makeBackup(QuickbakcupmultiReforged.getServerManager().getCommandSource(), scheduleName, "");

        if (QuickbakcupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }

        QuickbakcupmultiReforged.logger.info("Schedule backup complete: {}", scheduleName);
    }

    private List<ServerPlayer> filterBlacklistedPlayers(List<ServerPlayer> players, List<String> blacklist) {
        List<Pattern> patterns = new ArrayList<>();
        for (String patternStr : blacklist) {
            try {
                patterns.add(Pattern.compile(patternStr));
            } catch (PatternSyntaxException e) {
                QuickbakcupmultiReforged.logger.warn("Invalid pattern: {}, skip filter", patternStr);
            }
        }

        return players.stream()
            .filter(player -> patterns.stream().noneMatch(p -> p.matcher(player.getName().getString()).matches()))
            .toList();
    }


    private static boolean isCarpetPlayer(ServerPlayer player) {
        return CARPET_PLAYER_CLASS != null && CARPET_PLAYER_CLASS.isInstance(player);
    }

    static {
        Class<?> clazz;
        try {
            clazz = Class.forName("carpet.patches.EntityPlayerMPFake");
        } catch (ClassNotFoundException e) {
            clazz = null;
        }
        CARPET_PLAYER_CLASS = clazz;
    }
}
