# Sprite Sheet Gluer

Small JavaFX tool with two workflows:

- Build sprite sheets from a character folder layout with loose frame images.
- Merge existing sprite sheets into one larger sheet using a fixed frame size.

## Input folder layout

- Character root folder contains animation subfolders.
- Each animation folder can:
  - Contain image files directly, or
  - Contain direction subfolders with image files inside them.
- Image files are sorted by file name within a folder.
- If the selected root folder has no subfolders but contains images, those images
  are treated as a single animation and glued into one sprite sheet.

## Output files

For each character:

- `<character>.png` sprite sheet.
- `<character>.frames.txt` mapping of animation folder to frame indices.

For the existing-sheet merge tab:

- `<folder>-merged.png` merged sprite sheet, packed to stay within Godot's
  `16384x16384` texture limit when possible.
- `<folder>-merged.frames.txt` mapping of source sheet row to frame indices.
- The saved merged PNG is re-read and verified pixel-for-pixel against the
  source sheets.

## Cell size and layout

- Frames are expected to share the same width and height. If they do not, the
  tool uses the most common frame size and skips the others (with a warning).
- Frames are drawn without scaling into the top-left corner of each cell.
- Row order is the scan order of animation/direction folders. Column order is
  the file name order within each folder.

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
