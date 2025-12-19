# CraftTweaker Scripts Examples

# CraftTweaker 脚本示例

这个文件夹包含了 PrototypeMachinery 的 CraftTweaker 脚本示例。

## 文件说明

### jei_demo_machine_registration.zs

JEI 演示机器注册（多 IO）示例脚本：

- #loader preinit
- 注册 3 台 demo 机器（A/B/C），均使用 `example_recipe_processor_machine_io_demo` 结构
- 三台机器分别绑定不同 recipe group，便于在 JEI 中展示不同布局风格

### jei_demo_recipes.zs

JEI 演示配方脚本：

- #loader crafttweaker reloadable
- 为 A/B/C 三个 group 分别提供多条配方
- 覆盖 item / fluid / energy 的输入输出，并包含 per-tick（*_PER_TICK）示例

### jei_demo_layouts.zs

JEI 演示布局脚本：

- #loader crafttweaker reloadable
- 为 demo 机器 A/B/C 注册 3 套不同风格的 JEI layout（不同尺寸、分区、decorator、fixed slot）
- 使用新版 nine-slice 背景与 module 美术变体

### machine_registration.zs

机器注册示例脚本，展示了如何：

- 创建基本机器定义
- 引用结构文件
- 添加组件类型
- 设置机器属性

### recipe_processor_full_example.zs

配方处理器“最小闭环”示例：

- 注册一个带 `FactoryRecipeProcessorComponent` 的机器
- 绑定 `recipeGroups`（否则扫描系统不会启动）
- 注册一个仅包含 parallelism 的配方（不需要仓室）

### recipe_processor_with_hatches_example.zs

配方处理器“带仓室”示例：

- 结构中包含物品输入/输出仓
- 由于部分环境在 preinit 阶段无法解析物品，本示例已拆分为两份脚本：
	- `recipe_processor_with_hatches_example.zs`：#loader preinit，仅注册机器
	- `recipe_processor_with_hatches_recipes.zs`：#loader postinit，注册配方（itemInput/itemOutput）

### recipe_processor_with_hatches_recipes.zs

配方处理器“带仓室”的配方脚本：

- #loader postinit
- 包含 itemInput + itemOutput，必须通过仓室才能运行

## 使用方法

### 自动复制（推荐）

1. 首次运行游戏时，如果检测到 CraftTweaker 已加载
2. 这些示例会自动复制到 `scripts/prototypemachinery/examples/`
3. 可以直接编辑或作为参考

### 手动复制

1. 将 `.zs` 文件复制到 `scripts/prototypemachinery/` 目录
2. 根据需要修改脚本
3. 运行 `/mt reload` 重新加载脚本（开发环境）
4. 或重启游戏以应用更改

## 注意事项

- **机器注册**通常必须使用 `#loader preinit`（否则机器定义阶段可能错过注册时机）。
- **配方 / JEI layout** 建议使用 `#loader postinit` 或 `#loader crafttweaker reloadable`，便于解析物品/流体并支持 `/mt reload`。
- 机器 ID 必须唯一。
- 引用的结构 JSON 文件必须存在。
- CraftTweaker 的错误会在日志中显示。

> 备注：旧的 `jei_layout_example.zs` 已弃用，不再自动注册任何布局；请以 demo 三件套为准。

## 相关文档

- 结构定义示例：`assets/prototypemachinery/structures/examples/`
- 官方文档：查看 mod 仓库的 docs 文件夹
