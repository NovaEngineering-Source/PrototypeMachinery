# 构建终端

structure_preview_group ：结构预览组 X:10 Y:10 W:172 H:206[
    [使用 gui_structure_preview 中的 machine_prefix_name 组件 位置 X:1 Y:2],
    [使用 gui_structure_preview 中的 preview_reset 组件 位置 X:118 Y:2],
    [使用 gui_structure_preview 中的 structural_forming 组件 位置 X:136 Y:2],
    [使用 gui_structure_preview 中的 structural_information 组件 位置 X:154 Y:2],
    [de_dmaterial_preview_ui ：在X:1 Y:21 W:169 H:165 设置 material_preview 组件，背景贴图为 ./gui_build_instrument/material_preview.png ,光标在X:158 Y:3 W:8 H:159 区域按住移动时此组件跟随光标移动，移动到JEI标签页时JEI物品内容需要根据此组件的尺寸进行避让。
    此UI的四条边可按住缩放组件，四个角有额外5*5px的位置可按住缩放 四角所在的位置为中点，X轴缩放时18px为一个步进，最小缩放为W:44 H:24[
        在X:3 Y:3 W:144 H:159 设置 material_plaid 组件，此组件为结构材料预览区域 未开始构建时显示树状图所选择的构建材料 正在构建时显示待构建的材料 拆除时显示待拆除的材料[
            使用 ./states 贴图中的X:74 Y:11 W:18 H:18 为预览材料的格子背景，预览格子在区域内左对齐排列，预览材料增加时格子背景，组件X轴缩放每增加18px，横向增加一个预览格子，横向空间填满时在下一行排列，预览材料显示在格子的X:2 Y:2 W:16 H:16内。
            光标在区域内上下拖动、滚轮滚动时可上下移动材料预览列表，移动时 material_preview_slider 滑块同步移动。
        ],
        在X:149 Y:4 W:7 H:157 设置 material_preview_sliding_groove 组件，此组件为 material_plaid 区域的滑条[
            在X:1 Y:1 W:7 H:12 设置 material_preview_slider 滑块 贴图 [
                默认./gui_structure_preview/slider_default ，选中./gui_structure_preview/slider_selected ，按下./gui_structure_preview/slider_press]，
            滑块沿Y轴移动，光标在区域内拖动滑块和滚轮皆可滑动材料预览列表]
    按下 button_material_preview_ui  时此组件从中间弹出，按钮恢复默认状态后向中间缩回。]];

    [down_button ：在X:131 Y:187 W:40 H:16 设置此组件[

        [component_switch ：在X:28 Y:1 ，W:12 H:14 设置此按钮，默认状态使用 ./gui_structure_preview/down_button_default/component_switch_default ，选中状态使用 ./gui_structure_preview/down_button_selected/component_switch_selected ，按下状态使用 ./gui_structure_preview/down_button_pressed/component_switch_pressed 。
        未按下时不启用，按下松开后光标还在触发区域时触发，默认未按下 ],

        [down_button_base ：在X:0 Y:3 ，W:24 H:13 设置 down_button_base 组件，背景贴图为 ./gui_structure_preview/down_button_base_on 
            [按钮组指示灯 button_base_led
            在X:2 Y:3 W:1 H:7 设置 button_base_led 贴图组件，component_switch 未按下时贴图为 ./gui_structure_preview/down_button_base_off-led 按下时贴图为 ./gui_structure_preview/down_button_base_on-led],

            [材料预览UI按钮 button_material_preview_ui
            在X:6 Y:-3 W:12 H:14 设置 button_material_preview_ui 按钮，默认背景贴图为 ./gui_structure_preview/down_button_defaultdown_button_material_preview_ui_default ，选中时贴图为 ./gui_structure_preview/down_button_selected/material_preview_ui_selected ，按下时贴为 ./gui_structure_preview/down_button_pressed/material_preview_ui_pressed 。
            按下时启用 material_preview_ui 组件，默认时关闭，按下松开后光标还在触发区域时触发，此按钮为自锁按钮，默认未按下。]
        按钮 component_switch 按下时组件内按钮禁用，X轴移动+19px，移动到选定范围外的部分遮罩隐藏，未按下时为默认状态。]
    ]]
]

