package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)
    protected AtomicReference<Boolean[][]> slotsToPlayers;
    protected AtomicReference<Deque<Integer>> finished;
    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        slotsToPlayers=new AtomicReference<>();
        Boolean[][] x=new Boolean[env.config.tableSize][env.config.players];
        for(int i=0;i< x.length;i++){
           for(int j=0;j<env.config.players;j++){
               x[i][j]=new Boolean(false);
           }
        }
        slotsToPlayers.set(x);
        finished=new AtomicReference<>();
        finished.set(new ArrayDeque<>());
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        for(int i=0;i<slotToCard.length;i++){
            slotToCard[i]=null;
        }
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    public Queue<Integer> getSetOnBoardSlots(){
        Queue<Integer> setOnBoard = new ArrayDeque<>(3);
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck,1).forEach(set -> {
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            for (Integer i:
                 slots) {
                setOnBoard.add(i);
            }
        });
        return setOnBoard;
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}
        for (int i = 0; i < env.config.players; i++) {
            removeToken(i,slot);
        }
        cardToSlot[slot]=-1;
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(slotToCard[slot]!=null) {
            Boolean[][] oldVal;
            Boolean[][] newVal;
            do {
                oldVal = getSlotsToPlayers().get();
                newVal = getSlotsToPlayers().get();
                newVal[slot][player] = true;
                env.ui.placeToken(player,slot);
            } while (!slotsToPlayers.compareAndSet(oldVal,newVal));
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        Boolean[][] oldVal;
        Boolean[][] newVal;
        do {
            oldVal = getSlotsToPlayers().get();
            newVal = oldVal;
            newVal[slot][player] = false;
            env.ui.removeToken(player,slot);
        }while (!slotsToPlayers.compareAndSet(oldVal,newVal));
        return true;
    }
    private AtomicReference<Boolean[][]> getSlotsToPlayers(){
        return slotsToPlayers;
    }

    public void playerFinished(int id){
        Deque<Integer>oldVal;
        Deque<Integer> newVal;
        do {
            oldVal=finished.get();
            newVal=oldVal;
            newVal.addLast(id);;
        }while (!finished.compareAndSet(oldVal,newVal));
    }
    public int getPlayer(){
        Deque<Integer>oldVal;
        Deque<Integer> newVal;
        int x;
        do {
            oldVal=finished.get();
            newVal=oldVal;
            x=newVal.removeFirst();
        }while (!finished.compareAndSet(oldVal,newVal));
        return x;
    }
    public List<Integer> getPlayersDeck(int id){
        List<Integer> playerDeck=new ArrayList<>();
        for(int i=0;i<12;i++){
            if(slotsToPlayers.get()[i][id]==true)
                playerDeck.add(slotToCard[i]);
        }
        return playerDeck;
    }
    /**
     * Checks whether there is a set on the board.
     * Used for no timer game modes.
     * @return true if there is a set on the board
     * */
    public boolean isThereASetOnBoard(){
        List <Integer> currentCards = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        hints();
        return env.util.findSets(currentCards,1).size() > 0;
    }

    public boolean isNoTimerGameMode(){
        return env.config.turnTimeoutMillis <= 0;
    }

    public boolean isElapsedTimeNeeded(){
        return env.config.turnTimeoutMillis == 0;
    }
}
