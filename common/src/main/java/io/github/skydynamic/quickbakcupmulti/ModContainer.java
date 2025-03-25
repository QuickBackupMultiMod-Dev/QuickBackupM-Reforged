package io.github.skydynamic.quickbakcupmulti;

import com.mojang.brigadier.CommandDispatcher;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Path;

@Setter
@Getter
public class ModContainer {
    private CommandDispatcher<CommandSourceStack> dispatcher;
    private Path configPath;
    private PermissionManager permissionManager;
    private Path currentSavePath;

    private boolean isRestoringBackup;
    private String currentSelectionBackup;

    public ModContainer(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        this.dispatcher = dispatcher;
    }

    public ModContainer() {}
}
