# ===-----------------------------------------------------------------------------------------------------------===顶部
# 机器前缀与名称 machine_prefix_name
在X:8 ,Y:11 ,W:155 ,H:18 设置 machine_prefix_name 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/top_machine_prefix_name_base 。
    在X:11 ,Y:17 ,W:29 ,H:10 设置 machine_prefix 组件，内容为前缀本地化内容，长度超过范围则滚动显示。
    在X:43 ,Y:17 ,W:77 ,H:10 设置 machine_name 组件，内容为机器本地化名称内容，长度超过范围则滚动显示。

按下"down_button"组件的"component_switch"按钮时此组件Y轴滑动-16px，滑动到选定范围外的部分遮罩隐藏，未按下时为默认状态。

# 复位预览 preview_reset
在X:124 ,Y:11 ,W:17 ,H:17 设置 preview_reset 组件,背景贴图为 prototypemachinery:gui/gui_structure_preview/top_button_base_preview_reset 。
    在X:3 ,Y:15 ，W:13 ,H:5 设置 button_preview_reset 按钮，触发区域X:0 ,Y:0 ,W:17 ,H:19，默认状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_resetting_default 选中状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_resetting_selected 按下状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_resetting_press 。
    按下时重置预览的缩放和视角，按下松开后光标还在触发区域时触发，此按钮为点动按钮，默认未按下。

按下"down_button"组件的"component_switch"按钮时此组件Y轴滑动-15px，滑动到选定范围外的部分遮罩隐藏，未按下时为默认状态。

# 结构完整性开关 structural_forming
在X:11 ,Y:124 ，W:17 ,H:17 设置 structural_forming 组件,背景贴图为 prototypemachinery:gui/gui_structure_preview/top_button_base_structural_forming_off, 按下 button_structural_forming 按钮时切换到 prototypemachinery:gui/gui_structure_preview/top_button_base_structural_forming_on 。
    在X:3 ,Y:15 ，W:13 ,H:5  设置 button_structural_forming 按钮，触发区域X:0 ,Y:0 ,W:17 ,H:19，默认状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_locking_default 选中状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_locking_selected 按下状态使用 prototypemachinery:gui/gui_structure_preview/top_button/self_locking_pressed 。
    未按下时预览结构不成形，按下时预览结构成型，按下松开后光标还在触发区域时触发，此按钮为自锁按钮，默认未按下。

按下"down_button"组件的"component_switch"按钮时此组件Y轴滑动-15px，滑动到选定范围外的部分遮罩隐藏，未按下时为默认状态。

# 详情信息 structural_information
在X:11 ,Y:124 ，W:17 ,H:17 设置 structural_information 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/top_button_base_structural_forming_off 。
    此组件触发区域X:0 ,Y:0 ,W:17 ,H:18 悬停在触发区域时显示此机械详情信息。

按下"down_button"组件的"component_switch"按钮时此组件Y轴滑动-15px，滑动到选定范围外的部分遮罩隐藏，未按下时为默认状态。

# ===-----------------------------------------------------------------------------------------------------------===中部
# 替换方块列表  replace_preview
在X:9 ,Y:31 ，W:18 ,H:181 设置 replace_preview 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/left_replace_preview 。
    在X:10 ,Y:32 ,W:16 ,H:16 设置 replace_target 组件，显示在预览界面里选中的方块。
    在X:10 ,Y:49 ,W:16 ,H:162 设置 replacement_list 组件，显示选中方块的可替换方块，光标在区域内拖动和滚轮皆可滑动替换列表，替换列表沿Y轴移动。
选中预览界面中的方块时，此组件从X:7 ,Y:31 ，W:1 ,H:181 区域向右滑出，此时点击预览界面的空白区域缩回。

# 材料预览UI  material_preview_ui
在X:29 ,Y:31 ，W:134 ,H:163 设置 material_preview_ui 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/base_material_preview_ui ,光标在X:124 ,Y:4 ,W:8 ,H:157 区域按住移动时此组件跟随光标移动，移动到JEI标签页时JEI内容需要根据此组件的尺寸进行避让。
此UI的四个角和四条边皆可按住缩放组件，四个角有额外5*5px的位置可按住缩放 四角所在的位置为中点，X轴缩放时18px为一个步进，最小缩放为W:44 ,H:24
    在X:4 ,Y:4 ,W:111 ,H:160 设置 material_preview 组件，此组件为结构材料预览区域。
        选择 prototypemachinery:gui/states 贴图中的X:74 ,Y:11 ,W:18 ,H:18 为预览材料的格子背景，预览格子在区域内左对齐排列，预览材料增加时格子背景，组件X轴缩放每增加18px，横向增加一个预览格子，横向空间填满时在下一行排列，预览材料显示在格子的X:2 ,Y:2 ,W:16 ,H:16内。
        点击预览范围内的方块时，在 replace_preview 中查看选中方块的可替换列表。光标在区域内上下拖动、滚轮滚动时可上下移动材料预览列表，移动时 material_preview_slider 滑块同步移动。

    在X:114 ,Y:5 ,W:7 ,H:155 设置 material_preview_sliding_groove 组件，此组件为 material_preview 区域的滑条。
        在X:1 ,Y:1 ,W:7 ,H:12 设置 material_preview_slider 滑块，滑块沿Y轴移动，光标在区域内拖动滑块和滚轮皆可滑动材料预览列表，
