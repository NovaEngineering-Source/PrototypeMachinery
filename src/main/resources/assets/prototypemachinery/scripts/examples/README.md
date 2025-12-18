# CraftTweaker Scripts Examples

# CraftTweaker 脚本示例

这个文件夹包含了 PrototypeMachinery 的 CraftTweaker 脚本示例。

## 文件说明

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

- 脚本必须使用 `#loader preinit` 加载器
- 机器ID必须唯一
- 引用的结构JSON文件必须存在
- CraftTweaker的错误会在日志中显示

## 相关文档

- 结构定义示例：`assets/prototypemachinery/structures/examples/`
- 官方文档：查看 mod 仓库的 docs 文件夹
