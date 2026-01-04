# 03 注册机器：把“结构 + 功能”变成一个可放置的控制器

这一章讲怎么用 ZenScript 注册机器类型（Machine Type）。

你可以把“机器类型”理解成：

- 玩家手里能拿到的那个**控制器方块**是什么
- 它需要什么结构才能成型
- 成型后能做哪些事（最常见：跑配方）

## 1. 你会用到的入口

- `mods.prototypemachinery.MachineRegistry`
- `mods.prototypemachinery.MachineTypeBuilder`（一般不需要手动 new，由 MachineRegistry.create 返回）

## 2. 最小可用机器（能跑配方的最低配置）

一个能跑配方的机器，至少需要：

1) 指定结构 `structure("...")`
2) 指定配方组 `addRecipeGroup("...")`
3) 加上“配方处理器组件” `addComponentType(<pmcomponent:factory_recipe_processor>)`

> 关于“pmcomponent 传参”的重要说明：
>
> - 目前 `addComponentType(<pmcomponent:...>)` 主要表达“把某个组件类型挂到机器上”，**不直接携带参数/数据**。
> - 这对整合包作者来说不够直观（尤其是从 MMCE 迁移时常希望“加组件的同时传配置”）。
> - 规划方向有两种（择其一或并存）：
>   1) **为常用组件在 `MachineTypeBuilder` 上提供专用方法**（例如 `processParallelism(...)`、未来可能有 `workSlots(...)` 等），参数走强类型字段，而不是塞进配方/组件的 NBT。
>   2) **为每个组件类型提供对应的 ZenScript 扩展/DSL 方法**来传参（例如 `m.factoryRecipeProcessor { ... }` / `m.workSlots { ... }`），由扩展在 Kotlin 侧把参数写入 machine type。
>
> 这样可以避免“组件=黑盒 NBT”，也更利于后续存档/同步/兼容维护。

示例（推荐拆到 preinit 文件里）：

```zenscript
#loader preinit

import mods.prototypemachinery.MachineRegistry;

val GROUP = "my_pack:gas_generator";

val m = MachineRegistry.create("my_pack", "gas_generator");
m.name("Gas Generator");

// 结构：推荐用结构 id（延迟解析，加载更稳）
m.structure("my_pack:gas_generator");

// 并行相关（可选，但非常常用）
// 1) maxConcurrentProcesses：同时允许跑几个“配方进程”（默认 1）
// 2) processParallelism：单个进程的“并行倍数上限”（当前实现以机器侧上限为准；配方侧的 parallelism requirement 不再作为上限来源）
m.maxConcurrentProcesses(2);
m.processParallelism(8);

// 调度/执行模式（可选，默认 CONCURRENT）
// - CONCURRENT：机器逻辑可能在并发线程执行（默认，性能更好）
// - MAIN_THREAD：强制在主线程执行（更保守，适合某些非线程安全交互）
m.executionMode("CONCURRENT");

// 配方组：后面 PMRecipeBuilder 会把配方加到这个组
m.addRecipeGroup(GROUP);

// 核心：能跑配方
m.addComponentType(<pmcomponent:factory_recipe_processor>);

MachineRegistry.register(m);
```

## 3. 给控制器换模型（可选）

如果你需要自定义控制器方块外观：

```zenscript
m.setControllerModel("my_pack:block/gas_generator_controller");
```

> 注意：这里填的是资源路径（ResourceLocation），你需要确保对应模型资源存在。

## 4. 进阶组织方式：一个机器一个文件 vs 按模块分文件

整合包工程里常见两种：

- 方案 A：每个机器一个 `.zs`（适合超大整合包）
- 方案 B：按玩法模块分组（例如 power/chem/space）

建议你至少做到：

- `00_registry_preinit.zs` 里只做机器注册
- `10_recipes_reloadable.zs` 里只做配方注册

这样排错会非常快。

## 5. 从 MMCE 迁移时最常见的心理落差

在 MMCE 里，你可能习惯：

- machine name 是一个字符串（有时是无命名空间）
- recipe 直接绑定 machine

在 PM 里推荐的思路是：

- **机器绑定配方组（recipe group）**
- **配方只属于 group**

这样做的好处：

- 一类机器可以共享一组配方
- JEI 布局也能按 group/机器拆分

迁移细节见第 08 章。
