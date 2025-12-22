import {ActionIcon, Autocomplete, Button, Divider, Group, ScrollArea, Stack, Switch, Text,} from '@mantine/core';
import {IconFocus2, IconTrash} from '@tabler/icons-react';
import {useMemo, useState} from 'react';
import {pmTextureUrl} from '../../../editor/assets/pmAssets';
import {
    MachineUiButtonWidget,
    MachineUiImageWidget,
    MachineUiPanelWidget,
    MachineUiPlayerInventoryWidget,
    MachineUiProgressBarWidget,
    MachineUiScrollContainerWidget,
    MachineUiSliderWidget,
    MachineUiSlotGridWidget,
    MachineUiTextFieldWidget,
    MachineUiTextWidget,
    MachineUiToggleWidget,
    MachineUiWidget,
} from '../../model/ir';
import {
    resolveGuiStatesButtonPreview,
    resolveGuiStatesSliderPreview,
    resolveGuiStatesTextFieldPreview,
    resolveGuiStatesTogglePreview,
} from '../../model/guiStatesPreview';
import {useMachineUiStore} from '../../store/machineUiStore';
import {CommonWidgetFields} from './widgets/CommonWidgetFields';
import {ButtonWidgetEditor} from './widgets/editors/ButtonWidgetEditor';
import {ImageWidgetEditor} from './widgets/editors/ImageWidgetEditor';
import {PlayerInventoryWidgetEditor} from './widgets/editors/PlayerInventoryWidgetEditor';
import {ProgressWidgetEditor} from './widgets/editors/ProgressWidgetEditor';
import {ScrollContainerWidgetEditor} from './widgets/editors/ScrollContainerWidgetEditor';
import {SliderWidgetEditor} from './widgets/editors/SliderWidgetEditor';
import {SlotGridWidgetEditor} from './widgets/editors/SlotGridWidgetEditor';
import {TextFieldWidgetEditor} from './widgets/editors/TextFieldWidgetEditor';
import {TextWidgetEditor} from './widgets/editors/TextWidgetEditor';
import {ToggleWidgetEditor} from './widgets/editors/ToggleWidgetEditor';

export type WidgetsTabProps = {
  pmPaths: string[] | null;
};

