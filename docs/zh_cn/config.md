# 配置文件

## 配置文件详解
配置文件存放在`config`目录下, 文件名为`QuickBackupMulti.json`  
这是对配置文件的完整解释。如果你想深入了解, 请耐心往下看。

### 根配置
这是配置文件中的根 json 对象
```json
{
  "checkUpdate": true,
  "ignoredFiles": [],
  "ignoredFolders": [],
  "lang": "zh_cn",
  "autoRestartMode": "DEFAULT",
  "clientAutoReJoinWorld": true,
  "storagePath": "./QuickBackupMulti",
  "cacheDatabase": false,
  
  "scheduleBackup": {/* 定时备份配置 */},
  "prune": {/* 修剪配置 */},
  "database": {/* 数据库配置 */}
}
```

#### **checkUpdate**
是否检查更新
- 类型：`bool`
- 默认值：`true`

#### **ignoredFiles**
忽略的文件列表
- 类型：`string[]`
- 默认值：`[]`

#### **ignoredFolders**
忽略的文件夹列表
- 类型：`string[]`
- 默认值：`[]`

#### **lang**
语言
- 类型：`string`
- 默认值：`zh_cn`

#### **autoRestartMode**
自动重启模式 (仅服务端, 客户端无效)
- 类型：`string`
- 默认值：`DEFAULT`

#### **clientAutoReJoinWorld**
是否在回档完成后自动加入世界 (仅客户端, 服务端无效)
- 类型：`bool`
- 默认值：`true`

#### **storagePath**
QuickBackupMulti-Reforge 储存各种数据文件所用的根目录  
支持相对路径, 如：`./QuickBackupMulti` 或 `QuickBackupMulti`  
支持绝对路径, 如：`/home/user/QuickBackupMulti` 或 `C:/QuickBackupMulti`

- 类型：`string`
- 默认值：`./QuickBackupMulti`

#### **cacheDatabase**  

是否缓存数据库  
开启后, 每次进行数据库操作后会缓存所有备份信息到一个临时列表, 能够更快的查询备份信息

- 类型：`bool`
- 默认值：`false`

---

### 定时备份配置

定时备份功能的配置  

该功能会定期为服务器自动创建备份

```json
{
  "enabled": false,
  "interval": "3h",
  "crontab": null,
  "resetTimerOnBackup": true,
  "requireOnlinePlayers": false,
  "requireOnlinePlayersIgnoreCarpetFakePlayer": true,
  "requireOnlinePlayersBlacklist": []
}
```

