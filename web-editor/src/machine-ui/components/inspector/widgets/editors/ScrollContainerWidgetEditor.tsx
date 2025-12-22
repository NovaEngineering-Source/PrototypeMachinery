import {Group, NumberInput, Stack, Switch} from '@mantine/core';
import {MachineUiScrollContainerWidget} from '../../../../model/ir';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function ScrollContainerWidgetEditor(props: { w: MachineUiScrollContainerWidget }) {
  const { w } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <Stack gap="xs">
      <Group grow>
        <Switch
          label="scrollX"
          checked={Boolean((w as any).scrollX ?? false)}
          onChange={(e) => updateWidget(w.id, { scrollX: e.currentTarget.checked } as any)}
        />
        <Switch
          label="scrollY"
          checked={Boolean((w as any).scrollY ?? true)}
          onChange={(e) => updateWidget(w.id, { scrollY: e.currentTarget.checked } as any)}
        />
      </Group>

      <NumberInput
        label="scrollSpeed"
        description="鼠标滚轮每 tick 滚动像素。"
        value={Number((w as any).scrollSpeed ?? 30)}
        step={1}
        min={1}
        onChange={(v) => updateWidget(w.id, { scrollSpeed: Math.max(1, Math.trunc(Number(v) || 30)) } as any)}
      />

      <Switch
        label="cancelScrollEdge"
        description="到边缘后是否阻止滚动事件向父级传播。"
        checked={Boolean((w as any).cancelScrollEdge ?? true)}
        onChange={(e) => updateWidget(w.id, { cancelScrollEdge: e.currentTarget.checked } as any)}
      />

      <Group grow>
        <Switch
          label="scrollBarOnStartX"
          checked={Boolean((w as any).scrollBarOnStartX ?? false)}
          onChange={(e) => updateWidget(w.id, { scrollBarOnStartX: e.currentTarget.checked } as any)}
        />
        <Switch
          label="scrollBarOnStartY"
          checked={Boolean((w as any).scrollBarOnStartY ?? false)}
          onChange={(e) => updateWidget(w.id, { scrollBarOnStartY: e.currentTarget.checked } as any)}
        />
      </Group>

      <Group grow>
        <NumberInput
          label="scrollBarThicknessX"
          description="<=0 使用主题默认。"
          value={Number((w as any).scrollBarThicknessX ?? -1)}
          step={1}
          onChange={(v) => updateWidget(w.id, { scrollBarThicknessX: Math.trunc(Number(v) || -1) } as any)}
        />
        <NumberInput
          label="scrollBarThicknessY"
          description="<=0 使用主题默认。"
          value={Number((w as any).scrollBarThicknessY ?? -1)}
          step={1}
          onChange={(v) => updateWidget(w.id, { scrollBarThicknessY: Math.trunc(Number(v) || -1) } as any)}
        />
      </Group>

      {/* childrenIds 暂不在此编辑：通过“分组为 ScrollContainer”管理 */}
    </Stack>
  );
}
