import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import fs from 'node:fs';
import path from 'node:path';
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
  const includeDirs = [
    path.join(pmTexturesRoot, 'gui', 'jei_recipeicons'),
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

  return {
    name: 'pm-textures',
    apply: 'serve',
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
  };
}

function pmTexturesBuildPlugin() {
  const __filename = fileURLToPath(import.meta.url);
  const __dirname = path.dirname(__filename);
  const pmTexturesRoot = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'assets', 'prototypemachinery', 'textures');
  const includeDirs = [
    path.join(pmTexturesRoot, 'gui', 'jei_recipeicons'),
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

  return {
    name: 'pm-textures-build',
    apply: 'build',
    generateBundle(this: any) {
      const index = computeIndex();

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
  };
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
