# Editor GUI sprites / 编辑器 GUI 素材

These PNGs are the editable source assets; no generation step is required.
这些 PNG 就是源素材，可直接编辑，不需要重新生成。

| Files / 文件 | Purpose / 用途 |
| --- | --- |
| `type/*.png` | 16×16 transparent type icons / 透明类型图标 |
| `graystone/fill.png` | 8×8 white fill, tinted by button state / 白色底纹，随按钮状态着色 |
| `graystone/border.png` | 8×8 normal bevel / 正常灰石边框 |
| `graystone/border_disabled.png` | 8×8 disabled bevel / 禁用边框 |

Edit as RGBA PNG with a pixel pencil and nearest-neighbor scaling. Keep transparency;
white icon pixels inherit the UI foreground color, and colored pixels are multiplied by it.
The initial 16×16 icons preserve the former 8×8 design doubled without interpolation.

使用 RGBA PNG、像素铅笔和最近邻缩放，保留透明背景。图标白色部分跟随界面前景色，
彩色部分会与前景色相乘。初始图标是原 8×8 图案无插值放大两倍，可直接在 16×16 上细化。

Graystone sprites use adjacent `.png.mcmeta` files with an 8×8 nine-slice and a fixed
2-pixel border. The center is 4×4; fill has transparent borders and bevels have transparent
centers. Keep both layers aligned. If changing dimensions, update the metadata too.
Focus outlines and semantic state colors remain in code.

BRNQuest stretches the nine regions with a bounded mesh instead of vanilla 1.21.1's
tiny-tile repetition. Keep center/edge artwork suitable for stretching; texture details
there will stretch with the control. Corners retain their logical dimensions.

灰石素材旁的 `.png.mcmeta` 定义 8×8 九宫格、固定 2 像素边框，中心为 4×4。
底纹边缘透明，边框中心透明，两层应保持对齐；改尺寸时同步修改元数据。
键盘焦点轮廓和各状态的语义色仍由代码控制。

BRNQuest 使用固定数量的网格拉伸九个区域，避免 1.21.1 原生九宫格大量平铺小块。
中心和边缘的纹理细节会随控件伸展，请使用适合拉伸的图案；四角保留逻辑尺寸。

For a resource pack, keep the same paths below `assets/brnquest/textures/gui/sprites/editor/`.
Reload game resources with F3+T to preview changes to an enabled resource pack. Editing the
source checkout requires rebuilding/reinstalling the JAR unless running from development resources.
Native item and advancement images are not baked into these sprites.

资源包覆盖时保留上述路径。修改已启用的资源包后可用 F3+T 重载；修改项目源目录后，
通常需要重新构建并安装 JAR（开发环境直接加载资源的情况除外）。物品和进度图像仍使用原生渲染。
