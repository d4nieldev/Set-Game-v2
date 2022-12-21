package bguspl.set.ex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.swing.ViewportLayout;

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
    private final Dealer dealer;

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
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * the next slot to place a token upon
     */
    private volatile int nextSlot;

    private long freezeUntil;

    private boolean penalized;

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
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.nextSlot = -1;
        this.freezeUntil = System.currentTimeMillis();
        this.penalized = false;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            synchronized (this) {
                try { wait(); }
                catch (InterruptedException ignored) {}
            }
            placeNextToken();
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            Random rand = new Random();
            while (!terminate) {
                int keypress = rand.nextInt(env.config.tableSize);

                if (table.countTokens(id) == 3) {
                    int[] cards = table.getPlayerTokensCards(id);
                    keypress = table.cardToSlot[cards[rand.nextInt(cards.length)]];
                }
                
                keyPressed(keypress);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {}
                
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        // wait for player thread to finish
        try { playerThread.join(); } catch (InterruptedException ignored) {}
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (System.currentTimeMillis() >= freezeUntil) {
            // if the player is not frozen due to point or penalty
            System.out.println(Thread.currentThread() + " pressed key " + slot);
            nextSlot = slot;
            try {
                synchronized (this) { 
                    // wakes the playerThread up (to place the token)
                    notify();
                    // wait for the playerThread to finish placing the token
                    wait(); 
                }
            } catch (InterruptedException ignored) {}
        }
    }

    private void placeNextToken() {
        if (nextSlot != -1) {
            if(!table.removeToken(id, nextSlot))
                if (table.countTokens(id) < 3)
                    table.placeToken(id, nextSlot);

            // after placing token - there are 3 tokens
            while (table.countTokens(id) == 3 && !penalized)
                dealer.addClaim(id);
            

            penalized = false;
            
            // less than 3 tokens or penalized - notify the keypresses thread to accept keypresses
            synchronized(this) { notify(); }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        freezeUntil = System.currentTimeMillis() + env.config.pointFreezeMillis;
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        freezeUntil = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        penalized = true;
    }

    public int score() {
        return score;
    }

    public long getFreezeUntil() {
        return freezeUntil;
    }
}
