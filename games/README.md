# Wrist Arcade – 10 games for the M99 / H20 Android watch

Small neon games sized for the square 720×720 watch screen. Every game can be played
**vs CPU** (Easy / Normal / Hard) or **2 players** on the same watch.

| # | Game | How to play | CPU |
|---|------|-------------|-----|
| 1 | Tic-Tac-Toe | Three in a row | Perfect play on Hard |
| 2 | Connect Four | Tap a column to drop a disc, connect 4 | Alpha-beta search, 6 moves deep on Hard |
| 3 | Reversi | Place to flip, most discs wins. Dots show legal moves | Positional search |
| 4 | Checkers | Tap a piece, then a gold dot. Jumps are compulsory, multi-jumps, kings | Alpha-beta, 6 plies on Hard |
| 5 | Dots & Boxes | Tap between two dots to draw a line; close a box to score and go again | Avoids giving chains away |
| 6 | Gomoku | Five in a row on 11×11 | Pattern scoring + fork detection |
| 7 | Mancala | Tap a pit to sow; last seed in your store = go again; captures | Minimax, 8 plies on Hard |
| 8 | Nim | Tap a stick (selects it and those to its right), tap again to take. Last stick loses | Solves the game exactly on Hard |
| 9 | Pong | Drag in your half. First to 5 | Paddle speed by level |
| 10 | Memory Match | Find pairs, a pair = go again | Remembers more cards on Hard |

- After a game ends, tap the board to play again (the starting player alternates).
- The screen keeps a running score for the session.
- The CPU thinks on a background thread, so the screen never freezes.

## Getting the APK

Every push that changes `games/` runs the **Build Wrist Arcade games app** action and publishes
a `Wrist Arcade build N` release with the APK. Install it on the phone to test, then send it to the
watch with LocalSend.

The game rules and the CPU were tested on a desktop by playing every game CPU-vs-CPU at all three levels.
