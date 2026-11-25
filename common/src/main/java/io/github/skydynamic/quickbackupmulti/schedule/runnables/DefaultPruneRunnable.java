package io.github.skydynamic.quickbackupmulti.schedule.runnables;

import io.github.skydynamic.quickbackupmulti.QuickbackupmultiReforged;
import io.github.skydynamic.quickbackupmulti.config.PruneScheduleConfig;
import io.github.skydynamic.quickbackupmulti.utils.BackupManager;
import net.minecraft.commands.CommandSourceStack;

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
        // The selection itself lives in BackupManager so that `/qb prune dryrun` reports exactly what
        // this scheduled run would delete.
        int deleted = BackupManager.pruneRegularBackups(commandSourceStack, false).size();

        QuickbackupmultiReforged.logger.info("Prune backup: {}", deleted);
    }

    private static void defaultPruneTemporaryBackup(PruneScheduleConfig config, CommandSourceStack commandSourceStack) {
        QuickbackupmultiReforged.getManager().deleteTempStorage();

        QuickbackupmultiReforged.logger.info("Prune temporary backup success");
    }

    @FunctionalInterface
    public interface PruneRunnable {
        void execute(PruneScheduleConfig config, CommandSourceStack commandSourceStack);
    }
}
