package com.bingo125.game;

public class Player {
    private final String name;
    private final BingoCard card = new BingoCard();

    public Player(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public BingoCard getCard() { return card; }
}