#### **enabled, interval, crontab**
见 [定时作业配置](#定时作业配置) 小节

#### **resetTimerOnBackup**
是否在备份完成后重置定时器
- 类型：`bool`
- 默认值：`true`

#### **requireOnlinePlayers**
若设为 true, 则只有在服务器中存在玩家时, 才正常进行定时备份
- 类型：`bool`
- 默认值：`false`

#### **requireOnlinePlayersIgnoreCarpetFakePlayer**
在 [requireOnlinePlayers](#requireOnlinePlayers) 判断是否存在玩家在线时, 是否排除 carpet 假人存在
- 类型：`string[]`
- 默认值：`[]`

#### **requireOnlinePlayersBlacklist**
在 [requireOnlinePlayers](#requireOnlinePlayers) 判断是否存在玩家在线时, 排除的玩家名正则表达式列表  
如果你希望服务器中只有某些玩家在线的时候, 也视作服务器中不存在玩家,  不进行定时备份, 则可以使用该选项  
配置举例：

```json
"requireOnlinePlayersBlacklist": [
    "bot_.*",  // 匹配所有以 "bot_" 为前缀的玩家名
    "Steve"    // 匹配 "Steve" 这个玩家名
]
```

---

### 清理配置
QuickBackupMulti-Reforge 的备份清理功能可用于自动清理过时备份

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

它包含两种清理设置, 分别针对于如下两种类型的备份：
- `regularBackup`: 针对常规备份, 即非临时备份
- `temporaryBackup`: 针对临时备份, 如回档前的备份

regularBackup 详细描述了存档的保留策略

同 MCDR 的 [Prime Backup](https://github.com/TISUnion/PrimeBackup), QuickBackupMulti-Reforge 会执行以下步骤来决定删除/保留哪些备份
1. 使用 `last`, `hour`, `day`, `week`, `month`, `year`,  基于 [PBS](https://pbs.proxmox.com/docs/prune-simulator/) 保留策略, 筛选出要删除/保留的备份
2. 使用 `max_amount`, `max_lifetime` 这两条规则, 在第一步保留的那些备份里, 筛出那些旧的和过期的备份
3. 收集上述两次筛选过程中, 找到的哪些需要删除的备份, 并逐个进行删除

#### **maxAmount**
定义要保留的最大备份数量，例如 10 表示最多保留最新的 10 个备份

设置为 0 表示无限制
- 类型：`int`
- 默认值：`10`

#### **maxLifeTime**
定义所有备份的最大保存时长。超出给定时长的备份将被清理

设置为 0s 表示无时长限制
- 类型：[`Duration`]()

### **last, hour, day, week, month, year**
一组 [PBS](https://pbs.proxmox.com/docs/prune-simulator/) 风格的清理选项，用于描述备份的删除/保留方式

查看 [清理模拟器](https://pbs.proxmox.com/docs/prune-simulator/) 了解这些选项的更多解释

[清理模拟器](https://pbs.proxmox.com/docs/prune-simulator/) 也可用于模拟备份的保留策略

注意：值 0 表示不为该区间保留任何备份；值 -1 表示该区间可以保留无限多的备份，与设为极大值等价
- 类型：`int`

---

#### **enabled, interval, crontab**
见 [定时作业配置](#定时作业配置) 小节

#### **timezoneOverride**
在备份清理时所使用的时区. 默认情况下(使用 `null` 值), QuickBackupMulti-Reforge 将使用本地时区

例子：`null`, `"Asia/Shanghai"`, `"US/Eastern"`, `"Europe/Amsterdam"`

- 类型：`Option[string]`
- 默认值：`null`

---

### 数据库配置

QuickBackupMulti-Reforge 所使用的 H2 数据库的相关配置

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
数据库备份作业

#### **enabled, interval, crontab**
见 [定时作业配置](#定时作业配置) 小节

---

## 子配置项说明
### 定时作业配置
一个定时作业相关的配置, 用于描述该作业会在什么时候执行.有两种模式: 
- 间隔模式: 按给定的时间间隔执行作业. 第一次执行也要等待给定的间隔
- 定时模式: 在特定时间执行作业, 由 crontab 字符串描述

若作业被启用，你必须选择上述模式之一，并正确设置相关配置值

```json
{
  "enabled": false,
  "interval": "3h",
  "crontab": null
}
```

#### **enabled**
作业的开关。设为 true 以启用该作业，设为 false 以禁用该定时作业
- 类型：`bool`
- 默认值：`false`

#### **interval**
在间隔模式中使用. 两次任务之间的时间间隔

若作业未使用间隔模式，其值应为 `null`

- 类型：[`Duration`](#Duration)
- 默认值：`3h`

#### **crontab**
在定时模式中使用. 描述定时计划的一个六位 crontab 字符串

你可以使用 [https://calctools.online/zh/time/cron#quartz](https://calctools.online/zh/time/cron#quartz) 来创建一个 crontab 字符串

- 类型：`string`
- 默认值：`null`

---

## 特殊的值类型

### Duration

使用字符串描述的时间持续长度, 如：`"3s"`, `"15m"`

Duration 由两部分组成: 数字和时间单位.

对于数字部分, 它可以是整数或浮点数

对于单位部分, 参见下表:

| 单位                             | 描述 | 等价值    | 秒数       |
|--------------------------------|----|--------|----------|
| `ms`, `milli`, `millis`        | 毫秒 | 0.001秒 | 0.001    |
| `s`, `sec`, `second`           | 秒  | 1秒     | 1        |
| `m`, `min`, `minutes`          | 分钟 | 1分钟    | 60       |
| `h`, `hr`, `hour`, `hours`     | 小时 | 60 分钟  | 3600     |
| `d`, `day`, `days`             | 天  | 24 小时  | 86400    |
| `mo`, `mon`, `month`, `months` | 月  | 30 天   | 2592000  |
| `y`, `yr`, `year`, `years`     | 年  | 365 天  | 31536000 |