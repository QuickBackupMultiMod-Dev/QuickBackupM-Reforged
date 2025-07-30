package io.github.skydynamic.quickbakcupmulti.schedule.runnables;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbakcupmulti.QuickbakcupmultiReforged;
import io.github.skydynamic.quickbakcupmulti.config.PbsConfig;
import io.github.skydynamic.quickbakcupmulti.config.PruneScheduleConfig;
import io.github.skydynamic.quickbakcupmulti.utils.BackupManager;
import io.github.skydynamic.quickbakcupmulti.utils.DurationUtils;
import net.minecraft.commands.CommandSourceStack;

import java.time.ZoneId;
import java.util.*;

public class DefaultPruneRunnable implements Runnable {
    private final PruneScheduleConfig config;
    private final PruneRunnable executor;

    public final static DefaultPruneRunnable PRUNE_REGULAR_BACKUP_RUNNABLE = new DefaultPruneRunnable(
        QuickbakcupmultiReforged.getModConfig().getPruneScheduleConfig(),
        DefaultPruneRunnable::defaultPruneRegularBackup
    );

    public final static DefaultPruneRunnable PRUNE_TEMPORARY_BACKUP_RUNNABLE = new DefaultPruneRunnable(
        QuickbakcupmultiReforged.getModConfig().getPruneScheduleConfig(),
        DefaultPruneRunnable::defaultPruneTemporaryBackup
    );

    public DefaultPruneRunnable(PruneScheduleConfig config, PruneRunnable executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public void run() {
        CommandSourceStack commandSourceStack = QuickbakcupmultiReforged.getServerManager().getCommandSource();

        executor.execute(config, commandSourceStack);
    }

    private static void defaultPruneRegularBackup(PruneScheduleConfig config, CommandSourceStack commandSourceStack) {
        List<StorageInfo> backupList = QuickbakcupmultiReforged.getDatabase().getAllStorageInfo();
        List<StorageInfo> toDelete = filterBackupWithPbs(config.getRegularBackup(), backupList, config.getTimezoneOverride());
        toDelete.forEach(backup -> BackupManager.deleteBackup(commandSourceStack, backup.getName()));

        QuickbakcupmultiReforged.logger.info("Prune backup: {}", toDelete.size());
    }

    private static void defaultPruneTemporaryBackup(PruneScheduleConfig config, CommandSourceStack commandSourceStack) {
        QuickbakcupmultiReforged.getManager().deleteTempStorage();

        QuickbakcupmultiReforged.logger.info("Prune temporary backup success");
    }

    private static List<StorageInfo> filterBackupWithPbs(PbsConfig pbsConfig, List<StorageInfo> backupList, String timezoneOverride) {
        if (pbsConfig == null || !pbsConfig.isEnabled() || backupList == null || backupList.isEmpty()) {
            return new ArrayList<>();
        }

        ZoneId zoneId = timezoneOverride != null ? ZoneId.of(timezoneOverride) : ZoneId.systemDefault();

        List<StorageInfo> filteredList = new ArrayList<>(backupList);
        filteredList.sort(Comparator.comparingLong(StorageInfo::getTimestamp));

        List<StorageInfo> toDelete = new ArrayList<>();

        Map<String, Integer> timeUnits = Map.of(
            "hour", pbsConfig.getHour(),
            "day", pbsConfig.getDay(),
            "week", pbsConfig.getWeek(),
            "month", pbsConfig.getMonth(),
            "year", pbsConfig.getYear()
        );

        for (Map.Entry<String, Integer> entry : timeUnits.entrySet()) {
            String unit = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) continue;

            Map<String, StorageInfo> latestPerUnit = new HashMap<>();
            for (StorageInfo backup : filteredList) {
                String key = DurationUtils.formatByUnit(backup.getTimestamp(), unit, zoneId);
                if (!latestPerUnit.containsKey(key)) {
                    latestPerUnit.put(key, backup);
                } else if (backup.getTimestamp() > latestPerUnit.get(key).getTimestamp()) {
                    toDelete.add(latestPerUnit.get(key));
                    latestPerUnit.put(key, backup);
                } else {
                    toDelete.add(backup);
                }
            }
            filteredList.removeAll(latestPerUnit.values());
        }

        if (pbsConfig.getLast() > 0 && filteredList.size() > pbsConfig.getLast()) {
            int keepCount = pbsConfig.getLast();
            List<StorageInfo> toKeep = filteredList.subList(filteredList.size() - keepCount, filteredList.size());
            Set<StorageInfo> toKeepSet = new HashSet<>(toKeep);
            toDelete.addAll(filteredList.stream().filter(b -> !toKeepSet.contains(b)).toList());
        }

        if (pbsConfig.getMaxLifeTime() != null && !pbsConfig.getMaxLifeTime().equals("0s")) {
            long maxLifeTimeMillis = DurationUtils.parseDurationToSeconds(pbsConfig.getMaxLifeTime()) * 1000;
            long now = System.currentTimeMillis();
            List<StorageInfo> expired = filteredList.stream()
                    .filter(b -> now - b.getTimestamp() > maxLifeTimeMillis)
                    .toList();
            toDelete.addAll(expired);
            filteredList.removeAll(expired);
        }

        if (pbsConfig.getMaxAmount() > 0 && filteredList.size() > pbsConfig.getMaxAmount()) {
            int removeCount = filteredList.size() - pbsConfig.getMaxAmount();
            toDelete.addAll(filteredList.subList(0, removeCount));
        }

        return toDelete;
    }

    @FunctionalInterface
    public interface PruneRunnable {
        void execute(PruneScheduleConfig config, CommandSourceStack commandSourceStack);
    }
}
