# Configuration File

## Configuration File Detailed Explanation
The configuration file is stored in the `config` directory, and the file name is `QuickBackupMulti.json`
This is a complete explanation of the configuration file. If you want to learn more, please read on patiently.

### Root Configuration
This is the root JSON object in the configuration file
```json
{
  "checkUpdate": true,
  "ignoredFiles": [],
  "ignoredFolders": [],
  "lang": "zh_cn",
  "autoRestartMode": "DEFAULT",
  "relaunchCommand": "",
  "restoreCountdownSeconds": 10,
  "clientAutoReJoinWorld": true,
  "storagePath": "./QuickBackupMulti",
  "cacheDatabase": false,
  
  "scheduleBackup": {/* 定时备份配置 */},
  "prune": {/* 修剪配置 */},
  "database": {/* 数据库配置 */}
}
```

#### **checkUpdate**
Whether to check for updates
- Type: `bool`
- Default value: `true`

#### **ignoredFiles**
List of ignored files
- Type: `string[]`
- Default value: `[]`

#### **ignoredFolders**
List of ignored folders
- Type: `string[]`
- Default value: `[]`

#### **lang**
Language
- Type: `string`
- Default value: `zh_cn`

#### **autoRestartMode**
How the server is brought back after a rollback (server only, ignored on the client)

| Value | Behaviour |
| --- | --- |
| `DISABLE` | Do not restart; the process exits once the restore is done |
| `DEFAULT` | Exactly the same as `SELF_RESTART` (kept for old configs) |
| `SELF_RESTART` | The mod launches a new process with the command line it was started with, then exits — no panel, wrapper script or service manager needed |
| `MCSM` | Exit with code `1` and let an external supervisor (MCSManager and similar) restart the server |

- Type: `string`
- Default value: `DEFAULT`