按下 button_material_preview_ui 时此组件从中间弹出，按钮恢复默认状态后向中间缩回。

# 层预览滑条  layer_selection
在X:165 ,Y:33 ，W:10 ,H:158 设置 layer_selection 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/right_layer_selection 。
    在X:3 ,Y:11 ,W:7 ,H:137 设置 layer_selection_sliding_groove 滑条
        在X:1 ,Y:1 ,W:7 ,H:12 设置 layer_selection_slider 滑块，滑块沿Y轴移动，光标在区域内拖动滑块和滚轮皆可滑动替换列表，替换列表沿Y轴移动，滑块滑动时切换预览层。
此组件默认状态向X轴移动8px，按下 layer_preview 按钮切换为层预览状态时沿X轴滑动-8px，layer_preview 恢复默认状态后恢复默认状态。
    


# ===-----------------------------------------------------------------------------------------------------------===底部
# 底部组件 down_button
在X:100 ,Y:196 ,W:78 ,H:16 设置 down_button 组件

    组件隐藏按钮 component_switch
    在X:67 ,Y:2 ，W:12 ,H:14 设置 component_switch 按钮，默认状态使用 prototypemachinery:gui/gui_structure_preview/down_button_default/component_switch_default ，选中状态使用 prototypemachinery:gui/gui_structure_preview/down_button_selected/component_switch_selected ，按下状态使用 prototypemachinery:gui/gui_structure_preview/down_button_pressed/component_switch_pressed 。
    未按下时不启用，按下松开后光标还在触发区域时触发，默认未按下。

    底部按钮组 down_button_base
    在X:1 ,Y:4 ，W:62 ,H:13 设置 down_button_base 组件，背景贴图为 prototypemachinery:gui/gui_structure_preview/down_button_base_on 。
    按钮组指示灯 button_base_led
    在X:3 ,Y:7 ,W:1 ,H:7 设置 button_base_led 贴图组件，component_switch 未按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_base_off-led 按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_base_on-led

        放置投影按钮 place_projection
        在X:6 ,Y:-4 ,W:12 ,H:14 设置 place_projection 按钮，默认背景贴图为 prototypemachinery:gui/gui_structure_preview/down_button_default/down_button_place_projection_default ，选中时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_selected/place_projection_selected ，按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_pressed/place_projection_pressed 。
        按下时将预览投影放置到世界，按下松开后光标还在触发区域时触发，此按钮为点动按钮，默认未按下。

        材料预览UI按钮 button_material_preview_ui
        在X:19 ,Y:-4 ,W:12 ,H:14 设置 button_material_preview_ui 按钮，默认背景贴图为 prototypemachinery:gui/gui_structure_preview/down_button_default/down_button_material_preview_ui_default ，选中时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_selected/material_preview_ui_selected ，按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_pressed/material_preview_ui_pressed 。
        按下时启用 material_preview_ui 组件，默认时关闭，按下松开后光标还在触发区域时触发，此按钮为自锁按钮，默认未按下。

        替换方块轮换开关 replace_block
        在X:32 ,Y:-4 ,W:12 ,H:14 设置 replace_block 按钮，默认背景贴图为 prototypemachinery:gui/gui_structure_preview/down_button_default/replace_block_default ，选中时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_selected/replace_block_selected ，按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_pressed/replace_block_pressed 。
        按下时替换方块轮换锁定，默认时恢复，按下松开后光标还在触发区域时触发，此按钮为自锁按钮，默认未按下。

        预览模式开关 layer_preview
        在X:45 ,Y:-4 ,W:12 ,H:14 设置 layer_preview 按钮，默认背景贴图为 prototypemachinery:gui/gui_structure_preview/down_button_default/layer_preview_default ，选中时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_selected/layer_preview_selected ，按下时贴图为 prototypemachinery:gui/gui_structure_preview/down_button_pressed/layer_preview_pressed 。
        按下时启用 layer_selection 组件，预览切换为层预览，未按下时预览和按钮为默认状态，按下松开后光标还在触发区域时触发，此按钮为自锁按钮，默认未按下。
        
    按钮component_switch按下时组件内按钮禁用，X轴移动57px，移动到选定范围外的部分遮罩隐藏，未按下时为默认状态。