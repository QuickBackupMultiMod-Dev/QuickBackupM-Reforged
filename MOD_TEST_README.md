# QuickBackupMulti-Reforged 功能测试框架使用指南

## 概述

`harness/` 模块是一个端到端功能测试框架，用于在真实的 Minecraft 服务器和客户端进程中验证 mod 的完整生命周期。与单元测试不同，这些测试会：

- 启动真实的 Minecraft 服务器/客户端进程
- 加载实际编译的 mod jar
- 驱动 `/qb` 命令并验证文件系统和数据库状态
- 在分支声明支持的每个 Minecraft 版本和两个加载器（Fabric/NeoForge）上运行

## 前置要求

### 本地开发环境

- **Java 21+**（用于运行测试框架本身）
- **构建好的 mod jar**：运行 `./gradlew :fabric:remapJar :neoforge:remapJar`
- **足够的磁盘空间**：首次运行会下载 Minecraft 服务器/客户端、库文件和资源（数 GB）
- **客户端测试需要显示环境**：
  - Windows/macOS：自动可用
  - Linux 无头环境：需要 Xvfb（见下文）

### 支持的分支

- Java 21 分支：`1.20.2` / `1.20.3` / `1.20.5` / `1.21` / `1.21.4` / `1.21.5` / `1.21.6` / `1.21.7` / `1.21.10` / `1.21.11`
- Java 25 分支：`26.1` / `26.2`（需要设置 `JAVA25_HOME` 或 `-Dqbm.java25=<路径>`）

## 快速开始

### 1. 运行离线单元测试（快速验证）

```bash
./gradlew :quickbakcupmulti_reforged-harness:test
```

这会运行 36 个纯逻辑单元测试（版本范围解析、报告格式、known-issues 匹配），不启动 Minecraft，1-2 秒完成。

### 2. 运行功能测试（完整验证）

**最小测试**（推荐首次运行）：
```bash
./gradlew :quickbakcupmulti_reforged-harness:functionalTest \
  -Pqbm.versions=1.21 \
  -Pqbm.loaders=fabric \
  -Pqbm.sides=server \
  -Pqbm.scenarios=boot
```

这会测试单个版本的服务器启动，用于验证环境配置正确。

**完整矩阵**：
```bash
./gradlew :quickbakcupmulti_reforged-harness:functionalTest
```

这会对当前分支支持范围内的每个 Minecraft 版本，在 Fabric 和 NeoForge 上运行所有场景（启动、备份/还原、调度器、导出等）。

## 过滤选项

使用 Gradle 属性缩小测试范围：

| 属性 | 示例 | 说明 |
|------|------|------|
| `qbm.versions` | `-Pqbm.versions=1.21,1.21.3` | 只测试指定的 MC 版本 |
| `qbm.loaders` | `-Pqbm.loaders=fabric` | 只测试指定的加载器 |
| `qbm.sides` | `-Pqbm.sides=server` | 只测试服务器端或客户端 |
| `qbm.scenarios` | `-Pqbm.scenarios=boot,lifecycle` | 只运行指定的场景 |
| `qbm.keepRunDirs` | `-Pqbm.keepRunDirs=true` | 保留运行目录以供调试 |
| `qbm.forceRobot` | `-Pqbm.forceRobot=true` | 强制走 Robot 键盘回退（仅本机；裸 Xvfb 下不可用） |

### 常用组合

**只测试服务器端**（跳过客户端，不需要显示环境）：
```bash
./gradlew :quickbakcupmulti_reforged-harness:functionalTest -Pqbm.sides=server
```

**调试特定版本的失败**：
```bash
./gradlew :quickbakcupmulti_reforged-harness:functionalTest \
  -Pqbm.versions=1.21.3 \
  -Pqbm.loaders=neoforge \
  -Pqbm.scenarios=lifecycle \
  -Pqbm.keepRunDirs=true
```

运行目录会保留在 `harness/build/run/<version>-<loader>-<scenario>/`，可以手动启动服务器调试。

## Linux 无头环境（CI/服务器）

客户端测试需要 X11 显示。使用 Xvfb 创建虚拟显示：

