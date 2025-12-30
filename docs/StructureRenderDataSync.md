# 结构渲染数据同步（StructureRenderDataComponent）

> 目标：让客户端渲染线程 **不再做结构匹配/扫描**，改为消费服务器计算好的最小派生数据（bounds + sliceCounts），并支持 **增量同步**。

---

## 1. 背景：为什么要做成组件 + 增量同步

在结构规模较大、或 TESR 数量较多时，如果客户端为了渲染/隐藏逻辑在渲染线程反复做：

- `StructurePattern.matches(...)`
- `SliceStructure.matches(...)`

会导致严重的卡顿与热点（并且会引入“客户端与服务器判断不一致”的风险）。

因此本项目将“渲染/隐藏所需的结构派生数据”定义为一个 **系统组件**：

- 服务器定期刷新结构 → 计算派生数据 → 通过组件同步给客户端
- 客户端渲染只读该组件

---

## 2. 组件定义

类型与接口：

- `src/main/kotlin/api/machine/component/type/StructureRenderDataComponent.kt`

实现：

- `src/main/kotlin/impl/machine/component/StructureRenderDataComponentImpl.kt`

对外暴露的数据：

- `dataVersion: Int`：数据版本号（只要数据变化就递增）
- `structureMin/structureMax: BlockPos?`：世界坐标下的结构包围盒（block coords）
- `sliceCounts: Map<String, Int>`：每个结构 id（SliceStructure）对应的 matchedCount

---

## 3. 同步策略：FULL + INCREMENTAL

### 3.1 FULL（chunk load / 初次同步）

`writeFullSyncData` / `readFullSyncData` 使用以下 NBT 键：

- `V`：`dataVersion`
- `Min`：int[3]（x,y,z）
- `Max`：int[3]（x,y,z）
- `S`：compound（key = 结构 id，value = matchedCount）

FULL 的语义是“快照”：客户端先 reset，再完整应用。

### 3.2 INCREMENTAL（周期刷新 / 小改动）

`writeIncrementalSyncData` / `readIncrementalSyncData` 使用以下 NBT 键：

- `V`：`dataVersion`
- `Clr`：boolean，清空（unformed 或从 formed→unformed）
- `Min`/`Max`：bounds 更新
- `Set`：compound（增量 set/overwrite sliceCounts）
- `Rem`：String，使用 `\u0000` 作为分隔符的 key 列表（表示从 sliceCounts 删除这些 key）

设计要点：

- `Clr` 会覆盖其它 diff（清空意味着 bounds 与 slices 都无意义）。
- `Rem` 用 `\u0000` 分隔，避免与常见结构 id（含 `:`、`/` 等）冲突。

---

## 4. 服务器侧更新点（结构刷新）

关键入口：`src/main/kotlin/impl/MachineInstanceImpl.kt`

结构刷新流程（简化）：

1. 后台线程匹配结构，收集：
   - `positions`（结构块坐标集合）
   - `sliceCounts`（SliceStructure 计数）
   - `minPos/maxPos`（包含 controller 自身）
2. 切回主线程 `applyStructureRefreshResult(...)`：
   - 若不匹配：
     - `formed=false`（TE 仍然走原有 formed state 的 TE sync）
     - 调用 `StructureRenderDataComponent.updateFromServer(false, null, null, emptyMap())` 并 `syncComponent(c)`（INCREMENTAL）
   - 若匹配：
     - 更新/重建结构派生组件（StructureComponents）
     - 调用 `updateFromServer(true, minPos, maxPos, sliceCounts)`，若变化则 `syncComponent(c)`（INCREMENTAL）

该策略保证：

- “结构是否 formed”仍由 TE 的同步语义控制（兼容既有逻辑）
- “渲染/隐藏数据”走组件同步，且尽量增量

---

## 5. 客户端消费点

典型消费位置：

- `MachineBlockEntitySpecialRenderer`：从 `te.machine.componentMap` 读取 `StructureRenderDataComponentType`，用于结构渲染相关的提交。

另外，组件增量同步包：

- `src/main/kotlin/common/network/PacketSyncMachine.kt`

其中做了一个关键改动：

- **按运行时 `componentMap` 的 key 解析 componentType**，而不是只在 `machineType.componentTypes` 中查找。
- 这样像 `StructureRenderDataComponentType` 这类“系统/内部组件”也能同步到客户端。
