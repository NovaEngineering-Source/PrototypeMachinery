# PrototypeMachinery Structure Examples

# 结构示例说明

这个文件夹包含了多个机器结构示例，展示了如何定义多方块结构。

## 文件说明

### simple_machine.json

最简单的机器结构示例：

- 类型：template（模板结构）
- 包含4个方块的简单2x2x2结构
- 适合初学者了解基本结构定义

### slice_machine.json

切片结构示例：

- 类型：slice（切片结构）
- 展示如何定义可重复的垂直层
- minCount: 3（最少3层）
- maxCount: 10（最多10层）
- sliceOffset: 每层向上偏移1格

### complex_machine.json

复杂机器结构示例：

- 类型：template
- 3x3的底座加中心发光方块
- 使用offset偏移中心位置
- 展示更复杂的多方块布局

### child_structure.json 和 parent_with_child.json

父子结构示例：

- child_structure.json: 定义子结构（玻璃顶部）
- parent_with_child.json: 父结构引用子结构
- 展示如何组合多个结构

## 字段说明

- `id`: 结构的唯一标识符
- `type`: 结构类型（template 或 slice）
- `offset`: 结构相对于核心方块的偏移
- `pattern`: 方块模式定义
    - `pos`: 方块的相对位置
    - `blockId`: 方块ID（格式：modid:blockname）
    - `meta`: 方块元数据值
- `minCount/maxCount`: （仅slice类型）层数范围
- `sliceOffset`: （仅slice类型）每层的偏移向量
- `validators`: 验证器列表（高级功能）
- `children`: 子结构ID列表

## 使用方法

1. 复制这些文件到 `config/prototypemachinery/structures/` 目录
2. 根据需要修改结构定义
3. 重启游戏以加载新结构
4. 使用对应的CraftTweaker脚本注册机器

## 注意事项

- 所有坐标都是相对坐标
- blockId必须是有效的注册方块ID
- 子结构的ID必须存在
- 避免循环引用（A引用B，B又引用A）
