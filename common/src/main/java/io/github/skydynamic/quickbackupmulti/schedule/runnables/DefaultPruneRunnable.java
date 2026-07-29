package io.github.skydynamic.quickbackupmulti.schedule.runnables;

import io.github.skydynamic.increment.storage.lib.database.StorageInfo;
import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.config.PbsConfig;
import io.github.skydynamic.quickbackupmulti.config.PruneScheduleConfig;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import io.github.skydynamic.quickbackupmulti.utils.DurationUtils;
import net.minecraft.commands.CommandSourceStack;

import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

public class DefaultPruneRunnable implements Runnable {
    private final PruneScheduleConfig config;
    private final PruneRunnable executor;

    public final static DefaultPruneRunnable PRUNE_REGULAR_BACKUP_RUNNABLE = new DefaultPruneRunnable(
        QuickbackupmultiReforged.getModConfig().getPruneScheduleConfig(),
        DefaultPruneRunnable::defaultPruneRegularBackup
    );

    public final static DefaultPruneRunnable PRUNE_TEMPORARY_BACKUP_RUNNABLE = new DefaultPruneRunnable(
        QuickbackupmultiReforged.getModConfig().getPruneScheduleConfig(),
        DefaultPruneRunnable::defaultPruneTemporaryBackup
    );

    public DefaultPruneRunnable(PruneScheduleConfig config, PruneRunnable executor) {
        this.config = config;
        this.executor = executor;
    }

    @Override
    public void run() {
        CommandSourceStack commandSourceStack = QuickbackupmultiReforged.getServerManager().getCommandSource();

        executor.execute(config, commandSourceStack);
    }

    private static void defaultPruneRegularBackup(PruneScheduleConfig config, CommandSourceStack commandSourceStack) {
        List<StorageInfo> backupList = BackupManager.getBackupsList();
        List<StorageInfo> toDelete = filterBackupWithPbs(config.getRegularBackup(), backupList, config.getTimezoneOverride());
        toDelete.forEach(backup -> BackupManager.deleteBackup(commandSourceStack, backup.getName()));

        QuickbackupmultiReforged.logger.info("Prune backup: {}", toDelete.size());
    }

    private static void defaultPruneTemporaryBackup(PruneScheduleConfig config, CommandSourceStack commandSourceStack) {
        QuickbackupmultiReforged.getManager().deleteTempStorage();

        QuickbackupmultiReforged.logger.info("Prune temporary backup success");
    }

    private static List<StorageInfo> filterBackupWithPbs(PbsConfig pbsConfig, List<StorageInfo> backupList, String timezoneOverride) {
        if (pbsConfig == null || !pbsConfig.isEnabled() || backupList == null || backupList.isEmpty()) {
            return new ArrayList<>();
        }

        ZoneId zoneId = timezoneOverride != null ? ZoneId.of(timezoneOverride) : ZoneId.systemDefault();

        List<StorageInfo> sortedBackups = new ArrayList<>(backupList);
        sortedBackups.sort(Comparator.comparingLong(StorageInfo::getTimestamp).reversed());
        ListIterator<StorageInfo> backupIterator = sortedBackups.listIterator();

        List<StorageInfo> keepList = new ArrayList<>();
        applyPbsPolicy(pbsConfig.getLast(), backupIterator, keepList, StorageInfo::getName);
        applyPbsPolicy(pbsConfig.getHour(), backupIterator, keepList, b -> DurationUtils.formatByUnit(b.getTimestamp(), "hour", zoneId));
        applyPbsPolicy(pbsConfig.getDay(), backupIterator, keepList, b -> DurationUtils.formatByUnit(b.getTimestamp(), "day", zoneId));
        applyPbsPolicy(pbsConfig.getWeek(), backupIterator, keepList, b -> DurationUtils.formatByUnit(b.getTimestamp(), "week", zoneId));
        applyPbsPolicy(pbsConfig.getMonth(), backupIterator, keepList, b -> DurationUtils.formatByUnit(b.getTimestamp(), "month", zoneId));
        applyPbsPolicy(pbsConfig.getYear(), backupIterator, keepList, b -> DurationUtils.formatByUnit(b.getTimestamp(), "year", zoneId));

        // keepList.sort(Comparator.comparingLong(StorageInfo::getTimestamp).reversed());

        if (pbsConfig.getMaxLifeTime() != null && !pbsConfig.getMaxLifeTime().equals("0s")) {
            long maxLifeTimeMillis = DurationUtils.parseDurationToSeconds(pbsConfig.getMaxLifeTime()) * 1000;
            long now = System.currentTimeMillis();
            keepList.removeIf(backup -> now - backup.getTimestamp() > maxLifeTimeMillis);
        }

        if (pbsConfig.getMaxAmount() > 0 && keepList.size() > pbsConfig.getMaxAmount()) {
            keepList.subList(pbsConfig.getMaxAmount(), keepList.size()).clear();
        }

        List<StorageInfo> toDelete = new ArrayList<>(sortedBackups);
        // I'm too lazy to write a double pointer.
        toDelete.removeAll(new HashSet<>(keepList));
        return toDelete;
    }

    private static void applyPbsPolicy(int limit, ListIterator<StorageInfo> backupIterator, List<StorageInfo> keepList, Function<StorageInfo, String> bucketMapper) {
        Set<String> keepBuckets = new HashSet<>();
        for (StorageInfo kept : keepList) {
            String key = bucketMapper.apply(kept);
            keepBuckets.add(key);
        }

        int count = 0;
        while (backupIterator.hasNext()) {
            StorageInfo backup = backupIterator.next();

            String key = bucketMapper.apply(backup);
            if (keepBuckets.contains(key))
                continue;
            if (count >= limit && limit >= 0) {
                backupIterator.previous();
                break;
            }

            keepBuckets.add(key);
            keepList.add(backup);
            count++;
        }
    }

    @FunctionalInterface
    public interface PruneRunnable {
        void execute(PruneScheduleConfig config, CommandSourceStack commandSourceStack);
    }
}
