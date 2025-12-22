#  空按钮组件
default_button ：尺寸 W:16 H:19 ，贴图分为有阴影和无阴影。按钮贴图与对应状态[
    default.png = 未按下 可在X:2 Y:2 W:9 H:9 设置自定义贴图和文本 ，
    selected.png = 选中 可在X:2 Y:3 W:9 H:9 设置自定义贴图和文本 ，
    pressed.png = 按下 可在X:2 Y:4 W:9 H:9 设置自定义贴图和文本 ，
    disable.png = 不可用 可在X:2 Y:4 W:9 H:9 设置自定义贴图和文本  
],
贴图位置 [
    无阴影./empty_button/normal/*_n.png ，
    有阴影./empty_button/shadow/*_n.png
];

expand_button :自定义尺寸按钮，贴图分为有阴影和无阴影，贴图分为9部分，其中中上中间中下为x轴拉伸，中左中间中右为y轴拉伸，调整尺寸时自动拼接，按钮贴图与对应状态和9部分对应位置 [
default_expand.png = 未按下 ，可在X:2 Y:2 W:7 H:7 设置自定义贴图和文本 按钮尺寸增加时WH同时增加，贴图9部分拆分[
    X:0 Y:0 W:3 H:8 为左上贴图，X:4 Y:0 W:1 H:8 为中上拉伸贴图，X:6 Y:0 W:10 H:8 为右上贴图，
    X:0 Y:9 W:3 H:1 为中左拉伸贴图，X:4 Y:9 W:1 H:1 为中间拉伸贴图，X:6 Y:9 W:10 H:1 为中右拉伸贴图，
    X:0 Y:11 W:3 H:8 为左下贴图，X:4 Y:11 W:1 H:8 为中下拉伸贴图，X:6 Y:11 W:10 H:8 为右下贴图
]，
selected_expand.png = 选中 ，可在X:2 Y:3 W:7 H:7 设置自定义贴图和文本 按钮尺寸增加时WH同时增加，贴图9部分拆分[ 
    X:0 Y:1 W:3 H:8 为左上贴图，X:4 Y:1 W:1 H:8 为中上拉伸贴图，X:6 Y:1 W:10 H:8 为右上贴图，
    X:0 Y:10 W:3 H:1 为中左拉伸贴图，X:4 Y:10 W:1 H:1 为中间拉伸贴图，X:6 Y:10 W:10 H:1 为中右拉伸贴图，
    X:0 Y:12 W:3 H:7 为左下贴图，X:4 Y:12 W:1 H:7 为中下拉伸贴图，X:6 Y:12 W:10 H:7 为右下贴图
]，
pressed_expand.png = 按下 ，可在X:2 Y:4 W:7 H:7 设置自定义贴图和文本 按钮尺寸增加时WH同时增加，贴图9部分拆分[ 
    X:0 Y:2 W:3 H:8 为左上贴图，X:4 Y:2 W:1 H:8 为中上拉伸贴图，X:6 Y:2 W:10 H:8 为右上贴图，
    X:0 Y:11 W:3 H:1 为中左拉伸贴图，X:4 Y:11 W:1 H:1 为中间拉伸贴图，X:6 Y:11 W:10 H:1 为中右拉伸贴图，
    X:0 Y:13 W:3 H:6 为左下贴图，X:4 Y:13 W:1 H:6 为中下拉伸贴图，X:6 Y:13 W:10 H:6 为右下贴图
]，
disable_expand.png = 不可用 ，可在X:2 Y:4 W:7 H:7 设的自定义贴图和文本 按钮尺寸增加时WH同时增加，贴图9部分拆分[
    X:0 Y:1 W:3 H:8 为左上贴图，X:4 Y:1 W:1 H:8 为中上拉伸贴图，X:6 Y:1 W:10 H:8 为右上贴图，
    X:0 Y:10 W:3 H:1 为中左拉伸贴图，X:4 Y:10 W:1 H:1 为中间拉伸贴图，X:6 Y:10 W:10 H:1 为中右拉伸贴图，
    X:0 Y:12 W:3 H:7 为左下贴图，X:4 Y:12 W:1 H:7 为中下拉伸贴图，X:6 Y:12 W:10 H:7 为右下贴图
]，
有阴影和无阴影的贴图位置[
    无阴影./empty_button/normal/*_expand.png
    有阴影./empty_button/shadow/*_expand.png
]];

#  输入框组件

input_box_normal 默认输入框，尺寸 W:56 H:13 ，在X:2 Y:2 W:51 H:8 设置输入框，可选数据类型long string。分为有阴影和无阴影，按钮贴图与对应状态 [
无阴影./input_box/box.png
有阴影./input_box/box_shadow.png
];


input_box_expand 扩展输入框，尺寸 W:5 H:10 组件尺寸增加时WH同时增加。在X:2 Y:2 W:1 H:6 设置输入框 组件尺寸增加时WH同时增加 可选数据类型long string，贴图分为有阴影和无阴影，组件贴图与对应状态 [
    无阴影./input_box/box_expand.png
    有阴影./input_box/box_expand_shadow.png
]，背景贴图9部分拆分，自动拼接，组件尺寸增加时拉伸贴图同步拉伸[
    X:0 Y:0 W:2 H:5 为左上贴图，X:3 Y:0 W:1 H:5 为中上拉伸贴图，X:5 Y:0 W:3 H:5 为右上贴图，
    X:0 Y:6 W:2 H:1 为中左拉伸贴图，X:3 Y:6 W:1 H:1 为中间拉伸贴图，X:5 Y:6 W:3 H:1 为中右拉伸贴图，
    X:0 Y:8 W:2 H:5 为左下贴图，X:3 Y:8 W:1 H:5 为中下拉伸贴图，X:5 Y:8 W:3 H:5 为右下贴图
];


input_box_value_norma 默认数值输入框，尺寸 W:56 H:13 [
在X:1 Y:-2 W:9 H:12 设置减按钮 [
    未按下贴图为./input_box/button_box/as/subtract_default.png ,
    选中贴图为./input_box/button_box/as/subtract_selected.png ,
    按下贴图为./input_box/button_box/as/subtract_pressed.png 
]，
在X:12 Y:2 W:31 H:8 设置输入框 可选数据类型long string，分为有阴影和无阴影 组件贴图与对应状态 [
    无阴影./input_box/button_box/normal/button_input_box.png
    有阴影./input_box/button_box/shadow/button_input_box.png
],
在X:45 Y:-2 W:9 H:12 设置加按钮 [
    未按下贴图为./input_box/button_box/as/add_default.png ,
    选中贴图为./input_box/button_box/as/add_selected.png ,
    按下贴图为./input_box/button_box/as/add_pressed.png
]];


input_box_value_expand 扩展数值输入框，尺寸 W:25 H:12 组件尺寸增加时W同时增加。在X:12 Y:2 W:1 H:8 设置输入框 组件尺寸增加时W同时增加 可选数据类型long string，贴图分为有阴影和无阴影 组件贴图与对应状态[
    无阴影./input_box/button_box/normal/button_input_box.png
    有阴影./input_box/button_box/shadow/button_input_box.png
]，背景贴图3部分拆分[
    X:0 Y:0 W:12 H:13 为左贴图，X:13 Y:0 W:1 H:13 为中间拉伸贴图，X:15 Y:0 W:13 H:133 为右贴图，
],
在X:1 Y:-2 W:9 H:12 设置减按钮 [
    未按下贴图为./input_box/button_box/as/subtract_default.png ,
    选中贴图为./input_box/button_box/as/subtract_selected.png ,
    按下贴图为./input_box/button_box/as/subtract_pressed.png 
]，
在X:15 Y:-2 W:9 H:12 设置加按钮 ,组件W宽度扩展时 加按钮的X同时增加[
    未按下贴图为./input_box/button_box/as/add_default.png ,
    选中贴图为./input_box/button_box/as/add_selected.png ,
    按下贴图为./input_box/button_box/as/add_pressed.png
];


input_box_choice_norma 默认箭头选择框 [
在X:1 Y:-2 W:9 H:12 设置右按钮 [
    未按下贴图为./input_box/button_box/lr/subtract_default.png ,
    选中贴图为./input_box/button_box/lr/subtract_selected.png ,
    按下贴图为./input_box/button_box/lr/subtract_pressed.png 
]，
在X:12 Y:2 W:31 H:8 设置"选择列表" 可设置选项的自定义贴图和文本 点击"选择列表"展开选择列表 列表展开时"选项"根据注册顺序上下排列，贴图分为有阴影和无阴影 组件贴图与对应状态 [
    无阴影./input_box/button_box/normal/button_input_box.png
    有阴影./input_box/button_box/shadow/button_input_box.png
],
在X:45 Y:-2 W:9 H:12 设置左按钮 [
    未按下贴图为./input_box/button_box/lr/add_default.png ,
    选中贴图为./input_box/button_box/lr/add_selected.png ,
    按下贴图为./input_box/button_box/lr/add_pressed.png
]];

input_box_choice_expand 扩展箭头选择框 ，尺寸 W:25 H:12 组件尺寸增加时W同时增加。在X:12 Y:2 W:1 H:8 设置"选择列表" 可设置选项的自定义贴图和文本 点击"选择列表"展开选择列表 列表展开时"选项"根据注册顺序上下排列，组件尺寸增加时W同时增加 可选数据类型long string，贴图分为有阴影和无阴影 组件贴图与对应状态[
    无阴影./input_box/button_box/normal/button_input_box.png
    有阴影./input_box/button_box/shadow/button_input_box.png
]，背景贴图3部分拆分[
    X:0 Y:0 W:12 H:13 为左贴图，X:13 Y:0 W:1 H:13 为中拉伸贴图，X:15 Y:0 W:13 H:13 为右贴图，
],
在X:1 Y:-2 W:9 H:12 设置右按钮 [
    未按下贴图为./input_box/button_box/lr/subtract_default.png ,
    选中贴图为./input_box/button_box/lr/subtract_selected.png ,
    按下贴图为./input_box/button_box/lr/subtract_pressed.png 
]，
在X:15 Y:-2 W:9 H:12 设置左按钮 ,组件H宽度扩展时 左按钮的X同时增加[
    未按下贴图为./input_box/button_box/lr/add_default.png ,
    选中贴图为./input_box/button_box/lr/add_selected.png ,
    按下贴图为./input_box/button_box/lr/add_pressed.png
];

#  滑块组件 -----------------------------------------------------------------------------------------===

slider_s_y ：窄竖滑块 W:10 H:77 纹理设定[
    无阴影./slider/s/normal/ud_base.png
    有阴影./slider/s/shadow/ud_base.png
]，
滑块 X:1 Y:1 W:7 H:8 ,沿Y轴滑动 可滑动Y+66 ，纹理设定[
    未按下时贴图为./slider/s/ud_default.png ，选中贴图为./slider/s/ud_selected.png ，按住贴图为./slider/s/ud_pressed.png
];
# -------------------===
slider_m_y ：宽竖滑块 W:13 H:56 纹理设定[
    无阴影./slider/m/normal/ud_base.png
    有阴影./slider/m/shadow/ud_base.png
]，
滑块 X:1 Y:1 W:10 H:12 ,沿Y轴滑动 可滑动Y+41 ，纹理设定[
    未按下时贴图为./slider/m/ud_default.png ，选中贴图为./slider/m/ud_selected.png ，按住贴图为./slider/m/ud_pressed.png
];
# --------------------------------------------------------------------===[扩展竖滑块]
slider_s_y ：扩展窄竖滑块 W:10 H:13 组件尺寸增加时H同时增加，纹理设定[
    无阴影./slider/s/normal/ud_base_expand.png
    有阴影./slider/s/shadow/ud_base_expand.png
]，
背景贴图3部分拆分[
    X:0 Y:0 W:10 H:7 为上贴图，X:0 Y:8 W:10 H:1 为中拉伸贴图，X:0 Y:10 W:10 H:6 为下贴图，
],
滑块 X:1 Y:1 W:7 H:8 ,沿Y轴滑动 可滑动Y+3 组件H增加时可滑动距离同时增加，纹理设定[
    未按下时贴图为./slider/s/ud_default.png ，选中贴图为./slider/s/ud_selected.png ，按住贴图为./slider/s/ud_pressed.png
];
# -------------------===
slider_m_y ：扩展宽竖滑块 W:13 H:16 组件尺寸增加时H同时增加，纹理设定[
    无阴影./slider/m/normal/ud_base_expand.png
    有阴影./slider/m/shadow/ud_base_expand.png
]，
背景贴图3部分拆分[
    X:0 Y:0 W:13 H:7 为上贴图，X:0 Y:8 W:13 H:1 为中拉伸贴图，X:0 Y:10 W:13 H:8 为下贴图，
],
滑块 X:1 Y:1 W:10 H:12 ,沿Y轴滑动 可滑动Y+3 组件H增加时可滑动距离同时增加，纹理设定[
    未按下时贴图为./slider/m/ud_default.png ，选中贴图为./slider/m/ud_selected.png ，按住贴图为./slider/m/ud_pressed.png
];

# -----------------------------------------------------------------------------------------===[横滑块]
slider_s_x ：窄横滑块 W:56 H:10 ，纹理设定[
    无阴影./slider/s/normal/rl_base.png
    有阴影./slider/s/shadow/rl_base.png
]，
滑块 X:1 Y:1 W:7 H:8 ,沿X轴滑动 可滑动X+46 ，纹理设定[
    未按下时贴图为./slider/s/rl_default.png ，选中贴图为./slider/s/rl_selected.png ，按住贴图为./slider/s/rl_pressed.png
];
# -------------------===
slider_m_x ：宽横滑块 W:56 H:13 ，纹理设定[
    无阴影./slider/m/normal/rl_base.png
    有阴影./slider/m/shadow/rl_base.png
]，
滑块 X:1 Y:1 W:11 H:11 ,沿X轴滑动 可滑动X+43 ，纹理设定[
    未按下时贴图为./slider/m/rl_default.png ，选中贴图为./slider/m/rl_selected.png ，按住贴图为./slider/m/rl_pressed.png
];

# --------------------------------------------===[扩展横滑块]
slider_s_x ：扩展窄横滑块 W:12 H:10 组件尺寸增加时W同时增加，，纹理设定[
    无阴影./slider/s/normal/rl_base_expand.png
    有阴影./slider/s/shadow/rl_base_expand.png
]，
背景贴图3部分拆分[
    X:0 Y:0 W:5 H:10 为左贴图，X:6 Y:0 W:1 H:10 为中拉伸贴图，X:8 Y:0 W:6 H:10 为右贴图，
],
滑块 X:1 Y:0 W:7 H:8 ,沿X轴滑动 可滑动X+2 组件H增加时可滑动距离同时增加，纹理设定[
    未按下时贴图为./slider/s/rl_default.png ，选中贴图为./slider/s/rl_selected.png ，按住贴图为./slider/s/rl_pressed.png
];
# -------------------===
slider_m_x ：扩展宽横滑块 W:15 H: 13 组件尺寸增加时W同时增加，，纹理设定[
    无阴影./slider/s/normal/rl_base_expand.png
    有阴影./slider/s/shadow/rl_base_expand.png
]，
背景贴图3部分拆分[
    X:0 Y:0 W:6 H:13 为左贴图，X:7 Y:0 W:1 H:13 为中拉伸贴图，X:9 Y:0 W:8 H:13 为右贴图，
],
滑块 X:1 Y:0 W:11 H:11 ,沿X轴滑动 可滑动X+2 组件H增加时可滑动距离同时增加，纹理设定[
    未按下时贴图为./slider/s/rl_default.png ，选中贴图为./slider/s/rl_selected.png ，按住贴图为./slider/s/rl_pressed.png
];

# -----------------------------------------------------------------------------------------===

# 开关组件

switch_states ：开关组件 W:28 H:14 [
    关闭状态 ./switch/normal/off.png ，选择时为 ./switch/normal/off_selected.png ，
    开启状态 ./switch/normal/on.png ，选择时为 ./switch/normal/on_selected.png 
]


