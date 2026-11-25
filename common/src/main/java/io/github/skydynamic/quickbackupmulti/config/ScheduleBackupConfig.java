package io.github.skydynamic.quickbackupmulti.config;

import lombok.Getter;

import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Getter
public class ScheduleBackupConfig extends ScheduleConfig {
    public boolean resetTimerOnBackup = true;
    public boolean requireOnlinePlayers = false;
    public boolean requireOnlinePlayersIgnoreCarpetFakePlayer = true;
    public List<String> requireOnlinePlayersBlacklist = List.of();
    /** Broadcast the start/finish of every scheduled backup in chat, to every online player. */
    public boolean notifyPlayers = false;
}
