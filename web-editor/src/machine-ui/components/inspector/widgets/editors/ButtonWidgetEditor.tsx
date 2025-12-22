import {Autocomplete, Stack, TextInput} from '@mantine/core';
import {MachineUiButtonWidget} from '../../../../model/ir';
import {guiStatesButtonSkins} from '../../../../model/guiStatesSkins';
import {useMachineUiStore} from '../../../../store/machineUiStore';

export function ButtonWidgetEditor(props: { w: MachineUiButtonWidget }) {
  const { w } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  return (
    <Stack gap="xs">
      <Autocomplete
        label="skin（可选）"
        description="运行时皮肤 id（例如 gui_states/expand_button/normal）。设置后会由模组端按 skin 渲染。"
        value={String((w as any).skin ?? '')}
        data={[...guiStatesButtonSkins] as unknown as string[]}
        onChange={(v) => updateWidget(w.id, { skin: v.trim() || undefined } as any)}
      />

      <TextInput
        label="文本（可选）"
        value={String((w as any).text ?? '')}
        onChange={(e) => updateWidget(w.id, { text: e.currentTarget.value || undefined } as any)}
      />

      <TextInput
        label="actionKey（可选）"
        description="运行时按钮点击事件 key（UIActionRegistry / PacketMachineAction）。"
        value={String((w as any).actionKey ?? '')}
        onChange={(e) => updateWidget(w.id, { actionKey: e.currentTarget.value.trim() || undefined } as any)}
      />
    </Stack>
  );
}
