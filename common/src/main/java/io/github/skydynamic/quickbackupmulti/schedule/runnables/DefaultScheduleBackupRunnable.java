package io.github.skydynamic.quickbackupmulti.schedule.runnables;

import io.github.skydynamic.quickbackupmulti.DatabaseCache;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.config.ScheduleBackupConfig;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class DefaultScheduleBackupRunnable implements Runnable {
    public static final Class<?> CARPET_PLAYER_CLASS;

    @Override
    public void run() {
        ScheduleBackupConfig scheduleBackupConfig = QuickbackupmultiReforged.getModConfig().getScheduleBackupConfig();
        List<ServerPlayer> players = new ArrayList<>(QuickbackupmultiReforged.getServerManager().getPlayers());

        if (scheduleBackupConfig.isRequireOnlinePlayers()) {
            if (scheduleBackupConfig.isRequireOnlinePlayersIgnoreCarpetFakePlayer()) {
                players.removeIf(DefaultScheduleBackupRunnable::isCarpetPlayer);
            }

            if (!scheduleBackupConfig.getRequireOnlinePlayersBlacklist().isEmpty()) {
                players = filterBlacklistedPlayers(players, scheduleBackupConfig.getRequireOnlinePlayersBlacklist());
            }

            if (players.isEmpty()) {
                QuickbackupmultiReforged.logger.warn("No online player meets the requirements, skip schedule backup");
                return;
            }
        }

        String scheduleName = "ScheduleBackup-" + QuickbackupmultiReforged
            .formatTimestamp(System.currentTimeMillis())
            .replace(" ", "-");

        if (QuickbackupmultiReforged.getDatabase().storageExists(scheduleName)) {
            QuickbackupmultiReforged.logger.warn("Schedule backup name already exists: {}", scheduleName);
            return;
        }

        BackupManager.makeBackup(QuickbackupmultiReforged.getServerManager().getCommandSource(), scheduleName, "");

        if (QuickbackupmultiReforged.getModConfig().isCacheDatabase()) {
            DatabaseCache.updateStorageInfoCaches();
        }

        QuickbackupmultiReforged.logger.info("Schedule backup complete: {}", scheduleName);
    }

    private List<ServerPlayer> filterBlacklistedPlayers(List<ServerPlayer> players, List<String> blacklist) {
        List<Pattern> patterns = new ArrayList<>();
        for (String patternStr : blacklist) {
            try {
                patterns.add(Pattern.compile(patternStr));
            } catch (PatternSyntaxException e) {
                QuickbackupmultiReforged.logger.warn("Invalid pattern: {}, skip filter", patternStr);
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
