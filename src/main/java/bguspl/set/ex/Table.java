package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
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

    /**
     * for each player and slot, true iff the player's token is placed on the slot
     */
    private final boolean[][] tokensMatrix;

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
        this.tokensMatrix=new boolean[env.config.players][12];
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /*
     * Retrieves the cards which the player placed his tokens upon
     */
    public int[] getPlayerTokensCards(int playerId) {
        int count = 0;
        for (int i = 0; i < tokensMatrix[playerId].length; i++)
            if (tokensMatrix[playerId][i])
                count++;
        
        int[] cards = new int[count];
        int j = 0;
        for (int i = 0; i < tokensMatrix[playerId].length; i++)
            if (tokensMatrix[playerId][i]){
                cards[j] = slotToCard[i];
                j++;
            }
        
        return cards;
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
     * Counts the number of token the player has placed on the table
     * 
     * @param playerId - the player to count tokens for
     */
    public int countTokens(int playerId) {
        int count = 0;

        for (int slot = 0; slot < tokensMatrix[playerId].length; slot++)
            if (tokensMatrix[playerId][slot])
                count++;

        return count;
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

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        // take the card off the table (so no other player can place tokens on it)
        cardToSlot[slotToCard[slot]] = null;
        slotToCard[slot] = null;

        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if(!tokensMatrix[player][slot] && slotToCard[slot] != null){
            // there is a card present to place token upon and a token of the player was not placed already
            tokensMatrix[player][slot]=true;
            env.ui.placeToken(player, slot);
            System.out.println(Thread.currentThread().getName() + " placed on " + slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if(tokensMatrix[player][slot]){
            tokensMatrix[player][slot]=false;
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }
}