> [!IMPORTANT]
> `DEFAULT` and `SELF_RESTART` start a new process themselves. If your launcher or panel is **already** configured to restart the server when the process exits, use `MCSM` instead — otherwise two servers will end up running at once.
>
> Restarting "in place" is gone on purpose: the old implementation called `runServer()` again on the same `MinecraftServer`, whose world-generation registries had already been modified by the previous boot. On Minecraft 26.x that fails immediately with `IllegalStateException: Dimensions in this dynamic registries instance have already been modified` (issue [#61](https://github.com/QuickBackupMultiMod-Dev/QuickBackupM-Reforged/issues/61)), and the loader bootstrap (Fabric's `Knot`, NeoForge's `FMLLoader`) only runs once per JVM anyway.

> Compatibility: aliases such as `NONE` written by older versions are read as `DISABLE`.

#### **relaunchCommand**
The command line used by `SELF_RESTART` / `DEFAULT` to start the replacement process. Empty means "derive it from the current process", which is what you want in almost every case.

Only fill this in when the derived command is wrong — a wrapper script, a container entrypoint, or a panel that rewrites the launch arguments. The string is split on whitespace, and double quotes group a path containing spaces.

```json
"relaunchCommand": "java -Xmx4G -jar server.jar nogui"
```
- Type: `string`
- Default value: `""`

#### **restoreCountdownSeconds**
How many seconds the confirmation countdown lasts after `/qb confirm` (server only)

`0` restores immediately with no chance to cancel; values above 3600 are clamped
- Type: `int`
- Default value: `10`

#### **clientAutoReJoinWorld**
Whether to automatically join the world after rollback is completed (client only, server invalid)
- Type: `bool`
- Default value: `true`

#### **storagePath**
The root directory used by QuickBackupMulti-Reforge to store various data files  
Relative paths are supported, such as: `./QuickBackupMulti` or `QuickBackupMulti`  
Absolute paths are supported, such as: `/home/user/QuickBackupMulti` or `C:/QuickBackupMulti`

- Type: `string`
- Default value: `./QuickBackupMulti`

#### **cacheDatabase**  

Whether to cache the database  
After enabling, all backup information will be cached to a temporary list after each database operation, which can query backup information faster

- Type: `bool`
- Default value: `false`

---

### Scheduled Backup Configuration

Configuration for the scheduled backup function  

This function will automatically create backups for the server on a regular basis

```json
{
  "enabled": false,
  "interval": "3h",
  "crontab": null,
  "resetTimerOnBackup": true,
  "requireOnlinePlayers": false,
  "requireOnlinePlayersIgnoreCarpetFakePlayer": true,
  "requireOnlinePlayersBlacklist": [],
  "notifyPlayers": false
}
```

#### **enabled, interval, crontab**
See [Scheduled Job Configuration](#scheduled-job-configuration) section

#### **resetTimerOnBackup**
Whether to reset the timer after backup is completed
- Type: `bool`
- Default value: `true`

#### **requireOnlinePlayers**
If set to true, scheduled backups will only be performed when there are players on the server
- Type: `bool`
- Default value: `false`

#### **requireOnlinePlayersIgnoreCarpetFakePlayer**
When [requireOnlinePlayers](#requireonlineplayers) determines whether there are players online, whether to exclude carpet fake players
- Type: `string[]`
- Default value: `[]`

#### **requireOnlinePlayersBlacklist**
When [requireOnlinePlayers](#requireonlineplayers) determines whether there are players online, a list of player name regular expressions to exclude  
If you want the server to be considered as having no players online when only certain players are online, and thus not perform scheduled backups, you can use this option  
Configuration example:

```json
"requireOnlinePlayersBlacklist": [
    "bot_.*",  // 匹配所有以 "bot_" 为前缀的玩家名
    "Steve"    // 匹配 "Steve" 这个玩家名
]
```

#### **notifyPlayers**
Whether the start, completion and failure of a scheduled backup are broadcast to every online player

Scheduled backups run while players are on the server and can cause a short stutter; with this enabled they know what it was
- Type: `bool`
- Default value: `false`

---

### Prune Configuration
QuickBackupMulti-Reforge's backup pruning function can be used to automatically clean up outdated backups

```json
{
  "enabled": false,
  "interval": "3h",
  "crontab": null,
  "timezoneOverride": null,
  "regularBackup": {
    "enabled": false,
    "maxAmount": 10,
    "maxLifeTime": "0s",
    "last": -1,
    "hour": 0,
    "day": 0,
    "week": 0,
    "month": 1,
    "year": 0
  },
  "temporaryBackup": {
    "enabled": false,
    "interval": "3h",
    "crontab": null
  }
}
```

It contains two pruning settings, for the following two types of backups respectively:
- `regularBackup`: For regular backups, i.e., non-temporary backups
- `temporaryBackup`: For temporary backups, such as backups before rollback

regularBackup describes the retention policy of archives in detail

Like MCDR's [Prime Backup](https://github.com/TISUnion/PrimeBackup), QuickBackupMulti-Reforge will perform the following steps to decide which backups to delete/keep
1. Use `last` `hour` `day` `week` `month` `year`, based on [PBS](https://pbs.proxmox.com/docs/prune-simulator/) retention policy, to filter out backups to delete/keep
2. Use the two rules `max_amount`, `max_lifetime` to filter out old and expired backups among those retained in the first step
3. Collect the backups that need to be deleted found in the above two filtering processes, and delete them one by one

#### **maxAmount**
Defines the maximum number of backups to retain, e.g. 10 means to retain the latest 10 backups at most

This cap applies **after** the PBS options above: if `last`..`year` selected 30 backups and `maxAmount` is 10, only the newest 10 are kept, the other 20 are still deleted, and a warning is logged. Set it to 0 when you use long-term options such as `month` / `year`

Setting to 0 means no limit
- Type: `int`
- Default value: `10`

#### **maxLifeTime**
Defines the maximum retention time for all backups. Backups exceeding the given time will be cleaned up

Setting to 0 means no time limit (any duration that parses to zero, e.g. `0s` or `0m`)
- Type: [`Duration`](#Duration)

### **last, hour, day, week, month, year**
A set of [PBS](https://pbs.proxmox.com/docs/prune-simulator/) style pruning options, used to describe how backups are deleted/retained

See [Prune Simulator](https://pbs.proxmox.com/docs/prune-simulator/) for more explanation of these options

[Prune Simulator](https://pbs.proxmox.com/docs/prune-simulator/) can also be used to simulate backup retention policies

Note: Value 0 means no backups are retained for that interval; value -1 means infinite backups can be retained for that interval, equivalent to setting a very large value
- Type: `int`

Each interval counts **the newest backup inside it**, and a backup already kept by an earlier interval does not take up a slot in a later one.

`week` uses **ISO weeks** (`2026-W01`), so the same week across a new year counts as one week.

To see which backups the current settings would delete without deleting anything, run `/qb prune dryrun`.

---

#### **enabled, interval, crontab**
See [Scheduled Job Configuration](#scheduled-job-configuration) section

#### **timezoneOverride**
The timezone used during backup pruning. By default (using `null` value), QuickBackupMulti-Reforge will use the local timezone

Examples: `null`, `"Asia/Shanghai"`, `"US/Eastern"`, `"Europe/Amsterdam"`

- Type: `Option[string]`
- Default value: `null`

---

### Database Configuration

Configuration related to the H2 database used by QuickBackupMulti-Reforge

```json
{
  "backup": {
    "enabled": false,
    "interval": "12h",
    "crontab": null
  }
}
```

#### **backup**
Database backup job

#### **enabled, interval, crontab**
See [Scheduled Job Configuration](#scheduled-job-configuration) section

---

## Sub-configuration Item Description
### Scheduled Job Configuration
A configuration related to scheduled jobs, used to describe when the job will be executed. There are two modes:
- Interval mode: Execute jobs at given time intervals. The first execution also needs to wait for the given interval
- Cron mode: Execute jobs at specific times, described by crontab string

If the job is enabled, you must choose one of the above modes and set the relevant configuration values correctly

```json
{
  "enabled": false,
  "interval": "3h",
  "crontab": null
}
```

#### **enabled**
Switch for the job. Set to true to enable the job, set to false to disable the scheduled job
- Type: `bool`
- Default value: `false`

#### **interval**
Used in interval mode. Time interval between two tasks

If the job does not use interval mode, its value should be `null`

- Type: [`Duration`](#duration)
- Default value: `3h`

#### **crontab**
Used in cron mode. A six-digit crontab string describing the schedule

You can use [https://calctools.online/zh/time/cron#quartz](https://calctools.online/zh/time/cron#quartz) to create a crontab string

- Type: [string](file://com\mojang\brigadier\arguments\StringArgumentType.java#L12-L12)
- Default value: `null`

---

## Special Value Types

### Duration

A time duration described using a string, such as: `"3s"`, `"15m"`

Duration consists of two parts: a number and a time unit.

For the numeric part, it can be an integer or a floating point number

For the unit part, see the table below:

| Unit                           | Description  | Equivalent Value | Seconds  |
|--------------------------------|--------------|------------------|----------|
| `ms`, `milli`, `millis`        | Milliseconds | 0.001 Seconds    | 0.001    |
| `s`, `sec`, `second`           | Seconds      | 1 Second         | 1        |
| `m`, `min`, `minutes`          | Minutes      | 1 Minute         | 60       |
| `h`, `hr`, `hour`, `hours`     | Hours        | 60 Minutes       | 3600     |
| `d`, `day`, `days`             | Days         | 24 Hours         | 86400    |
| `mo`, `mon`, `month`, `months` | Months       | 30 Days          | 2592000  |
| `y`, `yr`, `year`, `years`     | Years        | 365 Days         | 31536000 |