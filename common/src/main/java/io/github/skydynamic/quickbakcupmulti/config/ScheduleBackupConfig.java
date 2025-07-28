package io.github.skydynamic.quickbakcupmulti.config;

import lombok.Getter;

import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class ScheduleBackupConfig extends ScheduleConfig {
    public boolean resetTimerOnBackup = true;
    public boolean requireOnlinePlayers = false;
    public boolean requireOnlinePlayersIgnoreCarpetFakePlayer = true;
    public List<String> requireOnlinePlayersBlacklist = List.of();
}
