# Web Editor 预览字体（可选）

本目录用于 **Machine UI Web Editor** 的“所见即所得”字体预览。

出于版权/授权差异，本仓库 **不直接分发** Mojangles / Unifont 等字体文件。

## 支持的文件名（约定）

把字体文件放到这里后，编辑器会尝试自动加载：

- `mojangles.ttf`  → 预览字体族：`PM_Mojangles`
- `unifont.ttf`    → 预览字体族：`PM_Unifont`（fallback）

路径对应：

- `/fonts/mojangles.ttf`
- `/fonts/unifont.ttf`

如果文件不存在或加载失败，编辑器会自动回退到系统字体/等宽字体。

## 自定义字体覆盖

在编辑器右侧「文档」页签的“字体预览”区域，你也可以上传任意 `.ttf/.otf`，用于覆盖预览效果（同样不会影响 runtime 导出）。

另外也支持填写 **字体 URL 链接**（例如放在同源静态目录下的 `/fonts/my.ttf`，或 `https://.../my.ttf`）。

注意：如果字体文件来自跨域地址，需要服务端提供正确的 CORS 头，否则浏览器可能拒绝加载（编辑器会静默回退到 fallback 字体）。
