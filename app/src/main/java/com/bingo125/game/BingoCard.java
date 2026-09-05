package com.bingo125.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A single player's 5x5 card containing numbers 1..25 and marked cells. */
public class BingoCard {
    public static final int SIZE = 5;
    public static final int MAX_NUMBER = SIZE * SIZE;
    private final int[][] grid = new int[SIZE][SIZE];
    private final boolean[][] marked = new boolean[SIZE][SIZE];
    private int nextNumberToPlace = 1;

    public boolean placeNumber(int row,int col,int number){
        if(row<0||row>=SIZE||col<0||col>=SIZE||number<1||number>MAX_NUMBER)return false;
        if(grid[row][col]!=0||number!=nextNumberToPlace)return false;
        grid[row][col]=number;nextNumberToPlace++;return true;
    }
    public int getNextNumberToPlace(){return nextNumberToPlace;}
    public boolean isComplete(){return nextNumberToPlace>MAX_NUMBER;}

    /** Fills remaining cells with unused numbers in random order. */
    public void autoFillRemaining(){fillRemaining(true);}
    public void randomFillRemaining(){fillRemaining(true);}
    private void fillRemaining(boolean random){
        boolean[] used=new boolean[MAX_NUMBER+1];
        for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++){int v=grid[r][c];if(v>=1&&v<=MAX_NUMBER)used[v]=true;}
        List<Integer> unused=new ArrayList<>();for(int n=1;n<=MAX_NUMBER;n++)if(!used[n])unused.add(n);if(random)Collections.shuffle(unused);
        int k=0;for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)if(grid[r][c]==0)grid[r][c]=unused.get(k++);
        nextNumberToPlace=MAX_NUMBER+1;
    }
    public int getValue(int row,int col){return grid[row][col];}
    public boolean isMarked(int row,int col){return marked[row][col];}
    public boolean markNumber(int number){
        if(number<1||number>MAX_NUMBER)return false;
        for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)if(grid[r][c]==number){if(marked[r][c])return false;marked[r][c]=true;return true;}
        return false;
    }
    public boolean containsNumber(int number){if(number<1||number>MAX_NUMBER)return false;for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)if(grid[r][c]==number)return true;return false;}
    public int markedCount(){int n=0;for(int r=0;r<SIZE;r++)for(int c=0;c<SIZE;c++)if(marked[r][c])n++;return n;}
    public int[][] getGrid(){return grid;}
}