```bash
sudo apt-get install xvfb
xvfb-run -a --server-args="-screen 0 1280x1024x24" \
  ./gradlew :quickbakcupmulti_reforged-harness:functionalTest
```

或设置环境变量：
```bash
export DISPLAY=:99
Xvfb :99 -screen 0 1280x1024x24 &
export QBM_HEADLESS_DISPLAY=1
./gradlew :quickbakcupmulti_reforged-harness:functionalTest
```

## 理解测试结果

### 兼容性报告

每次运行后生成 `build/compat-report/compatibility.md`，包含：

```markdown
| Version | Loader | Side | Scenario | Status |
|---------|--------|------|----------|--------|
| 1.21    | fabric | server | boot   | ✅ PASSED |
| 1.21.1  | fabric | server | boot   | ❌ FAILED: AssertionError: ... |
| 1.21.2  | neoforge | server | lifecycle | ⚠️ KNOWN_FAILURE (#123) |
```

**状态说明**：
- `✅ PASSED`：测试通过
- `❌ FAILED`：mod 在此版本上有 bug
- `⚠️ KNOWN_FAILURE`：已知问题，在 `.github/known-issues.json` 中标记
- `🔧 ERROR`：基础设施错误（下载失败、超时），不是 mod 的问题
- `⏭️ SKIPPED`：环境不支持（缺少 Java 25、无显示环境等）
- `❗ UNEXPECTED_PASS`：已标记的问题实际通过了，需要删除 known-issues 条目

### 日志位置

- **测试日志**：`build/compat-report/logs/<version>-<loader>-<scenario>/`
- **Minecraft 日志**：`build/compat-report/logs/<version>-<loader>-<scenario>/boot-1.log`

## 测试场景说明

### 服务器端场景

| 场景 | 覆盖内容 |
|------|----------|
| `boot` | Mod 加载、Mixin 应用 |
| `lifecycle` | 完整的备份/列举/还原循环 |
| `nested-world` | 回归测试 #56（还原不创建嵌套 world/world） |
| `full-rotation` | 回归测试 #55（完整备份轮转收敛） |
| `schedules` | 回归测试 #54（重置一个调度器不影响其他） |
| `export-delete` | 导出备份到独立副本并删除 |

### 客户端场景

| 场景 | 覆盖内容 |
|------|----------|
| `menu` | 客户端启动、Mixin 应用 |
| `world` | 集成服务器启动、每个世界的备份存储初始化、通过聊天框驱动 `/qb make` |
| `restore` | 完整的客户端驱动还原（`/qb restore` + `/qb confirm`），验证世界重建 |

## 标记已知问题

如果某个版本有已知的 mod bug（不是测试框架的问题），在 `.github/known-issues.json` 中标记：

```json
{
  "issues": [
    {
      "branch": "1.21",
      "version": "1.21.3",
      "loader": "*",
      "side": "*",
      "scenario": "lifecycle",
      "reason": "Mixin target moved in 1.21.3",
      "issue": "#789"
    }
  ]
}
```

**通配符**：`*` 匹配任意值。例如 `"version": "1.21.*"` 匹配 `1.21.1` / `1.21.2` / `1.21.3` 等。

**重要**：如果标记的问题实际通过了，测试会**失败**（`UNEXPECTED_PASS`），提示删除过时的条目。这防止标记掩盖真正的回归。

## CI 集成

GitHub Actions 工作流在 `.github/workflows/functional-tests.yml`：

```yaml
- name: Run functional tests
  run: |
    xvfb-run -a --server-args="-screen 0 1280x1024x24" \
      ./gradlew :quickbakcupmulti_reforged-harness:functionalTest
  env:
    QBM_HEADLESS_DISPLAY: 1
    QBM_HARNESS_CACHE: ~/.gradle/qbm-harness-cache

- name: Check gate
  run: python3 .github/workflows/scripts/functional_gate.py build/compat-report
```

**矩阵生成**：CI 从分支的 `minecraft_supported_versions` 动态生成矩阵：
```bash
./gradlew :quickbakcupmulti_reforged-harness:printMcMatrix
```

