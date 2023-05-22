package bguspl.set.ex;

import bguspl.set.Env;
//import sun.jvm.hotspot.runtime.Threads;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    private List<Thread> threads;
    private long timer;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        threads=new ArrayList<>();
        timer=env.config.turnTimeoutMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        System.out.println(Level.INFO+ " Thread " + Thread.currentThread().getName() + " starting.");
        for (Player player: players){
            Thread thread=new Thread(player);
            threads.add(thread);
        }
        for(Thread thread: threads){
            thread.start();
        }
        while (!shouldFinish()) {
            if(!table.isNoTimerGameMode()) {
                placeCardsOnTable();
                timerLoop();
                updateTimerDisplay(false);
                removeAllCardsFromTable();
            }
            else{
                placeCardsOnTable();
                if(table.isElapsedTimeNeeded())
                    env.ui.setElapsed(timer);
                WaitAndUpdateTimer();
            }
        }
        announceWinners();
        terminatePlayerThreadsGracefully();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate=true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        for (int i = 0; i <env.config.tableSize ; i++) {
            if(table.slotToCard[i]==null){
                env.ui.removeCard(i);
                if(!deck.isEmpty()) {
                    int cardIndex = getRandomCardFromDeck();
                    table.placeCard(deck.get(cardIndex), i);
                    env.ui.placeCard(deck.remove(cardIndex), i);
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int index=0;
        while (!deck.isEmpty()&&index<env.config.tableSize) {
            if (table.slotToCard[index]==null) {
                int cardIndex = getRandomCardFromDeck();
                table.placeCard(deck.get(cardIndex),index);
                env.ui.placeCard(deck.remove(cardIndex),index );

            }
            index++;
        }
        ReshuffleCurrentCards();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        while (timer>0){
            if(table.finished.get().isEmpty()==false){
                    int curr = table.getPlayer();
                    CheckCards(curr);
            }
                timer = timer - 10;
                if(timer<=env.config.turnTimeoutWarningMillis)
                    env.ui.setCountdown(timer, true);
                else
                    env.ui.setCountdown(timer, false);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        timer=env.config.turnTimeoutMillis;
        env.ui.setCountdown(timer,false);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < env.config.tableSize; i++) {
            if(table.slotToCard[i]!=null) {
                for (int j = 0; j <env.config.players ; j++) {
                    if(table.slotsToPlayers.get()[i][j])
                    {
                        table.removeToken(j,i);
                        players[j].updateTokensNumToZero();
                    }
                }
                int i1 = table.slotToCard[i];
                deck.add(i1);
                table.cardToSlot[i1] = null;
                table.slotToCard[i] = null;
                env.ui.removeCard(i);
            }
        }
        if(env.util.findSets(deck, 1).size() == 0)
            terminate();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max=winnersScore();
        int[] winners=getWinners(max);
        env.ui.announceWinner(winners);
    }

    /**
     * checks if the player(playerId) needs to be punished or score, and penalizes or give score accordingly
     *
     * @param playerId - id of player to penalize or give a score.
     * */
    public void CheckCards(int playerId){

        if(!isPlayersDeckASet(playerId)) {
            players[playerId].penalty();
        }
        else {
            List<Integer> playerDeck=table.getPlayersDeck(playerId);
            for(int i:playerDeck){
                int i1=table.cardToSlot[i];
                table.slotToCard[i1]=null;
                for(int i2=0;i2<env.config.players;i2++){
                    if(table.slotsToPlayers.get()[i1][i2]) {
                        table.removeToken(i2, i1);
                        players[i2].updateTokensNum(false);
                    }
                }
            }
            removeCardsFromTable();
            players[playerId].updateTokensNumToZero();
            players[playerId].point();
        }
    }

    /**
     * Checks if the player(playerId) has its tokens on cards that make a set
     * @param playerId - id of player to check
     * @return true if the player currently has its tokens on a set
     * */
    public boolean isPlayersDeckASet(int playerId){
        List<Integer> playerDeck=table.getPlayersDeck(playerId);
        return env.util.findSets(playerDeck, 1).size() > 0;
    }

    public int winnersScore(){
        Optional<Player> player = Arrays.stream(players).max(new Comparator<Player>() {
            @Override
            public int compare(Player o1, Player o2) {
                return o1.getScore()-o2.getScore();
            }
        });
        return player.get().getScore();
    }
    public int[] getWinners(int score){
        int[]winnersId;

        List<Player> winners=Arrays.stream(players).filter(new Predicate<Player>() {
            @Override
            public boolean test(Player player) {
                if(player.getScore()==score)
                    return true;
                else
                    return false;
            }
        }).collect(Collectors.toList());
        winnersId=new int[winners.size()];
        int index=0;
        for(Player player:winners){
            winnersId[index]=player.getId();
            index++;
        }
        return winnersId;
    }

    private int getRandomCardFromDeck(){
        Random rand = new Random();
        return rand.nextInt(deck.size());
    }

    private void ReshuffleCurrentCards(){
        if (table.isNoTimerGameMode()){
            while(!table.isThereASetOnBoard()){
                removeAllCardsFromTable();
                placeCardsOnTable();
            }
        }
    }


    private void WaitAndUpdateTimer(){
        final long Second = 1000;
        try {
            Thread.sleep(Second);
            timer+=Second;
            if (table.isElapsedTimeNeeded())
                env.ui.setElapsed(timer);
        }
        catch (InterruptedException ex){
            Thread.interrupted();
        }
        if(table.finished.get().isEmpty()==false) {
            int curr = table.getPlayer();
            CheckCards(curr);
            if (table.isElapsedTimeNeeded()){
                timer = 0;
                env.ui.setElapsed(timer);
            }
        }
    }
    /**
     * Terminates all player threads in reverse order to the one they were created by.
     * */
    private void terminatePlayerThreadsGracefully(){
        for(int i=players.length-1;i>=0;i--){
            players[i].terminate();
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                terminate();
            }
        }
    }

}
