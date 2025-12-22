import {Box, Code, Divider, Group, Stack, Text} from '@mantine/core';
import {useEffect, useState} from 'react';
import {fetchPmAssetIndex} from '../editor/assets/pmAssets';
import {MachineUiCanvasStage} from '../machine-ui/components/MachineUiCanvasStage';
import {MachineUiInspector} from '../machine-ui/components/MachineUiInspector';
import {MachineUiToolbar} from '../machine-ui/components/MachineUiToolbar';

export function MachineUiPage() {
  const [pmPaths, setPmPaths] = useState<string[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchPmAssetIndex()
      .then((idx) => {
        if (cancelled) return;
        setPmPaths(idx.paths);
      })
      .catch(() => {
        if (cancelled) return;
        setPmPaths([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Group
      align="stretch"
      gap="md"
      style={{
        height: '100%',
        overflow: 'hidden',
        minHeight: 0,
      }}
    >
      {/* Left: canvas */}
      <Box style={{ flex: 1, minWidth: 520, minHeight: 0, overflow: 'hidden' }}>
        <Stack h="100%" gap="xs">
          <Group justify="space-between" wrap="nowrap">
            <Group gap="xs">
              <Text size="sm" c="dimmed">
                Machine UI 编辑器（骨架）
              </Text>
              <Text size="sm" c="dimmed">
                路由：<Code>/#/machine-ui</Code>
              </Text>
            </Group>

            <Group gap="xs">
              <MachineUiToolbar />
            </Group>
          </Group>

          <Divider />

          <MachineUiCanvasStage />

          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              说明：支持 tabs 预览、widgets/guides 编辑、以及 JSON/ZenScript 导入导出（ZenScript 仍是草案）。
            </Text>
          </Group>
        </Stack>
      </Box>

      {/* Right: inspector */}
      <Box style={{ width: 360, height: '100%', minHeight: 0, overflow: 'hidden' }}>
        <MachineUiInspector pmPaths={pmPaths} />
      </Box>
    </Group>
  );
}
