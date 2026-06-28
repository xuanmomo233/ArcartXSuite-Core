# ArcartXSuite 模块开发 SDK

> **Suite — 组曲，亦是套装。**
><img width="3676" height="348" alt="ArcartX-Suite" src="https://github.com/user-attachments/assets/4080970a-8c06-4c56-9735-2072503bdb1d" />
> 在音乐中，**Suite（组曲）** 是由多首独立小曲串联而成的成套器乐作品。
> 一部组曲有四段——序曲启程，变奏展开，华彩交织，终章回响。
> 每一段风格、节奏各不相同，**分开能单独演奏，合起来是一部统一于同一调性的完整作品**。
>
> **ArcartX-Suite** 正是这样的理念：每个模块如同一个乐章，独立运作、各具音色、按需启用、共谱华章，共享 ArcartXSuite 的统一调性。
> 本 SDK 为你提供谱写这一乐章所需的全部乐谱与乐器。

这是 **ArcartXSuite (AXS)** 的模块开发 SDK。本仓库提供完整的公共 API 源码与文档，供第三方开发者基于 AXS 宿主编写自定义模块。

注意：ArcartXSuite 不属于 ArcartX 官方插件，它是基于 ArcartX 客户端模组生态构建的第三方宿主框架。核心职责包括：统一管理 AttributePlus / CraneAttribute / MythicLib / Symphony 等属性桥接、MythicMobs / NeigeItems / MMOItems 等物品来源桥接、Vault / PlayerPoints 等经济桥接；提供模块加载、生命周期管理与配置诊断引擎；内置跨服传输（Redis + Proxy）、按键绑定服务、聊天签名绕过、账号类型识别（正版 / LittleSkin / 离线）等基础设施。所有业务逻辑均通过 `modules/*.jar` 独立模块加载，宿主本身不持有任何业务服务，你可以把 Suite 看做是工具，通过本插件可以与其他模块内部联动，调用API。

## 快速开始

### 获取 `axs-api` JAR

**方式一（推荐）：** 从 [GitHub Releases](https://github.com/xuanmomo233/ArcartXSuite-Core/releases) 下载 `axs-api-<version>.jar`。

**方式二：** 克隆本仓库后本地构建：

```bash
./gradlew :axs-api:jar
# Windows: gradlew.bat :axs-api:jar
```

产物位于 `axs-api/build/libs/axs-api-<version>.jar`。

### 编写模块

1. 将 `axs-api` JAR 放入模块项目的 `libs/`
2. 在模块项目的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    compileOnly(files("libs/axs-api-x.x.x.jar"))
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
}
```

3. 实现 `AbstractAXSModule` 并打包为 `.jar`
4. 将模块 JAR 放入服务器的 `plugins/ArcartXSuite/modules/`
5. 在 `plugins/ArcartXSuite/config.yml` 中启用模块

### 模块 Ed25519 签名（可选）

对 `module.yml` 的 `id:version:main` 做 Ed25519 签名，供服主在 `module-signature-public-keys` 中校验：

```bash
pip install cryptography
python scripts/sign-module.py keygen --out-dir ./module-signing-keys
python scripts/sign-module.py sign --module-yml src/main/resources/module.yml --private-key module-signing-keys/ed25519-private.pem
python scripts/sign-module.py pubkey --public-key module-signing-keys/ed25519-public.pem
```

Wiki 完整说明：[模块 Ed25519 签名](https://github.com/xuanmomo233/ArcartXSuite-Wiki/blob/main/docs/guide/developer/module-signature.md)

## 发布 `axs-api`（维护者）

仓库已配置 GitHub Actions：[Build axs-api](.github/workflows/build-axs-api.yml)（push/PR 校验）、[Release axs-api](.github/workflows/release-axs-api.yml)（发布 JAR）。

### 方式一：打标签自动发布（推荐）

```bash
# Linux / macOS
./scripts/publish-release.sh 1.2.0-beta

# Windows PowerShell
.\scripts\publish-release.ps1 -Version 1.2.0-beta
```

推送 `v<version>` 标签后，CI 会构建 `axs-api-<version>.jar` 并创建 [GitHub Release](https://github.com/xuanmomo233/ArcartXSuite-Core/releases)。

### 方式二：GitHub 网页

1. 更新 `gradle.properties` 中的 `version`
2. 在 GitHub **Releases → Create a new release**，标签填 `v<version>` 并发布
3. CI 自动上传 `axs-api-<version>.jar`

### 方式三：手动触发 Actions

在 **Actions → Release axs-api → Run workflow** 运行，可勾选创建 Release 或仅下载 Artifact。

## 项目结构

| 目录 | 说明 |
|------|------|
| `axs-api/` | 模块公共 API（`AXSModule`, `ModuleContext`, `Bridge API` 等）|
| `axs-placeholder/` | PlaceholderAPI 扩展接口 |
| `proxy/` | Bungee / Velocity 代理端公共库 |
| `scripts/` | 发布脚本（`publish-release.*`、`sign-module.py`） |

## 模块开发指南

参见 [MODULAR-README.md](./MODULAR-README.md)。

## License

作者保留一切权利。
