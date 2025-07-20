package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class ScheduleBackupConfig extends ScheduleConfig {
    private boolean resetTimerOnBackup = true;
    private boolean requireOnlinePlayers = false;
    private boolean requireOnlinePlayersIgnoreCarpetFakePlayer = true;
    private List<String> requireOnlinePlayersBlacklist = List.of();
}
