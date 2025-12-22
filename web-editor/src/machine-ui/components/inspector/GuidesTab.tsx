import {ActionIcon, Group, ScrollArea, Stack, Switch, Text} from '@mantine/core';
import {IconTrash} from '@tabler/icons-react';
import {useMemo} from 'react';
import {useMachineUiStore} from '../../store/machineUiStore';

export function GuidesTab() {
  const doc = useMachineUiStore((s) => s.doc);
  const selectedGuideId = useMachineUiStore((s) => s.selection.selectedGuideId);
  const setSelectedGuideId = useMachineUiStore((s) => s.setSelectedGuideId);
  const updateGuide = useMachineUiStore((s) => s.updateGuide);
  const removeGuide = useMachineUiStore((s) => s.removeGuide);

  const guides = useMemo(() => doc.guides ?? [], [doc.guides]);

  return (
    <ScrollArea h="100%" type="auto" offsetScrollbars>
      <Stack gap="sm" pb="md">
        <Text size="sm" c="dimmed">
          对齐参考线（编辑器专用，不参与导出）。
        </Text>

        {guides.length === 0 ? <Text size="sm" c="dimmed">暂无 Guides</Text> : null}

        {guides.length > 0 ? (
          <Stack gap={6}>
            {guides.map((g) => {
              const selected = g.id === selectedGuideId;
              return (
                <Group
                  key={g.id}
                  gap="xs"
                  justify="space-between"
                  wrap="nowrap"
                  style={{
                    padding: '6px 8px',
                    borderRadius: 8,
                    background: selected ? 'rgba(255,255,255,0.06)' : 'transparent',
                    border: selected ? '1px solid rgba(255,255,255,0.14)' : '1px solid transparent',
                    cursor: 'pointer',
                  }}
                  onClick={() => setSelectedGuideId(g.id)}
                >
                  <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                    <Text size="sm" fw={selected ? 600 : 400} lineClamp={1}>
                      {g.label ?? g.id}
                    </Text>
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      x={g.x}, y={g.y}, w={g.w}, h={g.h}
                    </Text>
                  </Stack>
                  <Group gap={4}>
                    <Switch
                      size="sm"
                      checked={Boolean(g.locked)}
                      label="锁"
                      onChange={(e) => updateGuide(g.id, { locked: e.currentTarget.checked } as any)}
                      onClick={(e) => e.stopPropagation()}
                    />
                    <ActionIcon
                      variant={selected ? 'light' : 'subtle'}
                      color="red"
                      title="删除"
                      onClick={(e) => {
                        e.stopPropagation();
                        removeGuide(g.id);
                      }}
                    >
                      <IconTrash size={18} />
                    </ActionIcon>
                  </Group>
                </Group>
              );
            })}
          </Stack>
        ) : null}
      </Stack>
    </ScrollArea>
  );
}
