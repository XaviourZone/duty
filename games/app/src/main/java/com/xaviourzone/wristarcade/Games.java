package com.xaviourzone.wristarcade;

/** The 10 games in menu order. */
final class Games {

    static final String[] NAMES = {
            "Tic-Tac-Toe", "Connect Four", "Reversi", "Checkers", "Dots & Boxes",
            "Gomoku", "Mancala", "Nim", "Pong", "Memory Match"};

    static final String[] BLURBS = {
            "Three in a row", "Drop discs, connect 4", "Flip to own the board", "Jump & crown kings",
            "Close the most boxes", "Five in a row", "Sow seeds, fill your store", "Last stick loses",
            "Neon paddles, first to 5", "Find the most pairs"};

    private Games() {}

    static Game create(int i) {
        switch (i) {
            case 0: return new TicTacToe();
            case 1: return new ConnectFour();
            case 2: return new Reversi();
            case 3: return new Checkers();
            case 4: return new DotsBoxes();
            case 5: return new Gomoku();
            case 6: return new Mancala();
            case 7: return new Nim();
            case 8: return new Pong();
            default: return new Memory();
        }
    }
}
