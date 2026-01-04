# 扫描终端

[背景： ./gui_scanner_instrument/base],

[结构预览： X:10 Y:12 W:171 H:203],

lang_id_setup ： 本地化与ID X:197 Y:14  [
    [lang_setup ： 本地化名，使用 input_box_expand 扩展输入框 X:18 Y:3 W:153 H:13],
    [id_setup ： 结构ID，使用 input_box_expand 扩展输入框 X:18 Y:17 W:153 H:13]
]

expanded_setup ： 扩展结构 X:197 Y:50  [
    [expanded_o_f ： 是否扩展结构 ，使用 choose_switch 选择组件 X:3 Y:18],

    [expanded_quantity ： 设定扩展数量 ，使用 input_box_expand 扩展输入框 X:22 Y:2 W:67 H:13 ,
    expanded_quantity_preview ： 预览设定扩展数量 ，使用 slider_m_x 宽横滑块 X:104 Y:3 W:67 H:13],
    [expanded_spacing ： 设定扩展间距 ，使用 input_box_expand 扩展输入框 X:33 Y:17 W:67 H:13 ,
    expanded_spacing_preview ： 预览设定扩展间距 ，使用 slider_m_x 宽横滑块 X:104 Y:17 W:67 H:13]
]

structure_setup ： 结构设置 X:197 Y:86  [
    [Substructure_o_f ： 是否为子结构 ，使用 switch_states_ss 小开关组件 X:18 Y:2],
    [match_nbt_o_f ： 是否匹配NBT ，使用 switch_states_ss 小开关组件 X:66 Y:2],
    [mirror_o_f ： 是否允许镜像结构 ，使用 switch_states_ss 小开关组件 X:109 Y:2]
]


origin_set_size_preview ： 结构原点设定与结构尺寸预览 X:197 Y:107  [
    [origin_x ： 设定原点方块的X轴位置 在预览视图中选中的方块自动填入 ，使用 input_box_expand 扩展输入框 X:15 Y:3 W:69 H:13 ,
    origin_y ： 设定原点方块的Y轴位置 在预览视图中选中的方块自动填入 ，使用 input_box_expand 扩展输入框 X:15 Y:17 W:69 H:13 ,
    origin_z ： 设定原点方块的Z轴位置 在预览视图中选中的方块自动填入 ，使用 input_box_expand 扩展输入框 X:15 Y:31 W:69 H:13 ,
    ],

    [重置原点位置 ，使用 rese_origin_button 按钮类型为点动按钮 X:3 Y:45 ,
    设定原点位置 ，使用 set_origin_button 按钮类型为点动按钮 X:45 Y:45],

    [size_preview ： 显示结构尺寸大小 X:17 Y:67 W:64 H:8 ，格式为"X:---- Y:---- Z:----"]
]

preview_xyz_r ： 预览结构方向 X:287 Y:129  [
    [使用 reset_orientation_button 点动按钮 X:19 Y:3],
    [mirror_preview_o_f ： 镜像结构预览 ，使用 choose_switch 选择组件 X:69 Y:4],
    [preview_xyz ： 预览方向，单选自锁按钮，按钮组 X:3 Y:20[
        使用 y_add_button 按钮，设为顶 X:12 Y:0
        使用 y_sub_button 按钮，设为底 X:12 Y:24

        使用 x_add_button 按钮，设为东 X:0 Y:12
        使用 x_sub_button 按钮，设为西 X:24 Y:12
        
        使用 z_add_button 按钮，设为北 X:12 Y:12
        使用 z_sub_button 按钮，设为南 X:0 Y:24
    ]],
    [preview_r ： 预览方向的旋转方向，单选自锁按钮，按钮组 X:46 Y:20[
        使用 rotate_y_add_button 按钮，旋转到0度 X:12 Y:0
        使用 rotate_y_sub_button 按钮，旋转到90度 X:12 Y:24

        使用 rotate_x_sub_button 按钮，旋转到180度 X:0 Y:12
        使用 rotate_x_add_button 按钮，旋转到270度 X:24 Y:12
    ]]
]

output_setup ： 导出设置 X:197 Y:191  [
    [使用 delete_button 清空预览界面 ，点动按钮 X:0 Y:0],
    [使用 reset_button 是否匹配NBT ，点动按钮 X:22 Y:0],
    [使用 output_button 是否允许镜像结构 ，点动按钮 X:44 Y:0000]
]


# 按钮

delete_button ： 清空 按钮，尺寸 W:21 H:22 贴图设定[
    [默认： ./gui_scanner_instrument/delete_default.png],
    [选中： ./gui_scanner_instrument/delete_selected.png],
    [按下： ./gui_scanner_instrument/delete_pressed.png]
]