输出 `build/mc-matrix.json`，工作流用它并行运行每个版本。

## 用 act 在本地模拟 GitHub workflow

功能测试 workflow 跑在 `ubuntu-latest` + Xvfb 上，Windows 本机无法等价复现。
用 nektos/act 在 Docker Linux 引擎里跑同一份
`.github/workflows/functional-tests.yml`。

前置：Docker Desktop（Linux 引擎）、`winget install nektos.act`。

```powershell
pwsh ./scripts/act-ci.ps1 setup
pwsh ./scripts/act-ci.ps1 dry-run
pwsh ./scripts/act-ci.ps1 smoke    # 1.21 Fabric 服务端 boot
pwsh ./scripts/act-ci.ps1 client   # 1.21 Fabric 客户端 menu（Xvfb）
```

不要对整个 support range 跑 act：那会下载数 GB 并持续很久。需要缩小范围时，
GitHub / act 都认同一组 inputs：`versions`、`loaders`、`sides`、`scenarios`。

## 故障排查

### 问题：`No built jar matching -mc1.21.1-fabric-`

**原因**：矩阵尝试测试 1.21.1，但分支只构建了 `-mc1.21-fabric-` jar。

**解决**：这是正常的 —— 分支的 `minecraft_version=1.21` 产出单个 jar，用于测试范围内的所有版本（1.21 / 1.21.1 / 1.21.2 / 1.21.3）。已在 Stage 0.1 修复。

### 问题：`Minecraft 26.1 needs Java 25 but the harness is running on Java 21`

**解决**：设置 `JAVA25_HOME` 环境变量或传递 `-Dqbm.java25=/path/to/java`。

### 问题：客户端测试超时（`Time to start:` 从未出现）

**原因**：无显示环境（Linux 无头），或 GLFW 加载错误的原生库。

**解决**：
1. 确认 `DISPLAY` 已设置且 Xvfb 正在运行
2. 设置 `QBM_HEADLESS_DISPLAY=1`
3. 检查 `build/compat-report/logs/<version>-fabric-client-<scenario>/client-1.log` 中的 GLFW 错误

### 问题：NeoForge 安装器失败

**检查**：`<runDir>/neoforge-installer.log`

**常见原因**：
- 网络问题（Maven Central 不可达）— Stage 2 添加了重试
- JDK 版本不匹配 — Stage 2 修复了传递错误的 `java`

### 问题：测试通过但标记为 `UNEXPECTED_PASS`

**解决**：从 `.github/known-issues.json` 中删除该条目，bug 已修复。

## 高级用法

### 查看测试覆盖哪些版本

```bash
./gradlew :quickbakcupmulti_reforged-harness:printMcMatrix
cat build/mc-matrix.json
```

输出示例（1.21 分支）：
```json
{
  "branch": "1.21",
  "versions": ["1.21", "1.21.1", "1.21.2", "1.21.3"],
  "loaders": ["fabric", "neoforge"],
  "java": 21
}
```

### 并行运行（多核）

默认 `maxParallelForks=1`（每次一个场景）。可以增加：

```bash
./gradlew :quickbakcupmulti_reforged-harness:functionalTest --max-workers=4
```

但注意：每个场景占用 ~2GB 堆 + 整个服务器进程。

### 保留下载缓存

下载的 MC 服务器、客户端、资源缓存在 `.gradle/qbm-harness-cache/`（本地）或 `$QBM_HARNESS_CACHE`（CI）。`clean` 不会删除它。

手动清理：
```bash
rm -rf .gradle/qbm-harness-cache
```

### 调试单个场景

1. 使用 `-Pqbm.keepRunDirs=true` 保留运行目录
2. 找到 `harness/build/run/<version>-<loader>-<scenario>/`
3. 查看 `config/QuickBackupMulti.json`、`world/`、`QuickBackupMulti/` 等
4. 手动启动服务器调试：
   ```bash
   cd harness/build/run/1.21-fabric-lifecycle
   java -jar server.jar nogui
   ```

## 架构说明

### 测试框架不使用的东西

