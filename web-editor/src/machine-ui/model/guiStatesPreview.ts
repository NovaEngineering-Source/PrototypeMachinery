export type GuiStatesFullDrawable = {
  kind: 'full';
  texturePath: string; // e.g. "gui/gui_states/.../foo.png"
};

export type GuiStatesSeparatedNineSliceDrawable = {
  kind: 'separated9';
  texturePath: string;
  imageW: number;
  imageH: number;
  borderLeft: number;
  borderTop: number;
  borderRight: number;
  borderBottom: number;
  /** Separator pixels between slices. Use 1 for gui_states *_expand templates, 0 for normal 9-slice. */
  separatorPx?: number;
  /** Optional sub-rect inside the image (pixels). */
  sub?: { x: number; y: number; w: number; h: number };
};

export type GuiStatesDrawable = GuiStatesFullDrawable | GuiStatesSeparatedNineSliceDrawable;

export type GuiStatesSkinSizing = {
  /** Default widget size in editor world units (pixels). */
  defaultW: number;
  defaultH: number;
  /** Whether the skin is intended to be stretched on each axis. */
  stretchX: boolean;
  stretchY: boolean;
};

export function resolveGuiStatesButtonSizing(skin?: string): GuiStatesSkinSizing | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  if (s === 'gui_states/empty_button/normal' || s === 'gui_states/empty_button/shadow') {
    return { defaultW: 16, defaultH: 19, stretchX: false, stretchY: false };
  }

  if (s === 'gui_states/expand_button/normal' || s === 'gui_states/expand_button/shadow') {
    return { defaultW: 16, defaultH: 19, stretchX: true, stretchY: true };
  }

  return undefined;
}

export function resolveGuiStatesToggleSizing(skin?: string): GuiStatesSkinSizing | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  if (s === 'gui_states/switch/normal' || s === 'gui_states/switch/shadow') {
    return { defaultW: 28, defaultH: 14, stretchX: false, stretchY: false };
  }

  return undefined;
}

export function resolveGuiStatesTextFieldSizing(skin?: string): GuiStatesSkinSizing | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  // Mirrors PMUI.inputBox* defaults.
  if (s === 'gui_states/input_box/normal' || s === 'gui_states/input_box/shadow') {
    return { defaultW: 56, defaultH: 13, stretchX: true, stretchY: true };
  }

  // Mirrors PMUI.inputBoxExpand* defaults (min 5x10, 9-slice stretchable).
  if (s === 'gui_states/input_box/expand/normal' || s === 'gui_states/input_box/expand/shadow') {
    return { defaultW: 5, defaultH: 10, stretchX: true, stretchY: true };
  }

  return undefined;
}

export function resolveGuiStatesButtonPreview(skin?: string): GuiStatesDrawable | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  // Mirrors InteractiveWidgetFactory.resolveGuiStatesButtonSkin (preview uses NORMAL state).
  if (s === 'gui_states/empty_button/normal') {
    return { kind: 'full', texturePath: 'gui/gui_states/empty_button/normal/default_n.png' };
  }
  if (s === 'gui_states/empty_button/shadow') {
    return { kind: 'full', texturePath: 'gui/gui_states/empty_button/shadow/default_n.png' };
  }

  // expand_button uses empty_button/*/default_expand as the base (9-slice).
  if (s === 'gui_states/expand_button/normal') {
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/empty_button/normal/default_expand.png',
      imageW: 16,
      imageH: 19,
      borderLeft: 4,
      borderTop: 9,
      borderRight: 11,
      borderBottom: 9,
    };
  }
  if (s === 'gui_states/expand_button/shadow') {
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/empty_button/shadow/default_expand.png',
      imageW: 16,
      imageH: 19,
      borderLeft: 4,
      borderTop: 9,
      borderRight: 11,
      borderBottom: 9,
    };
  }

  return undefined;
}

export function resolveGuiStatesTogglePreview(skin?: string): GuiStatesDrawable | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  // Mirrors InteractiveWidgetFactory.buildToggleButton when skin matches gui_states switch.
  if (s === 'gui_states/switch/normal') {
    return { kind: 'full', texturePath: 'gui/gui_states/switch/normal/off.png' };
  }
  if (s === 'gui_states/switch/shadow') {
    return { kind: 'full', texturePath: 'gui/gui_states/switch/shadow/off.png' };
  }

  return undefined;
}

export function resolveGuiStatesTextFieldPreview(skin?: string): GuiStatesDrawable | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s) return undefined;

  // Mirrors InteractiveWidgetFactory.buildTextField skin mapping.
  if (s === 'gui_states/input_box/normal') {
    // gui_states.md: W=56 H=13, input area X:2 Y:2 W:51 H:8
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/input_box/box.png',
      imageW: 56,
      imageH: 13,
      borderLeft: 2,
      borderTop: 2,
      borderRight: 3,
      borderBottom: 3,
      separatorPx: 0,
    };
  }
  if (s === 'gui_states/input_box/shadow') {
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/input_box/box_shadow.png',
      imageW: 56,
      imageH: 13,
      borderLeft: 2,
      borderTop: 2,
      borderRight: 3,
      borderBottom: 3,
      separatorPx: 0,
    };
  }

  if (s === 'gui_states/input_box/expand/normal') {
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/input_box/box_expand.png',
      imageW: 8,
      imageH: 13,
      borderLeft: 3,
      borderTop: 6,
      borderRight: 4,
      borderBottom: 6,
      separatorPx: 1,
    };
  }

  if (s === 'gui_states/input_box/expand/shadow') {
    return {
      kind: 'separated9',
      texturePath: 'gui/gui_states/input_box/box_expand_shadow.png',
      imageW: 8,
      imageH: 13,
      borderLeft: 3,
      borderTop: 6,
      borderRight: 4,
      borderBottom: 6,
      separatorPx: 1,
    };
  }

  return undefined;
}

