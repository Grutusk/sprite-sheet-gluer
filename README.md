# Sprite Sheet Gluer

Small JavaFX tool with three workflows:

- Build sprite sheets from a character folder layout with loose frame images.
- Group loose images in one folder by file-name prefix and build one or more
  sprite sheets per prefix.
- Merge existing sprite sheets into one larger sheet using a fixed frame size.

## Input folder layout

- Character root folder contains animation subfolders.
- Each animation folder can:
  - Contain image files directly, or
  - Contain direction subfolders with image files inside them.
- Image files are sorted by file name within a folder.
- If the selected root folder has no subfolders but contains images, those images
  are treated as a single animation and glued into one sprite sheet.
- For the loose-file prefix tab, select a folder that contains image files directly.
  Enter the frame size first; the UI defaults to `128x256`.
  Files are grouped by the prefix before the first space, `_`, `-`, or digit.
  Examples:
  - `Stone 01.png`, `Stone_02.png`, `StoneB03.png` -> `Stone-sheet.png`
  - `Ground A01.png`, `Ground B02.png` -> `Ground-sheet.png`

## Output files

For each character:

- `<character>.png` sprite sheet.
- `<character>.frames.txt` mapping of animation folder to frame indices.

For loose-file prefix groups:

- `<prefix>-sheet.png` sprite sheet.
- `<prefix>-sheet.frames.txt` mapping of source file name to frame index.
- If a prefix group would exceed Godot's `16384x16384` texture limit, the tool
  splits it into numbered files such as `<prefix>-sheet-01.png`.

For the existing-sheet merge tab:

- `<folder>-merged.png` merged sprite sheet, packed to stay within Godot's
  `16384x16384` texture limit when possible.
- `<folder>-merged.frames.txt` mapping of source sheet row to frame indices.
- The saved merged PNG is re-read and verified pixel-for-pixel against the
  source sheets.

## Cell size and layout

- Frames are expected to share the same width and height. If they do not, the
  folder-based workflow uses the most common frame size and skips the others
  (with a warning).
- Frames are drawn without scaling into the top-left corner of each cell.
- Row order is the scan order of animation/direction folders. Column order is
  the file name order within each folder.
- For loose-file prefix groups, only frames matching the selected size are
  included. Matching frames stay in file name order and are packed into the
  most compact grid that fits within the Godot size limit.

## Mapping file format

First line stores the grid size:

```
grid: rowsxcolumns
```

Each following line is:

```
animation/path -> 0, 1, 2
```

Indices are zero-based and follow row-major order (left to right, then next row).

For merged existing sheets, each row entry uses the source file name without extension
plus the row direction in this order from top to bottom:

`right`, `down_se`, `down`, `down_sw`, `left`, `up_nw`, `up`, `up_ne`

Example:

```
Attack1/right -> 0, 1, 2
```

If some source sheets use a different row order, add an optional
`direction-order.txt` file in the selected folder. Example:

```txt
default=right,down_se,down,down_sw,left,up_nw,up,up_ne
Idle=right,down_se,down,left,down_sw,up_nw,up,up_ne
Idle2=right,down_se,down,left,down_sw,up_nw,up,up_ne
```

Use the source file name without `.png` on the left. `default` or `*` sets the
fallback order for files without an explicit override.

For loose-file prefix groups, each mapping line is the original file name without
extension followed by its zero-based frame index:

```txt
grid: 2x2
Stone A01 -> 0
Stone B02 -> 1
Stone C03 -> 2
```
