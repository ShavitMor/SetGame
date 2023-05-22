package bguspl.set.ex;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;
    public boolean done;
    /**
     * The current score of the player.
     */
    private int score;
    private volatile AtomicInteger BoardTokens;
    private int keyPressed;
    private Queue<Integer> picks;
    private boolean punished;
    private boolean isScore;
    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        picks =new ArrayDeque<>(3);
        BoardTokens =new AtomicInteger();
        BoardTokens.set(0);
        done =false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        System.out.println(Level.INFO+ "Thread " + Thread.currentThread().getName() + "starting.");
        try{
            Thread.sleep(25);
        }
        catch (InterruptedException ex){
            Thread.interrupted();
        }
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            boolean isPlaceNew=false;
            checkForPenaltyOrScore();
            synchronized (picks) {
                while (picks.isEmpty()) {
                    try {
                        // System.out.println("player" + id +" waits at player");
                        picks.wait();
                        // System.out.println("player" + id +" runs again at player");
                    } catch (InterruptedException e) {
                        terminate();
                    }
                }
                /*try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }*/
                int curr = picks.poll();
                if(table.slotToCard[curr]!=null&&table.slotsToPlayers.get()[curr][id]==false) {
                    if(BoardTokens.get()<3) {
                        table.placeToken(id, curr);
                        updateTokensNum(true);
                       isPlaceNew=true;
                    }
                }
                else{
                    table.removeToken(id,curr);
                    updateTokensNum(false);
                }
                if (BoardTokens.get() >= 3&&isPlaceNew) {
                    table.playerFinished(id);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        terminate();
                    }
                    isPlaceNew=false;
                }
                picks.notifyAll();
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                synchronized (picks) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                    Random random = new Random();
                    keyPressed = random.nextInt(env.config.rows*env.config.columns);
                    while (picks.size() == 3) {
                        try {
                            picks.wait();
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                    }
                    picks.add(keyPressed);
                    picks.notifyAll();
                }

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            terminate();
        }
    }
    /**
     * Creates a smart and fast ai - for testing
     *
     * Currently, might enter .wait() at player while waiting at AI and stops playing
     * */
    private void createActuallySmartAI(){
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            boolean repress = false;
            while (!terminate) {
                synchronized (picks) {
                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                    Queue<Integer> toPress = new ArrayDeque<>(3);
                    while (picks.size() == 3) {
                        int currScore = getScore();
                        try {
                            System.out.println("player" + id +" waits at AI");
                            picks.wait();
                            System.out.println("player" + id +" runs again at AI");
                        } catch (InterruptedException e) {
                            Thread.interrupted();
                        }
                        if(currScore==getScore()){
                            repress = true;
                        }
                    }
                    toPress = SmartAiMoves(repress, toPress);
                    while (toPress.isEmpty())
                        toPress = SmartAiMoves(!repress, toPress);
                    while(!toPress.isEmpty()){
                        keyPressed = toPress.poll();
                        picks.add(keyPressed);
                        picks.notifyAll();
                        try{
                            Thread.sleep(10);
                        }
                        catch (InterruptedException ex){
                            Thread.interrupted();
                        }

                    }
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "Smartypants-" + id);
        aiThread.start();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            terminate();
        }

    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
       terminate=true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
       if (picks.size() < 3 && !punished && !isScore){
            if(human) {
                while (picks.size() == 3) {
                }
                keyPressed = slot;
                synchronized (picks) {
                    picks.add(keyPressed);
                    picks.notifyAll();
                }
            }  
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        isScore=true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        punished =true;
    }

    public boolean getIsScore(){
        return isScore;
    }

    public Queue<Integer> getPicks(){
        return picks;
    }

    public boolean getIsPunished(){
        return punished;
    }
    public int getId(){
        return id;
    }

    public int getScore() {
        return score;
    }

    public synchronized void thirdToken(){
        done =true;
    }

    /**
     * Used in the AI to remove tokens when needed.
     * @return queue of all pressed inputs that currently have tokens on them
     * */
    private Queue<Integer> getAllPressed(){
        Queue<Integer> toRePress = new ArrayDeque<>(3);
        for (int i = 0; i<env.config.tableSize; i++){
            if (table.slotsToPlayers.get()[i][id]==true){
                toRePress.add(i);
            }
        }
        while (toRePress.isEmpty()){
            toRePress = table.getSetOnBoardSlots();
        }
        return toRePress;
    }


    private Queue <Integer> SmartAiMoves(boolean repress,Queue <Integer> currentPressQueue){
        while (currentPressQueue.peek() == null || currentPressQueue.isEmpty()){

            try{
                Thread.sleep(400);
            }
            catch (InterruptedException ex){
                Thread.interrupted();
            }
            if (BoardTokens.get() > 0){
                repress = true;
            }
            if (!repress){
                while(!table.isThereASetOnBoard()){
                    try{
                        Thread.sleep(1000);
                        System.out.println("Ai" + Thread.currentThread().getName() +" found no sets on board");
                    }
                    catch (InterruptedException ex){
                        Thread.interrupted();
                    }

                }
                currentPressQueue =  table.getSetOnBoardSlots();
            }
            else{
                currentPressQueue = getAllPressed();
            }
        }
        System.out.println("current queue for AI" + Thread.currentThread().getName() + " is "+ currentPressQueue);
        return currentPressQueue;
    }


    public void updateTokensNum(boolean incDec){
        int old;
        int newVal;
        if(incDec) {
            do {
                old = BoardTokens.get();
                newVal = old + 1;
            }while (!BoardTokens.compareAndSet(old,newVal));
        }
        else {
            do {

                old = BoardTokens.get();
                newVal = old - 1;
            }while (!BoardTokens.compareAndSet(old,newVal));
        }
    }
    public void updateTokensNumToZero(){
        int old;
        int newVal;
        do {
            old = BoardTokens.get();
            newVal = 0;
        }while (!BoardTokens.compareAndSet(old,newVal));

    }
    /**
     * Checks if the player needs penalty or score, and freezes them
     * */
    public void checkForPenaltyOrScore(){
        if(punished)
        {
            freezePlayer(env.config.penaltyFreezeMillis);
            punished = false;
        }
        else if(isScore){
            freezePlayer(env.config.pointFreezeMillis);
            isScore = false;
        }
    }
    public int getNumOfOnBoardTokens(){
        return BoardTokens.get();
    }

    /**
     * Freezes the current player and while updating the freeze timer in UI.
     * Used in penalty or point.
     *
     * @param freezeTime - time to freeze the player thread (milliseconds)
     *
     * */
    private void freezePlayer(long freezeTime){
        final long mlSecond = 1;
        env.ui.setFreeze(id,freezeTime);
        while(freezeTime > 0){
            try{
                env.ui.setFreeze(id,freezeTime);
                Thread.sleep(mlSecond);
                freezeTime-=mlSecond;
            }
            catch(InterruptedException ex){
                Thread.interrupted();
            }
        }
        env.ui.setFreeze(id, 0);
        synchronized (picks){
            picks.clear();
            picks.notifyAll();
        }
    }
}
