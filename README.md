[![License](https://img.shields.io/github/license/SkyDynamic/QuickBackupM-Fabric.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Issues](https://img.shields.io/github/issues/QuickBackupMultiMod-Dev/QuickBackupM-Reforged.svg)](https://github.com/QuickBackupMultiMod-Dev/QuickBackupM-Fabric/issues)
[![Modrinth](https://img.shields.io/modrinth/dt/DgWBIBY5?label=Modrinth%20Downloads)](https://modrinth.com/mod/quickbackupmulti)
[![CurseForge](https://img.shields.io/curseforge/dt/951047?label=CurseForge%20Downloads)](https://www.curseforge.com/minecraft/mc-mods/quickbackupmulti)
[![v2 Github Release](https://img.shields.io/github/downloads/QuickBackupMultiMod-Dev/QuickBackupM-Fabric/total?label=V2%20Github%20Downloads)](https://github.com/QuickBackupMultiMod-Dev/QuickBackupM-Fabric/releases)
[![v3 Github Release](https://img.shields.io/github/downloads/QuickBackupMultiMod-Dev/QuickBackupM-Reforged/total?label=V3%20Github%20Downloads)](https://github.com/QuickBackupMultiMod-Dev/QuickBackupM-Reforged/releases)

<div align="center">
<a><img src="./indexImg.png" width="180" height="180" alt="Logo"></a>
</div>
<div align="center">

# QuickBackupMulti-Reforged

**简体中文** | [English]()

_✨ MC备份 / 回档模组 ✨_  
重构自MCDR插件: [QuickBackupMulti](https://github.com/TISUnion/QuickBackupM)
与 Mod [QuickBackupMulti-Fabric](https://github.com/QuickBackupMultiMod-Dev/QuickBackupM-Fabric)

## 警告
### 如果你使用的是v3.1.0之后的mod, 你的之前的备份将会永久丢失, 因为3.1.0更换了更优秀的备份算法, 请做好数据备份

</div>

> [!WARNING]  
> v3与v2的数据库并不一致, 无法直接迁移
> 
> 数据库迁移可以使用 [这个工具](https://github.com/QuickBackupMultiMod-Dev/Qbm-DatabaseMerge/releases)

> 当前Mod大版本为`v3`, 相比于`v2`, 使用了更高性能的数据库, 并且重构了项目结构与简化了代码
> 
> 本mod已支持\NeoForge/

## 本Mod优势
- 支持回档自动重启服务器, 不再是只备份不回档
- 客户端支持回档自动重进存档! 

## 使用方式
> [!WARNING]  
> 严禁自行删除备份文件夹内的所有备份文件, 如需删除请进入游戏内进行手动删除! 

> 在使用mod前请确保你已安装Fabric Loader

将本mod放进`mods`文件夹即可

## 配置说明
详见 [配置说明](./docs/zh_cn/config.md)

## 指令
`/qb` 或 `/quickbackupmulti`均可触发mod

## 特性
- [x] 定时备份
- [x] 无限槽位
- [x] Hash对比并仅备份差异文件
- [x] 个性化设置

## 许可
本项目遵循 [LGPL-3.0 License](https://www.gnu.org/licenses/lgpl-3.0.en.html) 许可
