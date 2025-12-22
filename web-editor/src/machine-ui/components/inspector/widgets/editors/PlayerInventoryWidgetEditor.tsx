import {Stack, Text} from '@mantine/core';
import {MachineUiPlayerInventoryWidget} from '../../../../model/ir';

export function PlayerInventoryWidgetEditor(_props: { w: MachineUiPlayerInventoryWidget }) {
  return (
    <Stack gap="xs">
      <Text size="sm" c="dimmed">
        Player Inventory 组件当前没有额外可编辑字段（除了位置/大小、tabId、visibleIf/enabledIf）。
      </Text>
    </Stack>
  );
}
