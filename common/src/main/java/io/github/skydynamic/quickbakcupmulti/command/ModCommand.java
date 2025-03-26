package io.github.skydynamic.quickbakcupmulti.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.skydynamic.quickbakcupmulti.command.settings.SettingCommand;
import lombok.Getter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModCommand {
    @Getter
    private static final Logger logger = LoggerFactory.getLogger("Qbm-CmdExecutor");

    public static class CmdExecuteThread extends Thread {
        private final Runnable executor;

        public CmdExecuteThread() {
            this.executor = null;
        }

        public CmdExecuteThread(Runnable executor) {
            this.executor = executor;
            start();
        }

        @Override
        public void run() {
            if (executor != null) {
                logger.info("CmdExecutor thread started...");
                executor.run();
            } else {
                logger.error("executor must be not null");
            }
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("qb")
            .then(ListCommand.cmd)
            .then(MakeCommand.cmd)
            .then(DeleteCommand.cmd)
            .then(PermissionCommand.cmd)
            .then(RestoreCommand.restoreCmd)
            .then(RestoreCommand.confirmCmd)
            .then(RestoreCommand.cancelCmd)
            .then(SettingCommand.cmd)
            .then(SearchCommand.cmd)
            .then(ShowCommand.cmd)
        );
    }
}
