import {Autocomplete, Button, Group, Stack, Text} from '@mantine/core';
import {MachineUiImageWidget} from '../../../../model/ir';
import {useMachineUiStore} from '../../../../store/machineUiStore';
import {usePmTextureSize} from '../../utils';

export function ImageWidgetEditor(props: { w: MachineUiImageWidget; guiTextureCandidates: string[] }) {
  const { w, guiTextureCandidates } = props;
  const updateWidget = useMachineUiStore((s) => s.updateWidget);

  const texPath = String((w as any).texturePath ?? '').trim() || undefined;
  const imageTexSize = usePmTextureSize(texPath);

  return (
    <Stack gap="xs">
      <Autocomplete
        label="贴图"
        description="贴图路径（通常是 gui/*.png）。运行时会转换为资源定位参数。"
        value={String((w as any).texturePath ?? '')}
        data={guiTextureCandidates}
        onChange={(v) => updateWidget(w.id, { texturePath: v.trim() || undefined } as any)}
      />

      <Group justify="space-between" wrap="nowrap">
        <Text size="xs" c="dimmed">
          贴图尺寸：{imageTexSize ? `${imageTexSize.w}×${imageTexSize.h}` : '（未加载/无）'}
        </Text>
        <Button
          size="xs"
          variant="light"
          disabled={!imageTexSize}
          onClick={() => {
            if (!imageTexSize) return;
            updateWidget(w.id, { w: imageTexSize.w, h: imageTexSize.h } as any);
          }}
        >
          按贴图适配大小
        </Button>
      </Group>
    </Stack>
  );
}
