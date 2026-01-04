# 06 机器 GUI：用 PMUI 搭面板（含 Web Editor 导出）

这一章讲：怎么给机器做 GUI。

你可以把 GUI 理解成：

- 玩家右键机器看到的界面
- 可以放按钮、文本、进度条、槽位显示等

## 1. 两条路线：手写 builders vs Web Editor 导出

### A. 手写 builders（适合做小改动/写简单界面）

- 用 `mods.prototypemachinery.ui.PMUI` 创建控件
- 用 `mods.prototypemachinery.ui.UIRegistry` 把面板注册给机器

### B. Web Editor 导出（适合做复杂界面）

- Editor 导出两种脚本：
  - builders（可读性好）
  - runtime-json（更贴近运行时，容错更强）

PM 侧提供：

- `UIRegistry.registerRuntimeJson(machineId, runtimeJson)`

## 2. 最小 GUI：一块面板 + 一个按钮

```zenscript
#loader crafttweaker reloadable

import mods.prototypemachinery.ui.PMUI;
import mods.prototypemachinery.ui.UIRegistry;

val MACHINE = "my_pack:gas_generator";

val panel = PMUI.createPanel()
    .setSize(176, 166)
    .addChild(
        PMUI.button("Hello")
            .setPos(10, 10)
            .setSize(60, 18)
    );

UIRegistry.register(MACHINE, panel);
```

> 注意：GUI 只是“显示/交互”。机器能不能跑配方，还是取决于机器组件与配方本身。

## 3. UI 优先级（覆盖默认 UI）

当同一个机器注册了多个 UI，PM 会按 priority 选择。

```zenscript
UIRegistry.registerWithPriority(MACHINE, panel, 10);
```

## 4. 实战建议：先复用默认 UI，再逐步定制

整合包开发时最省事的路线：

1) 先不写 UI，确认机器与配方跑通
2) 再写 UI，只覆盖你想改的部分（例如加一个说明文本/加几个按钮）

## 5. TODO：补一个“真实机器 GUI”的完整脚本

我会从你提供的 NovaEng-CRL 脚本风格里挑一台机器，给出一个“实战 GUI”：

- 左侧：配方列表/进度
- 右侧：输入输出槽位展示
- 底部：一行说明文字 + 一个开关按钮

并附：

- builder 版脚本
- runtime-json 版脚本（模拟 Web Editor 输出）
