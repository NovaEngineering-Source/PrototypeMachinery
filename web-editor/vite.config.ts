import {defineConfig, type Plugin} from 'vite';
import react from '@vitejs/plugin-react';
// NOTE: These are Node.js built-ins used by Vite config.
// If your editor reports "Cannot find module 'node:fs'" etc, the root cause is usually missing @types/node.
// Recommended fix (project-wide): add devDependency "@types/node".
// This file keeps local ts-ignore to avoid hard-failing typecheck in environments without node types.
// @ts-ignore
import fs from 'node:fs';
// @ts-ignore
import path from 'node:path';
// @ts-ignore
import {fileURLToPath} from 'node:url';

type PmAssetIndex = {
  /** Path relative to PM textures root, e.g. "gui/slot.png" */
  paths: string[];
};

function walkPngFiles(dir: string): string[] {
  const out: string[] = [];
  const stack = [dir];
  while (stack.length) {
    const cur = stack.pop()!;
    let entries: fs.Dirent[];
    try {
      entries = fs.readdirSync(cur, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const ent of entries) {
      const full = path.join(cur, ent.name);
      if (ent.isDirectory()) {
        stack.push(full);
      } else if (ent.isFile() && ent.name.toLowerCase().endsWith('.png')) {
        out.push(full);
      }
    }
  }
  return out;
}

function toPosix(p: string): string {
  return p.split(path.sep).join('/');
}

function pmTexturesPlugin() {
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);

  // web-editor/.. = PrototypeMachinery/
  const pmTexturesRoot = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'prototypemachinery', 'textures');
  const pmLogoFile = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'prototypemachinery', 'logo.png');
  const includeDirs = [
    path.join(pmTexturesRoot, 'gui', 'jei_recipeicons'),
    // Machine UI gui_states skins (buttons/toggles/sliders/input boxes, etc)
    path.join(pmTexturesRoot, 'gui', 'gui_states'),
  ];
  const includeFiles = [
    path.join(pmTexturesRoot, 'gui', 'slot.png'),
    // Machine UI default backgrounds (used by DefaultMachineUI.kt)
    path.join(pmTexturesRoot, 'gui', 'gui_controller_a.png'),
    path.join(pmTexturesRoot, 'gui', 'gui_controller_b.png'),
  ];

  const computeIndex = (): PmAssetIndex => {
    const files: string[] = [];
    for (const d of includeDirs) {
      files.push(...walkPngFiles(d));
    }
    for (const f of includeFiles) {
      if (fs.existsSync(f) && fs.statSync(f).isFile()) files.push(f);
    }

    const paths = Array.from(
      new Set(
        files
          .map((abs) => toPosix(path.relative(pmTexturesRoot, abs)))
          .filter((rel) => !rel.startsWith('..')),
      ),
    ).sort();

    return { paths };
  };

  const resolveTextureFile = (relPosix: string): string | undefined => {
    const rel = relPosix.replace(/^\/+/, '');
    // basic traversal guard
    if (rel.includes('..')) return undefined;
    const abs = path.resolve(pmTexturesRoot, rel.split('/').join(path.sep));
    if (!abs.startsWith(pmTexturesRoot)) return undefined;
    if (!fs.existsSync(abs)) return undefined;
    if (!fs.statSync(abs).isFile()) return undefined;
    return abs;
  };

  const plugin = {
    name: 'pm-textures',
    apply: 'serve' as const,
    configureServer(server: any) {
      server.middlewares.use((req: any, res: any, next: any) => {
        const url = req.url || '';
        if (url === '/pm-asset-index.json') {
          const index = computeIndex();
          res.statusCode = 200;
          res.setHeader('Content-Type', 'application/json; charset=utf-8');
          res.end(JSON.stringify(index));
          return;
        }
        if (url === '/pm-logo.png') {
          if (!fs.existsSync(pmLogoFile) || !fs.statSync(pmLogoFile).isFile()) {
            res.statusCode = 404;
            res.end('Not found');
            return;
          }
          res.statusCode = 200;
          res.setHeader('Content-Type', 'image/png');
          fs.createReadStream(pmLogoFile).pipe(res);
          return;
        }
        if (url.startsWith('/pm-textures/')) {
          const rel = decodeURIComponent(url.slice('/pm-textures/'.length));
          const abs = resolveTextureFile(rel);
          if (!abs) {
            res.statusCode = 404;
            res.end('Not found');
            return;
          }
          res.statusCode = 200;
          res.setHeader('Content-Type', 'image/png');
          fs.createReadStream(abs).pipe(res);
          return;
        }
        next();
      });
    },
  } satisfies Plugin;

  return plugin;
}

function pmTexturesBuildPlugin() {
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  const pmTexturesRoot = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'prototypemachinery', 'textures');
  const pmLogoFile = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'prototypemachinery', 'logo.png');
  const includeDirs = [
    path.join(pmTexturesRoot, 'gui', 'jei_recipeicons'),
    // Machine UI gui_states skins (buttons/toggles/sliders/input boxes, etc)
    path.join(pmTexturesRoot, 'gui', 'gui_states'),
  ];
  const includeFiles = [
    path.join(pmTexturesRoot, 'gui', 'slot.png'),
    // Machine UI default backgrounds (used by DefaultMachineUI.kt)
    path.join(pmTexturesRoot, 'gui', 'gui_controller_a.png'),
    path.join(pmTexturesRoot, 'gui', 'gui_controller_b.png'),
  ];

  const computeIndex = (): PmAssetIndex => {
    const files: string[] = [];
    for (const d of includeDirs) {
      files.push(...walkPngFiles(d));
    }
    for (const f of includeFiles) {
      if (fs.existsSync(f) && fs.statSync(f).isFile()) files.push(f);
    }
    const paths = Array.from(
      new Set(
        files
          .map((abs) => toPosix(path.relative(pmTexturesRoot, abs)))
          .filter((rel) => !rel.startsWith('..')),
      ),
    ).sort();
    return { paths };
  };

  const plugin = {
    name: 'pm-textures-build',
    apply: 'build' as const,
    generateBundle(this: any) {
      const index = computeIndex();

      // 0) emit logo (non-texture asset)
      try {
        if (fs.existsSync(pmLogoFile) && fs.statSync(pmLogoFile).isFile()) {
          const buf = fs.readFileSync(pmLogoFile);
          this.emitFile({
            type: 'asset',
            fileName: 'pm-logo.png',
            source: buf,
          });
        }
      } catch {
        // ignore
      }

      // 1) emit index
      this.emitFile({
        type: 'asset',
        fileName: 'pm-asset-index.json',
        source: JSON.stringify(index),
      });

      // 2) emit textures
      for (const rel of index.paths) {
        const abs = path.resolve(pmTexturesRoot, rel.split('/').join(path.sep));
        try {
          const buf = fs.readFileSync(abs);
          this.emitFile({
            type: 'asset',
            fileName: `pm-textures/${rel}`,
            source: buf,
          });
        } catch {
          // ignore
        }
      }
    },
  } satisfies Plugin;

  return plugin;
}

// GitHub Pages 友好：使用相对 base，避免仓库名/子路径问题。
export default defineConfig({
  base: './',
  plugins: [react(), pmTexturesPlugin(), pmTexturesBuildPlugin()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