export type GuiStatesSliderPreview = {
  base: GuiStatesDrawable;
  handleTexturePath: string;
  handleW: number;
  handleH: number;
  axis: 'x' | 'y';
};

export type GuiStatesSliderSizing = GuiStatesSkinSizing & {
  axis: 'x' | 'y';
};

export function resolveGuiStatesSliderSizing(skin?: string): GuiStatesSliderSizing | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s.startsWith('gui_states/slider/')) return undefined;

  const parts = s.split('/');
  // gui_states/slider/{s|m}/{x|y}_expand?/{normal|shadow}
  if (parts.length < 5) return undefined;

  const size = parts[2];
  const axisPart = parts[3];

  const expand = axisPart.endsWith('_expand');
  const axis = (expand ? axisPart.replace(/_expand$/, '') : axisPart) as 'x' | 'y';
  if (axis !== 'x' && axis !== 'y') return undefined;
  if (size !== 's' && size !== 'm') return undefined;

  // Mirrors PMUI template defaults (PMUI.kt)
  if (!expand) {
    if (size === 's' && axis === 'x') return { axis, defaultW: 56, defaultH: 10, stretchX: false, stretchY: false };
    if (size === 'm' && axis === 'x') return { axis, defaultW: 56, defaultH: 13, stretchX: false, stretchY: false };
    if (size === 's' && axis === 'y') return { axis, defaultW: 10, defaultH: 77, stretchX: false, stretchY: false };
    if (size === 'm' && axis === 'y') return { axis, defaultW: 13, defaultH: 56, stretchX: false, stretchY: false };
    return undefined;
  }

  // *_expand: stretch only along the axis.
  if (size === 's' && axis === 'x') return { axis, defaultW: 14, defaultH: 10, stretchX: true, stretchY: false };
  if (size === 'm' && axis === 'x') return { axis, defaultW: 17, defaultH: 13, stretchX: true, stretchY: false };
  if (size === 's' && axis === 'y') return { axis, defaultW: 10, defaultH: 16, stretchX: false, stretchY: true };
  if (size === 'm' && axis === 'y') return { axis, defaultW: 13, defaultH: 18, stretchX: false, stretchY: true };
  return undefined;
}

export function resolveGuiStatesSliderPreview(skin?: string): GuiStatesSliderPreview | undefined {
  const s = typeof skin === 'string' ? skin.trim() : '';
  if (!s.startsWith('gui_states/slider/')) return undefined;

  const parts = s.split('/');
  // gui_states/slider/{s|m}/{x|y}_expand?/{normal|shadow}
  if (parts.length < 5) return undefined;

  const size = parts[2];
  const axisPart = parts[3];
  const look = parts[4];
  const folder = look === 'shadow' ? 'shadow' : 'normal';
  const expand = axisPart.endsWith('_expand');
  const axis = (expand ? axisPart.replace(/_expand$/, '') : axisPart) as 'x' | 'y';

  if (axis !== 'x' && axis !== 'y') return undefined;
  if (size !== 's' && size !== 'm') return undefined;

  const baseRel =
    axis === 'x'
      ? `gui/gui_states/slider/${size}/${folder}/lr_base${expand ? '_expand' : ''}.png`
      : `gui/gui_states/slider/${size}/${folder}/ud_base${expand ? '_expand' : ''}.png`;

  let base: GuiStatesDrawable;
  if (!expand) {
    base = { kind: 'full', texturePath: baseRel };
  } else {
    // Mirrors InteractiveWidgetFactory.applyGuiStatesSliderSkin
    const key = `${size}/${axis}/${folder}`;
    if (key === 's/x/normal' || key === 's/x/shadow') {
      base = { kind: 'separated9', texturePath: baseRel, imageW: 14, imageH: 10, borderLeft: 6, borderTop: 0, borderRight: 7, borderBottom: 0 };
    } else if (key === 'm/x/normal') {
      base = { kind: 'separated9', texturePath: baseRel, imageW: 17, imageH: 13, borderLeft: 7, borderTop: 0, borderRight: 9, borderBottom: 0 };
    } else if (key === 'm/x/shadow') {
      base = { kind: 'separated9', texturePath: baseRel, imageW: 14, imageH: 13, borderLeft: 6, borderTop: 0, borderRight: 7, borderBottom: 0 };
    } else if (key === 's/y/normal' || key === 's/y/shadow') {
      base = { kind: 'separated9', texturePath: baseRel, imageW: 10, imageH: 16, borderLeft: 0, borderTop: 8, borderRight: 0, borderBottom: 7 };
    } else if (key === 'm/y/normal' || key === 'm/y/shadow') {
      base = { kind: 'separated9', texturePath: baseRel, imageW: 13, imageH: 18, borderLeft: 0, borderTop: 8, borderRight: 0, borderBottom: 9 };
    } else {
      base = { kind: 'full', texturePath: baseRel };
    }
  }

  const handlePrefix = axis === 'x' ? 'lr' : 'ud';
  const handleTexturePath = `gui/gui_states/slider/${size}/${handlePrefix}_default.png`;

  // Matches runtime handle sizes.
  const dimsKey = `${size}/${axis}`;
  const [handleW, handleH] =
    dimsKey === 's/x' || dimsKey === 's/y'
      ? [7, 8]
      : dimsKey === 'm/x'
        ? [11, 11]
        : dimsKey === 'm/y'
          ? [10, 12]
          : [7, 8];

  return { base, handleTexturePath, handleW, handleH, axis };
}