reset_button ： 初始化 按钮，尺寸 W:21 H:22 贴图设定[
    [默认： ./gui_scanner_instrument/reset_default.png],
    [选中： ./gui_scanner_instrument/reset_selected.png],
    [按下： ./gui_scanner_instrument/reset_pressed.png]
]

output_button ： 导出 按钮，尺寸 W:130 H:22 贴图设定[
    [默认： ./gui_scanner_instrument/output_default.png],
    [选中： ./gui_scanner_instrument/output_selected.png],
    [按下： ./gui_scanner_instrument/output_pressed.png]
]

rese_origin_button ： 原点复位 按钮，尺寸 W:39 H:13 贴图设定[
    [默认： ./gui_scanner_instrument/reset_i_default.png],
    [选中： ./gui_scanner_instrument/reset_i_selected.png],
    [按下： ./gui_scanner_instrument/reset_i_pressed.png]
]

set_origin_button ： 设定原点 按钮，尺寸 W:39 H:13 贴图设定[
    [默认： ./gui_scanner_instrument/set_default.png],
    [选中： ./gui_scanner_instrument/set_selected.png],
    [按下： ./gui_scanner_instrument/set_pressed.png]
]

reset_orientation_button ： 初始化预览方向 按钮，尺寸 W:30 H:13 贴图设定[
    [默认： ./gui_scanner_instrument/reset_ii_default.png],
    [选中： ./gui_scanner_instrument/reset_ii_selected.png],
    [按下： ./gui_scanner_instrument/reset_ii_pressed.png]
]

# 预览结构方向按钮

x_add_button ： 设为东 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

x_sub_button ： 设为西 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

y_add_button ： 设为顶 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

y_sub_button ： 设为底 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

z_add_button ： 设为北 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

z_sub_button ： 设为南 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/default],
    [选中： ./gui_scanner_instrument/selected],
    [按下： ./gui_scanner_instrument/pressed]
]

# 预览方向的旋转方向按钮

rotate_x_add_button ： 旋转0度 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/rotate/default],
    [选中： ./gui_scanner_instrument/rotate/selected],
    [按下： ./gui_scanner_instrument/rotate/pressed]
]

rotate_x_sub_button ： 旋转90度 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/rotate/default],
    [选中： ./gui_scanner_instrument/rotate/selected],
    [按下： ./gui_scanner_instrument/rotate/pressed]
]

rotate_y_add_button ： 旋转180度 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/rotate/default],
    [选中： ./gui_scanner_instrument/rotate/selected],
    [按下： ./gui_scanner_instrument/rotate/pressed]
]

rotate_y_sub_button ： 旋转270度 按钮，尺寸 W:11 H:11 贴图设定[
    [默认： ./gui_scanner_instrument/rotate/default],
    [选中： ./gui_scanner_instrument/rotate/selected],
    [按下： ./gui_scanner_instrument/rotate/pressed]
]





# 以下组件若是没有则注册

switch_states ：开关组件 W:28 H:14 [
    [关闭状态 ./gui_states/switch/normal/off.png 选择时为 ./gui_states/switch/normal/off_selected.png],
    [开启状态 ./gui_states/switch/normal/on.png 选择时为 ./gui_states/switch/normal/on_selected.png] 
]

switch_states_s ：有阴影开关组件 W:28 H:14 [
    [关闭状态 ./gui_states/switch/shadow/off.png 选择时为 ./gui_states/switch/shadow/off_selected.png],
    [开启状态 ./gui_states/switch/shadow/on.png 选择时为 ./gui_states/switch/shadow/on_selected.png] 
]

switch_states_ss ：小开关组件 W:20 H:13 [
    [关闭状态 ./gui_states/switch/s_normal/off.png 选择时为 ./gui_states/switch/s_normal/off_selected.png],
    [开启状态 ./gui_states/switch/s_normal/on.png 选择时为 ./gui_states/switch/s_normal/on_selected.png] 
]

switch_states_sn ：有阴影小开关组件 W:20 H:13 [
    [关闭状态 ./gui_states/switch/s_shadow/off.png 选择时为 ./gui_states/switch/s_shadow/off_selected.png],
    [开启状态 ./gui_states/switch/s_shadow/on.png 选择时为 ./gui_states/switch/s_shadow/on_selected.png] 
]

choose_switch ：选择组件 W:11 H:11 [
    [关闭状态 ./gui_states/choose_switch/normal/off.png 光标选中 时为 ./gui_states/choose_switch/normal/off_selected.png],
    [开启状态 ./gui_states/choose_switch/normal/on.png 光标选中 时为 ./gui_states/choose_switch/normal/on_selected.png] 
]


