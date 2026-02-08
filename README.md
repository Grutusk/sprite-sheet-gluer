# Sprite Sheet Gluer

Small JavaFX tool that builds sprite sheets from a character folder layout.

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

## Cell size and layout

- All frames must share the same width and height. If any frame differs, the
  tool stops with an error.
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
