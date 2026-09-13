# Sudoku Scanner

## Description
An app to scan Sudoku puzzles and export them to be played in OpenSudoku.
- https://opensudoku.moire.org/


## Todo
- Continueing to improve the processing of images, so I put a bug reporting button that will push a bug report to Github Issues.


## Key Features & Design Highlights
- 100% Offline OCR & Puzzle Recognition:
  - Integrated on-device Google ML Kit Text Recognition to process captured photos and gallery images without any internet connection, making it suitable for flights or areas without connectivity.
  - Automatically maps detected digits to their corresponding 9x9 cell coordinates and highlights the detected clues.
- Graphical Sudoku Board & Interactive Editor:
  - High-Contrast 9x9 Grid Layout: Engineered with distinct 3x3 block subgrid boundaries, responsive cell sizing, and typography optimized for quick visual scanning.
  - Visual Feedback & Real-Time Conflict Detection: Dynamically highlights conflicting duplicate numbers in rows, columns, or 3x3 blocks in soft alert red. Related rows, columns, and matching numbers are subtly highlighted for visual tracking.
  - Side-by-Side Photo Comparison: An accordion preview allows you to expand the original scanned photo directly above or alongside the grid to verify individual digits against the source material.
  - Touch Keypad & Tools: Numeric keypad (1–9) with frequency counters, quick clear, undo/reset to original scan, and an automated rule-validation solver.
- OpenSudoku Export Engine (.opensudoku & .sdm):
  - .opensudoku XML Format: Generates valid OpenSudoku XML files with folder categorization, timestamps, and puzzle state attributes ready for OpenSudoku import.
  - .sdm Text Format: Generates the standard 81-character line format widely compatible with OpenSudoku, SudoCue, and QQWing.
  - Device Storage & Sharing: Allows saving directly to any folder on your device (e.g. Downloads or Documents) via Android's Storage Access Framework, sharing directly to OpenSudoku via the system sharesheet, or copying to the clipboard.
- Offline Persistence & Preloaded Samples:
  - Built with a local Room database to automatically save scanned puzzles and maintain history.
  - Includes built-in sample puzzles across difficulty tiers so you can test importing, editing, and exporting immediately without needing a physical puzzle on hand.
- Adaptive Theming:
  - Full support for both Light Mode and Dark Mode with high-contrast board borders, eye-safe midnight slate backgrounds, and custom adaptive app launcher icons.