tree_diagram ：树状图 在X:191 Y:8 W:185 H:192 创建此组件，按住 X:191 Y:8 W:185 H:19 时拖动树状图视图，在树状图区域 滚轮为上下移动视图 crtl+滚轮上下为缩放视图 shift+滚轮上下为左右移动视图[

    main_item ：主条目 W:31 H:15 组件尺寸增加时W同时增加 [
        [连接点：
            [X:5 Y:14 W:2 H:1 为下连接点  #F2F2F2， X:7 Y:14 W:1 H:1为下连接点阴影 #696D88，连接点向Y+扩展]
        ],
        [图标 ./gui_build_instrument/tree_icon/main],
        [背景 ./gui_build_instrument/tree_diagram 此贴图中的[X:0 Y:0 W:13 H:15 为左贴图 ,X:14 Y:0 W:1 H:15 为中拉伸贴图 ,X:16 Y:0 W:17 H:15 为右贴图 ] 左贴图置于X:0 Y:0 W:13 H:15 ,中拉伸贴图置于X:13 Y:0 W:1 H:15 组件W尺寸增加时W向X+轴拉伸,右贴图置于X:14 Y:0 W:17 H:15 组件W尺寸增加时X向X+轴移动],
        [ec_button ：展开收回按钮 X:17 Y:0 W:11 H:12 按下收回按钮松开后变为展开并展开包含的条目 按下展开按钮松开后变为收回
            [展开：默认 ./gui_build_instrument/button/expand_default.png 选中./gui_build_instrument/button/expand_selected.png 按下 ./gui_build_instrument/button/expand_pressed.png]，
            [收回：默认 ./gui_build_instrument/button/withdraw_default.png 选中./gui_build_instrument/button/withdraw_selected.png 按下 ./gui_build_instrument/button/withdraw_pressed.png]
        ]
    ],

    extend_structure ：扩展结构条目 W:31 H:15 [
        [连接点：
            [X:-1 Y:6 W:1 H:2 为左连接点 #F2F2F2，X:-1 Y:6 W:1 H:1 #696D88 为左连接点阴影，连接点线条向X-扩展]，
            [X:5 Y:14 W:2 H:1 为下连接点 #F2F2F2，X:7 Y:14 W:1 H:1 #696D88 为下连接点阴影，连接点线条向Y+扩展]
        ],
        [图标 ./gui_build_instrument/tree_icon/extend_structure],
        [背景 ./gui_build_instrument/tree_diagram 此贴图中的[X:0 Y:0 W:13 H:15 为左贴图 ,X:14 Y:0 W:1 H:15 为中拉伸贴图 ,X:16 Y:0 W:17 H:15 为右贴图 ] 左贴图置于X:0 Y:0 W:13 H:15 ,中拉伸贴图置于X:13 Y:0 W:1 H:15 组件W尺寸增加时W向X+轴拉伸,右贴图置于X:14 Y:0 W:17 H:15 组件W尺寸增加时X向X+轴移动],
        [ec_button ：展开收回按钮 X:17 Y:0 W:11 H:12 按下收回按钮松开后变为展开并展开包含的条目 按下展开按钮松开后变为收回
            [展开：默认 ./gui_build_instrument/button/expand_default.png 选中./gui_build_instrument/button/expand_selected.png 按下 ./gui_build_instrument/button/expand_pressed.png]，
            [收回：默认 ./gui_build_instrument/button/withdraw_default.png 选中./gui_build_instrument/button/withdraw_selected.png 按下 ./gui_build_instrument/button/withdraw_pressed.png]
        ]
    ],

    extend_length ：扩展长度条目 W:33 H:15 [
        [连接点：
            [X:-1 Y:6 W:1 H:2 为左连接点 #F2F2F2，X:-1 Y:6 W:1 H:1 #696D88 为左连接点阴影，连接点线条向X-扩展]，
            [X:5 Y:14 W:2 H:1 为下连接点 #F2F2F2，X:7 Y:14 W:1 H:1 #696D88 为下连接点阴影，连接点线条向Y+扩展]
        ],
        [图标 ./gui_build_instrument/tree_icon/extend_structure],
        [length_input_box输入框 X:14 Y:3 W:1 H:8 输入数值来设置结构扩展长度，超出设定长度上限则修正为最大扩展长度],
        [背景 ./gui_build_instrument/tree_diagram 此贴图中的[X:0 Y:0 W:14 H:15 为左贴图 ,X:15 Y:0 W:1 H:15 为中拉伸贴图 ,X:17 Y:0 W:18 H:15 为右贴图 ] 左贴图置于X:0 Y:0 W:14 H:15 ,中拉伸贴图置于X:14 Y:0 W:1 H:15 组件W尺寸增加时W向X+轴拉伸,右贴图置于X:15 Y:0 W:18 H:15 组件W尺寸增加时X向X+轴移动],
        [ec_button：展开收回按钮 X:19 Y:0 W:11 H:12 按下收回按钮松开后变为展开并展开包含的条目 按下展开按钮松开后变为收回
            [展开：默认 ./gui_build_instrument/button/expand_default.png 选中./gui_build_instrument/button/expand_selected.png 按下 ./gui_build_instrument/button/expand_pressed.png]，
            [收回：默认 ./gui_build_instrument/button/withdraw_default.png 选中./gui_build_instrument/button/withdraw_selected.png 按下 ./gui_build_instrument/button/withdraw_pressed.png]
        ]
    ],

    substructure ：子结构条目 W:53 H:15 [
        [连接点：
            [X:-1 Y:6 W:1 H:2 为左连接点 #F2F2F2，X:-1 Y:6 W:1 H:1 #696D88 为左连接点阴影，连接点线条向X-扩展]，
            [X:5 Y:14 W:2 H:1 为下连接点 #F2F2F2，X:7 Y:14 W:1 H:1 #696D88 为下连接点阴影，连接点线条向Y+扩展]
        ],
        [图标 ./gui_build_instrument/tree_icon/extend_structure],
        [substructure_switch: 子结构开关 X:19 Y:0 W:19 H:12 选择是否使用此子结构，开关贴图 W:10 H:12 [
            ./gui_build_instrument/switch.png 开启状态位于X:0 Y:0 ,开启状态选中时位于X:2 Y:0 ，关闭状态位于X:9 Y:0 ，关闭状态选中时位于X:7 Y:0
        ]],
        [背景 ./gui_build_instrument/tree_diagram 此贴图中的[X:0 Y:0 W:13 H:15 为左贴图 ,X:14 Y:0 W:1 H:15 为中拉伸贴图 ,X:17 Y:0 W:39 H:15 为右贴图 ] 左贴图置于X:0 Y:0 W:14 H:15 ,中拉伸贴图置于X:13 Y:0 W:1 H:15 组件W尺寸增加时W向X+轴拉伸,右贴图置于X:14 Y:0 W:39 H:15 组件W尺寸增加时X向X+轴移动],
        [ec_button：展开收回按钮 X:39 Y:0 W:11 H:12 按下收回按钮松开后变为展开并展开包含的条目 按下展开按钮松开后变为收回
            [展开：默认 ./gui_build_instrument/button/expand_default.png 选中./gui_build_instrument/button/expand_selected.png 按下 ./gui_build_instrument/button/expand_pressed.png]，
            [收回：默认 ./gui_build_instrument/button/withdraw_default.png 选中./gui_build_instrument/button/withdraw_selected.png 按下 ./gui_build_instrument/button/withdraw_pressed.png]
        ]
    ],

    replace ：替换方块条目 W:31 H:12 [
        [连接点：
            [X:-1 Y:6 W:1 H:2 为左连接点 #F2F2F2，X:-1 Y:6 W:1 H:1 #696D88 为左连接点阴影，连接点线条向X-扩展]，
            [X:5 Y:14 W:2 H:1 为下连接点 #F2F2F2，X:7 Y:14 W:1 H:1 #696D88 为下连接点阴影，连接点线条向Y+扩展]
        ],
        [图标 ./gui_build_instrument/tree_icon/replace],
        [背景 ./gui_build_instrument/tree_diagram 此贴图中的[X:0 Y:0 W:13 H:15 为左贴图 ,X:14 Y:0 W:1 H:15 为中拉伸贴图 ,X:16 Y:0 W:17 H:15 为右贴图 ] 左贴图置于X:0 Y:0 W:13 H:15 ,中拉伸贴图置于X:13 Y:0 W:1 H:15 组件W尺寸增加时W向X+轴拉伸,右贴图置于X:14 Y:0 W:17 H:15 组件W尺寸增加时X向X+轴移动],
        [ec_button ：展开收回按钮 X:17 Y:0 W:11 H:12 按下收回按钮松开后变为展开并展开包含的条目 按下展开按钮松开后变为收回
            [展开：默认 ./gui_build_instrument/button/expand_default.png 选中./gui_build_instrument/button/expand_selected.png 按下 ./gui_build_instrument/button/expand_pressed.png]，
            [收回：默认 ./gui_build_instrument/button/withdraw_default.png 选中./gui_build_instrument/button/withdraw_selected.png 按下 ./gui_build_instrument/button/withdraw_pressed.png]
        ]
    ]
]

progress_bar ： 进度条 在X:192 Y:207 W:160 H:9 创建此组件，根据本次所操作的方块总数，根据操作进度百分比拉伸进度条[
    进度条前部 X:0 Y:0 W:1 H:9 根据进度条百分比向右扩展W的范围 W:1为0% W:159为100%[
        构建状态颜色为 #17B86D 
        暂停状态颜色为 #ECA23C
        拆卸状态颜色为 #D73E42
    ],
    进度条尾部 X:1 Y:0 W:1 H:9 根据进度条百分比向右增加X的位置 X:1为0% X:159为100%[
        构建状态颜色为 #079B6B
        暂停状态颜色为 #D9782F
        拆卸状态颜色为 #AA212B
    ]
]

task_status_button ： 任务状态按钮 在X:357 Y:205 W:20 H:14 创建此按钮组件[
    start_building ：开始构建，按下松开此按钮后切换为 start_building，并根据树状图的选择结果开始构建结构 结构内若是已包含部分已构建的结构时只构建未构建的部分 构建时从下到上构建 材料不足时向ME网络发配缺少材料的合成任务 没有对应样板时取消本次操作并提示没有对应样板，如果结构内包含流体和重力方块 则在放置所有常规方块后再放置流体和重力方块，贴图为[
        默认./gui_build_instrument/button.png X:0 Y:0 W:20 H:14
        选中./gui_build_instrument/button.png X:21 Y:0 W:20 H:14
        按下./gui_build_instrument/button.png X:42 Y:0 W:20 H:14
    ],
    under_construction ：正在构建，按下松开此按钮后切换为 pause_operation ，贴图为[
        默认./gui_build_instrument/button.png X:0 Y:15 W:20 H:14
        选中./gui_build_instrument/button.png X:21 Y:15 W:20 H:14
        按下./gui_build_instrument/button.png X:42 Y:15 W:20 H:14
    ],
    pause_operation ：暂停操作，按下松开此按钮后继续当前任务，根据任务类型切换到 under_construction 或 disassembling (请分析此状态是否必要)，贴图为[
        默认./gui_build_instrument/button.png X:0 Y:30 W:20 H:14
        选中./gui_build_instrument/button.png X:21 Y:30 W:20 H:14
        按下./gui_build_instrument/button.png X:42 Y:30 W:20 H:14
    ],
    start_disassembling ：开始拆卸，按下松开后切换为 disassembling 并拆卸结构内包含的所有方块与流体存入ME网络，贴图为[
        默认./gui_build_instrument/button.png X:0 Y:45 W:20 H:14
        选中./gui_build_instrument/button.png X:21 Y:45 W:20 H:14
        按下./gui_build_instrument/button.png X:42 Y:45 W:20 H:14
    ],
    disassembling ：正在拆卸，按下松开此按钮后切换为 pause_operation ，贴图为[
        默认./gui_build_instrument/button.png X:0 Y:60 W:20 H:14
        选中./gui_build_instrument/button.png X:21 Y:60 W:20 H:14
        按下./gui_build_instrument/button.png X:42 Y:60 W:20 H:14
    ],
]

为构建终端创建能量缓存，每次执行构建或拆除操作时减少5RF能量 可由config配置此功能的开关和每次构建操作、拆除操作时的耗能 (请分析此功能是否必要) 

为构建终端创建材料缓存，按下shift+左键点击 start_building 时将材料全部缓存到内置缓存中 构建结构时使用内置缓存中的材料 材料不足时向ME网络发配缺少材料的合成任务 没有对应样板时取消本次操作并提示没有对应样板 ，material_plaid 组件的显示切换为在缓存中的剩余材料 玩家不可取出或存入缓存内的材料 正在构建操作被暂停时shift+左键点击 pause_operation 将缓存内材料返还到ME网络中并取消本次构建  (请分析此功能是否必要) 