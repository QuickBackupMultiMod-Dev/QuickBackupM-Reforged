package io.github.skydynamic.quickbakcupmulti;

import com.mojang.brigadier.CommandDispatcher;
import io.github.skydynamic.quickbakcupmulti.schedule.IModSchedule;
import io.github.skydynamic.quickbakcupmulti.utils.permission.PermissionManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.commands.CommandSourceStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Setter
@Getter
public class ModContainer {
    private ModVersion modVersion;
    private ModEnvType envType;

    private String originalStoragePath;
    private String levelId = "";

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private Path configPath;
    private PermissionManager permissionManager;
    private Path currentSavePath;

    private boolean isRestoringBackup;
    private String currentSelectionBackup;

    private List<IModSchedule> schedules = new ArrayList<>();

    public ModContainer(
        CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        this.dispatcher = dispatcher;
    }

    public ModContainer() {}

    public Optional<IModSchedule> getSchedule(String name) {
        return schedules.stream().filter(it -> it.getName().equals(name)).findFirst();
    }
}