export function WidgetsTab({ pmPaths }: WidgetsTabProps) {
  const doc = useMachineUiStore((s) => s.doc);

  const selectedWidgetId = useMachineUiStore((s) => s.selection.selectedWidgetId);
  const selectedWidgetIds = useMachineUiStore((s) => s.selection.selectedWidgetIds);
  const setSelectedWidgetId = useMachineUiStore((s) => s.setSelectedWidgetId);
  const setSelectedWidgetIds = useMachineUiStore((s) => s.setSelectedWidgetIds);
  const toggleSelectedWidgetId = useMachineUiStore((s) => s.toggleSelectedWidgetId);
  const deleteSelected = useMachineUiStore((s) => s.deleteSelected);

  const addTextWidget = useMachineUiStore((s) => s.addTextWidget);
  const addSlotGridWidget = useMachineUiStore((s) => s.addSlotGridWidget);
  const addProgressBarWidget = useMachineUiStore((s) => s.addProgressBarWidget);
  const addImageWidget = useMachineUiStore((s) => s.addImageWidget);
  const addButtonWidget = useMachineUiStore((s) => s.addButtonWidget);
  const addToggleWidget = useMachineUiStore((s) => s.addToggleWidget);
  const addSliderWidget = useMachineUiStore((s) => s.addSliderWidget);
  const addTextFieldWidget = useMachineUiStore((s) => s.addTextFieldWidget);
  const addPlayerInventoryWidget = useMachineUiStore((s) => s.addPlayerInventoryWidget);
  const addScrollContainerWidget = useMachineUiStore((s) => s.addScrollContainerWidget);

  const removeWidget = useMachineUiStore((s) => s.removeWidget);
  const alignSelectedWidget = useMachineUiStore((s) => s.alignSelectedWidget);
  const alignSelection = useMachineUiStore((s) => s.alignSelection);
  const distributeSelection = useMachineUiStore((s) => s.distributeSelection);

  const groupSelectionIntoPanel = useMachineUiStore((s) => s.groupSelectionIntoPanel);
  const ungroupSelectedPanel = useMachineUiStore((s) => s.ungroupSelectedPanel);

  const groupSelectionIntoScrollContainer = useMachineUiStore((s) => s.groupSelectionIntoScrollContainer);
  const ungroupSelectedScrollContainer = useMachineUiStore((s) => s.ungroupSelectedScrollContainer);

  const widgets = useMemo(() => (doc.widgets ?? []) as MachineUiWidget[], [doc.widgets]);

  const selectedWidgetCount = (selectedWidgetIds ?? []).length;
  const isMultiSelected = selectedWidgetCount > 1;
  const isSingleSelected = selectedWidgetCount === 1;

  const selectedWidget = useMemo(() => {
    if (!selectedWidgetId) return undefined;
    if (!isSingleSelected) return undefined;
    return widgets.find((x) => x?.id === selectedWidgetId);
  }, [widgets, selectedWidgetId, isSingleSelected]);

  const canUngroupPanel = Boolean(selectedWidget && (selectedWidget as any).type === 'panel');
  const canUngroupScroll = Boolean(selectedWidget && (selectedWidget as any).type === 'scroll_container');

  const [batchTabId, setBatchTabId] = useState<string>('');
  const [batchLocked, setBatchLocked] = useState<boolean>(false);

  const guiTextureCandidates = useMemo(() => {
    const base = ['gui/gui_controller_a.png', 'gui/gui_controller_b.png'];
    if (!pmPaths || pmPaths.length === 0) return base;
    return pmPaths.filter((p) => p.startsWith('gui/') && p.endsWith('.png')).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const slotTextureCandidates = useMemo(() => {
    const base = ['gui/slot.png'];
    if (!pmPaths || pmPaths.length === 0) return base;
    const extra = pmPaths.filter((p) => p.endsWith('.png') && (p.includes('slot') || p.includes('/slot_')));
    return Array.from(new Set([...base, ...extra])).sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const progressBaseCandidates = useMemo(() => {
    if (!pmPaths || pmPaths.length === 0) return [];
    return pmPaths
      .filter((p) => p.includes('jei_recipeicons/progress_module/') && p.endsWith('_base.png'))
      .sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const progressRunCandidates = useMemo(() => {
    if (!pmPaths || pmPaths.length === 0) return [];
    return pmPaths
      .filter((p) => p.includes('jei_recipeicons/progress_module/') && p.endsWith('_run.png'))
      .sort((a, b) => a.localeCompare(b));
  }, [pmPaths]);

  const tabIdCandidates = useMemo(() => {
    const ids = (doc.options?.tabs ?? []).map((t) => t.id);
    return Array.from(new Set(ids)).sort((a, b) => a.localeCompare(b));
  }, [doc.options?.tabs]);

  const skinPreview = (texturePath: string, w: number, h: number) => (
    <img
      src={pmTextureUrl(texturePath)}
      width={w}
      height={h}
      style={{
        imageRendering: 'pixelated',
        display: 'block',
        borderRadius: 3,
        border: '1px solid rgba(255,255,255,0.12)',
      }}
      alt=""
    />
  );

  const widgetListPreview = (w: MachineUiWidget) => {
    const skin = String((w as any).skin ?? '').trim();
    if (!skin) return null;

    if (w.type === 'button') {
      const d = resolveGuiStatesButtonPreview(skin);
      if (!d) return null;
      return skinPreview(d.texturePath, 16, 19);
    }

    if (w.type === 'toggle') {
      const d = resolveGuiStatesTogglePreview(skin);
      if (!d) return null;
      return skinPreview(d.texturePath, 28, 14);
    }

    if (w.type === 'slider') {
      const p = resolveGuiStatesSliderPreview(skin);
      if (!p) return null;
      return skinPreview(p.base.texturePath, 28, 13);
    }

    if (w.type === 'textField') {
      const d = resolveGuiStatesTextFieldPreview(skin);
      if (!d) return null;
      // Keep this small; the source asset is wide.
      return skinPreview(d.texturePath, 40, 10);
    }

    return null;
  };

  return (
    <ScrollArea h="100%" type="auto" offsetScrollbars>
      <Stack gap="sm" pb="md">
        <Text size="sm" fw={600}>
          Widgets
        </Text>

        <Divider label="添加 Widgets" />

        <Stack gap={6}>
          <Button size="xs" variant="light" fullWidth onClick={() => addTextWidget()}>
            添加 Text
          </Button>

          <Button size="xs" variant="light" fullWidth onClick={() => addImageWidget()}>
            添加 Image
          </Button>

          <Button size="xs" variant="light" fullWidth onClick={() => addSlotGridWidget()}>
            添加 SlotGrid
          </Button>

          <Button size="xs" variant="light" fullWidth onClick={() => addPlayerInventoryWidget()}>
            添加 PlayerInv
          </Button>

          <Button size="xs" variant="light" fullWidth onClick={() => addProgressBarWidget()}>
            添加 Progress
          </Button>

          <Button
            size="xs"
            variant="light"
            fullWidth
            leftSection={skinPreview('gui/gui_states/empty_button/normal/default_n.png', 16, 19)}
            onClick={() => addButtonWidget()}
          >
            添加 Button
          </Button>

          <Button
            size="xs"
            variant="light"
            fullWidth
            leftSection={skinPreview('gui/gui_states/switch/normal/off.png', 28, 14)}
            onClick={() => addToggleWidget()}
          >
            添加 Toggle
          </Button>

          <Button
            size="xs"
            variant="light"
            fullWidth
            leftSection={skinPreview('gui/gui_states/slider/m/normal/lr_base_expand.png', 28, 13)}
            onClick={() => addSliderWidget()}
          >
            添加 Slider
          </Button>

          <Button
            size="xs"
            variant="light"
            fullWidth
            leftSection={skinPreview('gui/gui_states/input_box/box.png', 40, 10)}
            onClick={() => addTextFieldWidget()}
          >
            添加 TextField
          </Button>

          <Button size="xs" variant="light" fullWidth onClick={() => addScrollContainerWidget()}>
            添加 ScrollContainer
          </Button>
        </Stack>

        {widgets.length === 0 ? <Text size="sm" c="dimmed">暂无 Widgets</Text> : null}

        {widgets.length > 0 ? (
          <Stack gap={6}>
            {widgets.map((w) => {
              const selected = (selectedWidgetIds ?? []).includes(w.id);
              const isPrimary = w.id === selectedWidgetId;
              const title =
                w.type === 'text'
                  ? `Text: ${String((w as MachineUiTextWidget).text ?? '')}`
                  : w.type === 'slotGrid'
                    ? `SlotGrid ${(w as any).cols}×${(w as any).rows}`
                    : w.type === 'progress'
                      ? `Progress ${Math.round((Number((w as any).progress ?? 0) || 0) * 100)}%`
                      : w.type === 'image'
                        ? `Image: ${String((w as any).texturePath ?? '') || '(none)'}`
                        : w.type === 'button'
                          ? `Button: ${String((w as any).text ?? '') || '(no text)'}`
                          : w.type === 'toggle'
                            ? 'Toggle'
                            : w.type === 'slider'
                              ? 'Slider'
                              : w.type === 'textField'
                                ? `TextField: ${String((w as any).valueKey ?? '') || '(unbound)'}`
                      : w.type === 'panel'
                        ? `Panel (group) (${((w as MachineUiPanelWidget).childrenIds ?? []).length} children)`
                      : w.type === 'scroll_container'
                        ? `ScrollContainer (${((w as MachineUiScrollContainerWidget).childrenIds ?? []).length} children)`
                              : w.type === 'playerInventory'
                                ? 'PlayerInv'
                                : String((w as any).type ?? 'Widget');

              return (
                <Group
                  key={w.id}
                  gap="xs"
                  justify="space-between"
                  wrap="nowrap"
                  style={{
                    padding: '6px 8px',
                    borderRadius: 8,
                    background: isPrimary
                      ? 'rgba(255,255,255,0.07)'
                      : selected
                        ? 'rgba(255,255,255,0.05)'
                        : 'transparent',
                    border: selected ? '1px solid rgba(255,255,255,0.14)' : '1px solid transparent',
                    cursor: 'pointer',
                  }}
                  onClick={(e) => {
                    const additive = Boolean((e as any).shiftKey || (e as any).ctrlKey || (e as any).metaKey);
                    if (additive) toggleSelectedWidgetId(w.id);
                    else setSelectedWidgetId(w.id);
                  }}
                >
                  <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
                    {(() => {
                      const p = widgetListPreview(w);
                      if (!p) return null;
                      return <div style={{ marginBottom: 4, width: 'fit-content' }}>{p}</div>;
                    })()}
                    <Text size="sm" fw={selected ? 600 : 400} lineClamp={1}>
                      {title}
                    </Text>
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      x={(w as any).x}, y={(w as any).y}, w={(w as any).w}, h={(w as any).h}
                    </Text>
                  </Stack>
                  <Group gap={4}>
                    <ActionIcon
                      variant={selected ? 'light' : 'subtle'}
                      title="选中"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedWidgetId(w.id);
                      }}
                    >
                      <IconFocus2 size={18} />
                    </ActionIcon>
                    <ActionIcon
                      variant={selected ? 'light' : 'subtle'}
                      color="red"
                      title="删除"
                      onClick={(e) => {
                        e.stopPropagation();
                        removeWidget(w.id);
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

        {isMultiSelected ? (
          <>
            <Divider label="多选" />
            <Text size="sm" c="dimmed">
              已选中 {selectedWidgetCount} 个 Widget。当前详细编辑仅支持单选；你仍可进行批量操作。
            </Text>

            <Group grow>
              <Button size="xs" color="red" variant="light" onClick={() => deleteSelected()}>
                删除选中
              </Button>
              <Button size="xs" variant="light" onClick={() => groupSelectionIntoPanel()}>
                分组为 Panel
              </Button>
              <Button size="xs" variant="light" onClick={() => groupSelectionIntoScrollContainer()}>
                分组为 ScrollContainer
              </Button>
              <Button
                size="xs"
                variant="subtle"
                onClick={() => {
                  setSelectedWidgetIds([], undefined);
                }}
              >
                清空选择
              </Button>
            </Group>

            <Divider label="对齐 / 分布" />

            <Text size="xs" c="dimmed">
              对齐/分布基于“选中整体的包围盒”；已锁定的 Widget 不会被移动，但会参与包围盒计算。
            </Text>

            <Stack gap={6}>
              <Text size="sm" fw={600}>
                对齐到选择
              </Text>
              <Group grow>
                <Button size="xs" variant="light" onClick={() => alignSelection('left')}>
                  左
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelection('hcenter')}>
                  水平居中
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelection('right')}>
                  右
                </Button>
              </Group>
              <Group grow>
                <Button size="xs" variant="light" onClick={() => alignSelection('top')}>
                  上
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelection('vcenter')}>
                  垂直居中
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelection('bottom')}>
                  下
                </Button>
              </Group>
              <Button size="xs" variant="subtle" onClick={() => alignSelection('center')}>
                水平+垂直居中
              </Button>
            </Stack>

            <Stack gap={6}>
              <Text size="sm" fw={600}>
                均匀分布（按中心点）
              </Text>
              <Group grow>
                <Button size="xs" variant="light" onClick={() => distributeSelection('x')}>
                  水平分布
                </Button>
                <Button size="xs" variant="light" onClick={() => distributeSelection('y')}>
                  垂直分布
                </Button>
              </Group>
            </Stack>

            <Divider label="批量设置" />

            <Autocomplete label="批量设置 Tab（留空=全局）" value={batchTabId} data={tabIdCandidates} onChange={setBatchTabId} />
            <Button
              size="xs"
              variant="light"
              onClick={() => {
                const ids = selectedWidgetIds ?? [];
                const tabId = batchTabId.trim();
                const patches: Record<string, any> = {};
                ids.forEach((id) => (patches[id] = { tabId: tabId || undefined }));
                useMachineUiStore.getState().updateWidgets(patches);
              }}
            >
              应用 Tab 到选中
            </Button>

            <Group grow align="end">
              <Switch label="批量锁定" checked={batchLocked} onChange={(e) => setBatchLocked(e.currentTarget.checked)} />
              <Button
                size="xs"
                variant="light"
                onClick={() => {
                  const ids = selectedWidgetIds ?? [];
                  const patches: Record<string, any> = {};
                  ids.forEach((id) => (patches[id] = { locked: batchLocked }));
                  useMachineUiStore.getState().updateWidgets(patches);
                }}
              >
                应用锁定到选中
              </Button>
            </Group>
          </>
        ) : null}

        {selectedWidget ? (
          <>
            <Divider label="选中 Widget" />

            {canUngroupPanel || canUngroupScroll ? (
              <Group grow>
                {canUngroupPanel ? (
                  <Button size="xs" variant="light" onClick={() => ungroupSelectedPanel()}>
                    取消分组 Panel（Ungroup）
                  </Button>
                ) : null}
                {canUngroupScroll ? (
                  <Button size="xs" variant="light" onClick={() => ungroupSelectedScrollContainer()}>
                    取消分组 ScrollContainer（Ungroup）
                  </Button>
                ) : null}
              </Group>
            ) : null}

            <Stack gap={6}>
              <Text size="sm" fw={600}>
                对齐到画布
              </Text>
              <Group grow>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('left')}>
                  左
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('hcenter')}>
                  水平居中
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('right')}>
                  右
                </Button>
              </Group>
              <Group grow>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('top')}>
                  上
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('vcenter')}>
                  垂直居中
                </Button>
                <Button size="xs" variant="light" onClick={() => alignSelectedWidget('bottom')}>
                  下
                </Button>
              </Group>
              <Button size="xs" variant="subtle" onClick={() => alignSelectedWidget('center')}>
                水平+垂直居中
              </Button>
            </Stack>

            <CommonWidgetFields
              widgetId={selectedWidget.id}
              tabIdCandidates={tabIdCandidates}
              tabId={(selectedWidget as any).tabId}
              visibleIf={(selectedWidget as any).visibleIf}
              enabledIf={(selectedWidget as any).enabledIf}
            />

            {selectedWidget.type === 'text' ? <TextWidgetEditor w={selectedWidget as MachineUiTextWidget} /> : null}

            {selectedWidget.type === 'slotGrid' ? (
              <SlotGridWidgetEditor
                w={selectedWidget as MachineUiSlotGridWidget}
                slotTextureCandidates={slotTextureCandidates}
              />
            ) : null}

            {selectedWidget.type === 'progress' ? (
              <ProgressWidgetEditor
                w={selectedWidget as MachineUiProgressBarWidget}
                pmPaths={pmPaths}
                progressBaseCandidates={progressBaseCandidates}
                progressRunCandidates={progressRunCandidates}
              />
            ) : null}

            {selectedWidget.type === 'image' ? (
              <ImageWidgetEditor w={selectedWidget as MachineUiImageWidget} guiTextureCandidates={guiTextureCandidates} />
            ) : null}

            {selectedWidget.type === 'button' ? <ButtonWidgetEditor w={selectedWidget as MachineUiButtonWidget} /> : null}

            {selectedWidget.type === 'toggle' ? (
              <ToggleWidgetEditor w={selectedWidget as MachineUiToggleWidget} guiTextureCandidates={guiTextureCandidates} />
            ) : null}

            {selectedWidget.type === 'slider' ? <SliderWidgetEditor w={selectedWidget as MachineUiSliderWidget} /> : null}

            {selectedWidget.type === 'textField' ? <TextFieldWidgetEditor w={selectedWidget as MachineUiTextFieldWidget} /> : null}

            {selectedWidget.type === 'playerInventory' ? (
              <PlayerInventoryWidgetEditor w={selectedWidget as MachineUiPlayerInventoryWidget} />
            ) : null}

            {selectedWidget.type === 'scroll_container' ? (
              <ScrollContainerWidgetEditor w={selectedWidget as MachineUiScrollContainerWidget} />
            ) : null}
          </>
        ) : null}
      </Stack>
    </ScrollArea>
  );
}
