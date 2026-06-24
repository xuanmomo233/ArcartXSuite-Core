# ArcartXSuite 模块开发 SDK

> **Suite — 组曲，亦是套装。**
>
> 在音乐中，**Suite（组曲）** 是由多首独立小曲串联而成的成套器乐作品。
> 一部组曲有四段——序曲启程，变奏展开，华彩交织，终章回响。
> 每一段风格、节奏各不相同，**分开能单独演奏，合起来是一部统一于同一调性的完整作品**。
>
> **ArcartX-Suite** 正是这样的理念：每个模块如同一个乐章，独立运作、各具音色、按需启用，共享 ArcartX 的统一调性。
> 本 SDK 为你提供谱写这一乐章所需的全部乐谱与乐器。

这是 **ArcartXSuite (AXS)** 的模块开发 SDK。本仓库提供完整的公共 API 源码与文档，供第三方开发者基于 AXS 宿主编写自定义模块。

注意：ArcartXSuite 不属于 ArcartX 官方插件，它是基于 ArcartX 客户端模组生态构建的第三方宿主框架。核心职责包括：统一管理 AttributePlus / CraneAttribute / MythicLib / Symphony 等属性桥接、MythicMobs / NeigeItems / MMOItems 等物品来源桥接、Vault / PlayerPoints 等经济桥接；提供模块加载、生命周期管理与配置诊断引擎；内置跨服传输（Redis + Proxy）、按键绑定服务、聊天签名绕过、账号类型识别（正版 / LittleSkin / 离线）等基础设施。所有业务逻辑均通过 `modules/*.jar` 独立模块加载，宿主本身不持有任何业务服务。

## 快速开始

1. 获取 `axs-api` JAR
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

## 项目结构

| 目录 | 说明 |
|------|------|
| `axs-api/` | 模块公共 API（`AXSModule`, `ModuleContext`, `Bridge API` 等）|
| `axs-placeholder/` | PlaceholderAPI 扩展接口 |
| `proxy/` | Bungee / Velocity 代理端公共库 |
| `src/main/java/xuanmo/arcartxsuite/` | 宿主核心源码参考（`ModuleRegistry`, `BridgeLifecycleManager` 等）|

## 模块开发指南

参见 [MODULAR-README.md](./MODULAR-README.md)。

## License

作者保留一切权利。
