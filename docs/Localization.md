# 本地化（Localization / i18n）

本文记录 PrototypeMachinery 的本地化（i18n）约定与常见 key 的组织方式，便于 addon/脚本作者补充自己的语言文件。

> 说明：本项目为 Minecraft 1.12.2，使用 `.lang` 格式（而不是 1.13+ 的 json）。

## 语言文件位置

内置语言文件位于：

- `src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- `src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

第三方 addon 通常应在自己的资源包/模组里提供对应的 `assets/<modid>/lang/<locale>.lang`。

---

Chinese original:

- 本页即为中文原文

English translation:

- [`docs/en/Localization.md`](./en/Localization.md)

## Key 的使用方式

本项目在不同场景会用到不同的本地化入口：

- `TextComponentTranslation(key, args...)`：用于聊天消息/命令反馈等。
- `I18n.format(key, args...)`：用于客户端 UI 文本拼接。
- ModularUI 的 `IKey.lang(key)`：用于 GUI 标题/组件文本等（具体使用点见各 GUI 代码）。

> 约定：key 与参数格式以语言文件为准；当 key 缺失时通常会回退显示 key 本身。

## 常见 key 约定

### 机械属性（Machine Attributes）

内置属性的 translation key 约定为：

- `attribute.<namespace>.<path>`

例如（来自 `zh_cn.lang`）：

- `attribute.prototypemachinery.process_speed=进程速度`

实现细节：

- `StandardMachineAttributes` 内部使用 `"attribute.${'$'}{id.namespace}.${'$'}{id.path}"` 生成 key。

相关文档：

- [属性系统（Machine Attributes）](./Attributes.md)

### 方块/物品名称

方块/物品一般使用 `translationKey`（即“未本地化名”）并在语言文件中提供 `.name` 条目。

示例（实现会为控制器方块构造 key）：

- `prototypemachinery.machine.<machine_id>_controller`

语言文件中对应：

- `prototypemachinery.machine.xxx_controller.name=...`

（具体 key 以实现为准；你也可以在运行时用调试方式打印 `translationKey` 来确认。）

### 提示/错误信息

例如结构预览、选择性需求系统内部错误等，通常直接以固定 key 输出：

- `pm.preview.started`
- `error.selective.invalid_selection`

这些 key 也集中维护在语言文件里，便于统一翻译。

## 第三方/脚本的建议

- 如果你新增了“用户可见文本”（GUI 标题、提示、错误信息等），优先使用可本地化的 key，而不是硬编码字符串。
- 为你的 `<modid>` 提供至少 `en_us.lang`，并按需补充其他语言。

