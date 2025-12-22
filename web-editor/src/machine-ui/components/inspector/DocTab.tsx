import {Button, Divider, FileInput, Group, NumberInput, ScrollArea, Select, Stack, Switch, Text, TextInput} from '@mantine/core';
import {useEffect, useMemo, useState} from 'react';
import {useMachineUiStore} from '../../store/machineUiStore';
import {getPreviewFontFamily} from '../../fonts/previewFonts';

export function DocTab() {
  const doc = useMachineUiStore((s) => s.doc);

  const setDocName = useMachineUiStore((s) => s.setDocName);
  const setCanvasSize = useMachineUiStore((s) => s.setCanvasSize);
  const setGridSize = useMachineUiStore((s) => s.setGridSize);

  const mode = useMachineUiStore((s) => s.selection.mode);
  const setMode = useMachineUiStore((s) => s.setMode);

  const setShowGuides = useMachineUiStore((s) => s.setShowGuides);

  const previewFont = useMachineUiStore((s) => s.previewFont);
  const setPreviewFontPreset = useMachineUiStore((s) => s.setPreviewFontPreset);
  const setPreviewFontScale = useMachineUiStore((s) => s.setPreviewFontScale);
  const setCustomPreviewFont = useMachineUiStore((s) => s.setCustomPreviewFont);
  const setCustomPreviewFontUrl = useMachineUiStore((s) => s.setCustomPreviewFontUrl);

  const [customFontUrlDraft, setCustomFontUrlDraft] = useState(previewFont.customUrl ?? '');
  useEffect(() => {
    setCustomFontUrlDraft(previewFont.customUrl ?? '');
  }, [previewFont.customUrl]);

  const previewFontFamily = useMemo(() => getPreviewFontFamily(previewFont), [previewFont]);

  const showGuides = Boolean(doc.options?.showGuides);

  return (
    <ScrollArea h="100%" type="auto" offsetScrollbars>
      <Stack gap="sm" pb="md">
        <TextInput label="文档名" value={doc.name} onChange={(e) => setDocName(e.currentTarget.value)} />

        <Group grow>
          <NumberInput
            label="画布宽"
            value={doc.canvas.width}
            min={1}
            step={1}
            onChange={(v) => setCanvasSize(Number(v) || 1, doc.canvas.height)}
          />
          <NumberInput
            label="画布高"
            value={doc.canvas.height}
            min={1}
            step={1}
            onChange={(v) => setCanvasSize(doc.canvas.width, Number(v) || 1)}
          />
        </Group>

        <NumberInput
          label="网格大小"
          description="编辑器吸附步长（>=1px）。提示：当 gridSize 很小时，网格显示会自动稀疏化以避免卡顿。"
          value={doc.options?.gridSize ?? 8}
          min={1}
          step={1}
          onChange={(v) => setGridSize(Number(v) || 1)}
        />

        <Switch label="显示 Guides" checked={showGuides} onChange={(e) => setShowGuides(e.currentTarget.checked)} />

        <Divider label="字体预览（编辑器）" />

        <Select
          label="预览字体"
          description="仅影响编辑器 Canvas 预览，不会导出到 runtime JSON/ZenScript。Minecraft 预设会尝试加载 public/fonts 下的 mojangles/unifont；自定义字体可上传文件或填写 URL 链接（跨域需要 CORS）。"
          value={previewFont.preset}
          onChange={(v) => setPreviewFontPreset((v as any) ?? 'minecraft')}
          data={[
            { value: 'minecraft', label: 'Minecraft（Mojangles + Unifont fallback）' },
            { value: 'system', label: '系统字体（System UI）' },
            { value: 'custom', label: '自定义字体（上传文件 / URL 链接）' },
          ]}
        />

        <NumberInput
          label="字体缩放"
          description="用于适配 Mojangles / 其他字体的观感差异。建议范围：0.75 ~ 1.5。"
          value={previewFont.scale}
          min={0.25}
          max={3}
          step={0.05}
          onChange={(v) => setPreviewFontScale(Number(v) || 1)}
        />

        <FileInput
          label="上传自定义字体（.ttf/.otf）"
          description={previewFont.customName ? `当前：${previewFont.customName}` : '上传后将覆盖预览字体（Custom preset）。'}
          accept=".ttf,.otf"
          clearable
          onChange={(file) => {
            if (!file) {
              setCustomPreviewFont(undefined, undefined);
              return;
            }

            const reader = new FileReader();
            reader.onload = () => {
              const dataUrl = typeof reader.result === 'string' ? reader.result : undefined;
              setCustomPreviewFont(dataUrl, file.name);
              // Switch to custom preset automatically.
              setPreviewFontPreset('custom');
            };
            reader.readAsDataURL(file);
          }}
        />

        <Group align="end" grow>
          <TextInput
            label="自定义字体链接（URL）"
            description="支持 http(s):// 或同源 /...（例如放在 web-editor/public/ 下）。点击“应用”后才会保存到本地。"
            placeholder="https://example.com/font.ttf"
            value={customFontUrlDraft}
            onChange={(e) => setCustomFontUrlDraft(e.currentTarget.value)}
          />
          <Button
            variant="light"
            onClick={() => {
              setCustomPreviewFontUrl(customFontUrlDraft || undefined, undefined);
              setPreviewFontPreset('custom');
            }}
          >
            应用
          </Button>
        </Group>

        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            预览示例：
          </Text>
          <Button
            size="xs"
            variant="subtle"
            color="gray"
            onClick={() => {
              setCustomPreviewFont(undefined, undefined);
              setCustomPreviewFontUrl(undefined, undefined);
              setCustomFontUrlDraft('');
            }}
          >
            清除自定义字体
          </Button>
        </Group>
        <Text
          style={{
            fontFamily: previewFontFamily,
            fontSize: Math.max(10, Math.round(12 * (previewFont.scale || 1))),
            background: 'rgba(0,0,0,0.18)',
            border: '1px solid rgba(255,255,255,0.08)',
            padding: 8,
            borderRadius: 8,
          }}
        >
          The quick brown fox jumps over the lazy dog 0123456789
          <br />
          中文预览：机器界面 文本 对齐 颜色
        </Text>

        <Divider label="编辑模式" />
        <Group grow>
          <Button variant={mode === 'widgets' ? 'light' : 'subtle'} onClick={() => setMode('widgets')}>
            编辑 Widgets
          </Button>
          <Button variant={mode === 'guides' ? 'light' : 'subtle'} onClick={() => setMode('guides')}>
            编辑 Guides
          </Button>
        </Group>
      </Stack>
    </ScrollArea>
  );
}
