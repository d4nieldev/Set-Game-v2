package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collector;
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
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private final Queue<Integer> setClaims;

    private volatile boolean wakeup;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.setClaims = new LinkedList<>();
        this.wakeup = false;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : players)
            new Thread(p, "Player #" + p.id).start();;
        
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Adding a set claim
     * 
     * @param playerId - the player that claimed for a set
     * @return         - true iff the player was inserted to the queue
     */
    public boolean addClaim(int playerId) {
        boolean added = false;
        synchronized(setClaims) {
            // now dealer cant remove tokens and keypress is waiting so no change to the player tokens can be made
            if (table.countTokens(playerId) == 3){
                // player has 3 tokens
                setClaims.add(playerId); 
                added = true;
            }
        }
        // PROBLEM: dealer removed the player token, so he removed him from the queue too and notified, then player sent to wait - nobody notifies him
        if (added) {
            try{ 
                // wake the dealer up
                this.wakeup = true;
                // make the playerThread wait on the dealer until a set is checked and then everybody are notified
                synchronized(this) {
                    // System.out.println(Thread.currentThread() + " claimed set " + Arrays.toString(table.getPlayerTokensCards(playerId)) + " is waiting...");
                    while (setClaims.contains(playerId))
                        wait(100); 
                    // System.out.println(Thread.currentThread() + " Done waiting!");
                }
            } catch (InterruptedException ignored) {}
        }

        return added;
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            // System.out.println("dealer woke up");
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        if (System.currentTimeMillis() >= reshuffleTime)
            updateTimerDisplay(true);
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // terminate all players
        for (Player p : players)
            p.terminate();

        terminate = true;
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
        if (!setClaims.isEmpty()){
            // remove the player from the queue
            int playerId;
            synchronized (setClaims) {
                playerId = setClaims.remove();
            }

            // get the actual set
            int[] cards = table.getPlayerTokensCards(playerId);
            // System.out.println("Set claim being checked: player #" + playerId + " - " + Arrays.toString(cards));
            if (cards.length != 3)
                throw new UnsupportedOperationException("WTF?????????????????????????? " + cards.length);
            Set<Integer> playersToRemoveAndNotify = new HashSet<>();
            
            // check if it is a set
            if (env.util.testSet(cards)){
                // remove all cards and tokens
                for (int card : cards) 
                    playersToRemoveAndNotify.addAll(removeCardWithTokens(table.cardToSlot[card]));

                players[playerId].point();
                // System.out.println("Set found!");
            }
            else{
                players[playerId].penalty();
                // System.out.println("Not a set :(");
            }

            // remove players from the queue if needed, and notify everyone
            removeAndNotifyAllPlayers(playersToRemoveAndNotify);
        }
    }

    /**
     * @param slot  - the slot to remove the card and tokens from
     * @return      - set of players that their tokens were removed from this slot
     */
    private Set<Integer> removeCardWithTokens(int slot){
        table.lockTable();
        Set<Integer> playersWithRemovedTokens = new HashSet<>();

        // remove the card
        table.removeCard(slot);

        // remove the tokens of all players from this card
        for (int playerId = 0; playerId < env.config.players; playerId++)
            if (table.removeToken(playerId, slot))
                // for each player like that, remove him from the queue and notify later
                playersWithRemovedTokens.add(playerId);

        return playersWithRemovedTokens;
    }

    /**
     * Removes the players from the set claims queue and notifies them
     * 
     * @param playersSet - the players to remove from the set claims
     */
    private void removeAndNotifyAllPlayers(Set<Integer> playersSet) {
        synchronized (setClaims) {
            for (Integer p : playersSet){
                // System.out.println("Removing Player #" + p + " from set claims queue");
                setClaims.remove(p);
            }
        }
        
        synchronized(this) { notifyAll(); }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);

        for (int slot = 0; slot < env.config.tableSize; slot++){
            // for each empty slot
            if (table.slotToCard[slot] == null && deck.size() > 0){
                // place card from deck if possible
                table.placeCard(deck.remove(0), slot);
                updateTimerDisplay(true);
            }
        }

        // check if there are no sets
        if (deck.size() > 0 && env.util.findSets(Arrays.asList(table.slotToCard).stream().filter(Objects::nonNull).collect(Collectors.toList()), 1).size() == 0){
            removeAllCardsFromTable();
            placeCardsOnTable();
        }
        else if (deck.size() == 0 && env.util.findSets(Arrays.asList(table.slotToCard).stream().filter(Objects::nonNull).collect(Collectors.toList()), 1).size() == 0){
            removeAllCardsFromTable();
            terminate = true;
        }

        // finished placing cards
        table.unlockTable();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            while(!this.wakeup && System.currentTimeMillis() < reshuffleTime){
                Thread.sleep(100);
                updateTimerDisplay(false);
            }
            // sleep next time
            this.wakeup = false;
        } 
        catch (InterruptedException ignored) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;

        env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), 
                            reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis);

        for (Player p : players)
            env.ui.setFreeze(p.id, p.getFreezeUntil() - System.currentTimeMillis());
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // start removing all cards
        table.lockTable();

        Set<Integer> playersToRemoveAndNotify = new HashSet<>();

        for (int slot = 0; slot < env.config.tableSize; slot++){
            // add the card back to the deck
            if (table.slotToCard[slot] != null)
                deck.add(table.slotToCard[slot]);
            playersToRemoveAndNotify.addAll(removeCardWithTokens(slot));
            updateTimerDisplay(true);
        }
        
        removeAndNotifyAllPlayers(playersToRemoveAndNotify);
    }

    /**
     * Returns the players with highest score.
     */
    private int [] getHighestScores(){
        int top=0;
        //find the highest score among all players
        for(Player p: players){
            if(p.score()>=top){
                top=p.score();
            }
        }
        List<Integer> list=new ArrayList<>();
        //check how many has this score and add them to list
        for(Player p: players){
            if(p.score()==top){
                list.add(p.id);
            }
        }
        int [] res=new int[list.size()];
        for(int i=0; i<res.length; i++){
            res[i]=list.get(i);
        }
        return res;
    }

    
    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // System.out.println("THE GAME IS FINISHED! number of sets left in deck: " + env.util.findSets(deck, 1));
        env.ui.announceWinner(getHighestScores());
        // System.out.println("The winners are players "+Arrays.toString(getHighestScores()));
        terminate();
    }
}
