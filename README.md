# ArcartXSuite 模块开发 SDK

这是 **ArcartXSuite (AXS)** 的模块开发 SDK。本仓库提供完整的公共 API 源码与文档，供第三方开发者基于 AXS 宿主编写自定义模块。

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