- ❌ **不使用 vanilla launcher**：直接从 Mojang/Fabric 元数据组装类路径
- ❌ **不模拟 Minecraft**：启动真实的游戏进程
- ❌ **不使用 Minecraft 的 JUnit 钩子**：这是一个外部黑盒测试

### 测试框架使用的东西

- ✅ **真实的游戏进程**：`java -jar server.jar nogui` 或 Fabric 客户端启动器
- ✅ **标准输入命令**（服务器）：`/qb make "backup"` 写入 stdin
- ✅ **Robot 键盘注入**（客户端）：`java.awt.Robot` 在聊天框中输入 `/qb make clientprobe`
- ✅ **日志标记等待**：`awaitLine("Make Backup thread close")` 扫描 `logs/latest.log`
- ✅ **文件系统和 H2 数据库断言**：读取 `world/`、`QuickBackupMulti/` 和 `.mv.db`

### 关键设计决策

1. **每个场景一个新目录**：避免泄漏状态，独立失败
2. **内容寻址缓存**：资源和库共享，避免重复下载 GB 数据
3. **`.part` 原子下载**：中断的运行不会毒化缓存
4. **xfail 系统**：已知问题标记为 `KNOWN_FAILURE`，意外通过会失败（防止掩盖回归）
5. **Robot 驱动客户端**：真实的玩家输入路径，不是绕过 mod 的测试专用后门

## 贡献指南

### 添加新测试场景

1. 在 `ServerLifecycleTest.java` 或 `ClientLifecycleTest.java` 中添加 `@ParameterizedTest`
2. 使用 `runner.run(mc, loader, side, "scenario-name", () -> { ... })`
3. 在 lambda 中执行 provision → boot → 驱动命令 → 断言
4. 运行 `./gradlew :quickbakcupmulti_reforged-harness:functionalTest -Pqbm.scenarios=scenario-name` 验证

### 添加新日志标记

如果 mod 添加了新的英文日志标记（不依赖本地化），可以在测试中使用 `awaitLine()` / `sawLine()`。

**禁止**：依赖翻译的消息（`tr("quickbackupmulti.backup.success")`） —— 会在 `lang=en_us` 之外中断。

### 同步到其他分支

功能测试在 1.21 分支上全绿后，cherry-pick 到其他分支：

```bash
git checkout 1.20.5
git cherry-pick <commit-hash>
./gradlew :quickbakcupmulti_reforged-harness:functionalTest
```

**注意**：26.1/26.2 分支使用 `ci_build_task=build`（不是 `remapJar`）和 Java 25，可能需要小调整。

---

## 示例输出

成功运行：
```
[harness] branch 1.21 range [1.21, 1.21.4) -> versions [1.21, 1.21.1, 1.21.2, 1.21.3] on [fabric, neoforge]
[harness] using mod jar: build/libs/QuickBakcupMulti-Reforged-mc1.21-fabric-3.3.3+snapshot.jar

ServerLifecycleTest > 1.21 / fabric — boots with mixins applied PASSED
ServerLifecycleTest > 1.21 / fabric — make, list, restore round trip PASSED
ServerLifecycleTest > 1.21 / neoforge — boots with mixins applied PASSED
...
ClientLifecycleTest > 1.21 / Fabric client — restores a backup (client-driven) PASSED

Compatibility report: build/compat-report/compatibility.md
```

失败运行：
```
ServerLifecycleTest > 1.21.3 / fabric — make, list, restore round trip FAILED
  org.opentest4j.AssertionFailedError: The restore did not revert level.dat to its backed-up size
  Expected: 123456
  Actual: 123461
  --- last 40 lines of 1.21.3/fabric ---
  [Server thread/INFO]: Make a temp backup success.
  [Server thread/ERROR]: Failed to restore backup: ...
```

查看 `build/compat-report/logs/1.21.3-fabric-lifecycle/boot-1.log` 获取完整日志。

---

有问题或 bug？提交 issue 到 [GitHub Issues](https://github.com/SkyDynamic/QuickBackupM-Reforged/issues)，附上：
- 分支名称
- 完整的命令和输出
- `build/compat-report/compatibility.md`
- 相关的 `build/compat-report/logs/` 日志