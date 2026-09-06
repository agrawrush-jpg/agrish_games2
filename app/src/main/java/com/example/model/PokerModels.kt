package com.example.model

import java.io.Serializable
import kotlin.random.Random

enum class Suit(val symbol: String, val colorHex: String) {
    SPADES("♠", "#1E1E1E"),
    HEARTS("♥", "#D32F2F"),
    DIAMONDS("♦", "#1976D2"),
    CLUBS("♣", "#388E3C")
}

enum class Rank(val value: Int, val displayName: String) {
    TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
    SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"),
    TEN(10, "10"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"),
    ACE(14, "A")
}

data class Card(val rank: Rank, val suit: Suit) : Serializable {
    override fun toString(): String = "${rank.displayName}${suit.symbol}"
}

enum class BotDifficulty {
    BEGINNER, EASY, MEDIUM, HARD, EXPERT
}

data class Player(
    val id: String,
    val name: String,
    var avatar: String, // Emoji representation
    var balance: Int,
    var currentBet: Int = 0,
    var isFolded: Boolean = false,
    var isAllIn: Boolean = false,
    var isDealer: Boolean = false,
    var isSmallBlind: Boolean = false,
    var isBigBlind: Boolean = false,
    var isMyTurn: Boolean = false,
    var actionBubbleText: String? = null, // Fold, Check, Raise, etc.
    var holeCards: List<Card> = emptyList(),
    val isBot: Boolean = false,
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM
) : Serializable

enum class GamePhase {
    PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN
}

enum class HandType(val rankValue: Int, val displayName: String) {
    HIGH_CARD(1, "High Card"),
    ONE_PAIR(2, "One Pair"),
    TWO_PAIR(3, "Two Pair"),
    THREE_OF_A_KIND(4, "Three of a Kind"),
    STRAIGHT(5, "Straight"),
    FLUSH(6, "Flush"),
    FULL_HOUSE(7, "Full House"),
    FOUR_OF_A_KIND(8, "Four of a Kind"),
    STRAIGHT_FLUSH(9, "Straight Flush"),
    ROYAL_FLUSH(10, "Royal Flush")
}

// Result of a hand evaluation
data class HandEvaluation(
    val handType: HandType,
    val description: String,
    val scoreValue: Int // Higher value is a better hand
)

object PokerHandEvaluator {

    fun generateDeck(): MutableList<Card> {
        val deck = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                deck.add(Card(rank, suit))
            }
        }
        deck.shuffle()
        return deck
    }

    fun evaluate7Cards(hole: List<Card>, community: List<Card>): HandEvaluation {
        val allCards = (hole + community).distinct()
        if (allCards.size < 5) {
            // Under 5 cards, evaluate pocket potential
            return evaluateHoleCardsOnly(hole)
        }

        // Generate all possible 5-card combinations
        val combinations = combinations(allCards, 5)
        var bestEval = HandEvaluation(HandType.HIGH_CARD, "High Card", 0)

        for (combo in combinations) {
            val currentEval = evaluate5Cards(combo)
            if (currentEval.scoreValue > bestEval.scoreValue) {
                bestEval = currentEval
            }
        }
        return bestEval
    }

    private fun evaluateHoleCardsOnly(hole: List<Card>): HandEvaluation {
        if (hole.size < 2) return HandEvaluation(HandType.HIGH_CARD, "No Cards", 0)
        val c1 = hole[0]
        val c2 = hole[1]

        if (c1.rank == c2.rank) {
            return HandEvaluation(HandType.ONE_PAIR, "Pocket Pair of ${c1.rank.displayName}'s", 200000 + c1.rank.value * 100)
        }

        val highCard = if (c1.rank.value > c2.rank.value) c1 else c2
        val secondCard = if (c1.rank.value > c2.rank.value) c2 else c1
        val isSuited = c1.suit == c2.suit
        val isConnector = Math.abs(c1.rank.value - c2.rank.value) == 1

        val desc = when {
            isSuited && isConnector -> "Suited Connectors: ${highCard.rank.displayName} & ${secondCard.rank.displayName}"
            isSuited -> "Suited Hole Cards: ${highCard.rank.displayName} & ${secondCard.rank.displayName}"
            isConnector -> "Connected Hole Cards: ${highCard.rank.displayName} & ${secondCard.rank.displayName}"
            else -> "High Card ${highCard.rank.displayName}"
        }

        return HandEvaluation(HandType.HIGH_CARD, desc, highCard.rank.value * 100 + secondCard.rank.value)
    }

    private fun evaluate5Cards(cards: List<Card>): HandEvaluation {
        val sorted = cards.sortedByDescending { it.rank.value }
        val isFlush = cards.groupBy { it.suit }.any { it.value.size >= 5 }
        
        // Straight check
        var isStraight = false
        var straightHighRank = 0
        val ranks = sorted.map { it.rank.value }.distinct()
        
        // Normal straight check
        for (i in 0..ranks.size - 5) {
            if (ranks[i] - ranks[i + 4] == 4) {
                isStraight = true
                straightHighRank = ranks[i]
                break
            }
        }
        
        // Ace-low straight check (A, 2, 3, 4, 5)
        if (!isStraight && ranks.contains(14) && ranks.contains(2) && ranks.contains(3) && ranks.contains(4) && ranks.contains(5)) {
            isStraight = true
            straightHighRank = 5
        }

        val rankCounts = cards.groupBy { it.rank }.mapValues { it.value.size }
        val counts = rankCounts.values.sortedDescending()
        val pairsAndSets = rankCounts.entries.sortedWith(compareBy({ it.value }, { it.key.value })).reversed()

        return when {
            isFlush && isStraight && straightHighRank == 14 -> {
                HandEvaluation(HandType.ROYAL_FLUSH, "Royal Flush", 1000000)
            }
            isFlush && isStraight -> {
                HandEvaluation(HandType.STRAIGHT_FLUSH, "Straight Flush", 900000 + straightHighRank)
            }
            counts[0] == 4 -> {
                val fourRank = pairsAndSets[0].key.value
                val kicker = pairsAndSets[1].key.value
                HandEvaluation(HandType.FOUR_OF_A_KIND, "Four of a Kind: ${pairsAndSets[0].key.displayName}'s", 800000 + fourRank * 100 + kicker)
            }
            counts[0] == 3 && counts[1] >= 2 -> {
                val threeRank = pairsAndSets[0].key.value
                val pairRank = pairsAndSets[1].key.value
                HandEvaluation(HandType.FULL_HOUSE, "Full House: ${pairsAndSets[0].key.displayName}'s full of ${pairsAndSets[1].key.displayName}'s", 700000 + threeRank * 100 + pairRank)
            }
            isFlush -> {
                val highRanksValue = sorted.fold(0) { acc, c -> acc * 15 + c.rank.value }
                HandEvaluation(HandType.FLUSH, "Flush in ${sorted[0].suit.name.lowercase().capitalize()}", 600000 + sorted[0].rank.value)
            }
            isStraight -> {
                HandEvaluation(HandType.STRAIGHT, "Straight: ${straightHighRank} High", 500000 + straightHighRank)
            }
            counts[0] == 3 -> {
                val threeRank = pairsAndSets[0].key.value
                val k1 = pairsAndSets[1].key.value
                val k2 = pairsAndSets[2].key.value
                HandEvaluation(HandType.THREE_OF_A_KIND, "Three of a Kind: ${pairsAndSets[0].key.displayName}'s", 400000 + threeRank * 100 + k1 + k2)
            }
            counts[0] == 2 && counts[1] == 2 -> {
                val highPair = pairsAndSets[0].key.value
                val lowPair = pairsAndSets[1].key.value
                val kicker = pairsAndSets[2].key.value
                HandEvaluation(HandType.TWO_PAIR, "Two Pair: ${pairsAndSets[0].key.displayName}'s and ${pairsAndSets[1].key.displayName}'s", 300000 + highPair * 1000 + lowPair * 100 + kicker)
            }
            counts[0] == 2 -> {
                val pairRank = pairsAndSets[0].key.value
                val k1 = pairsAndSets[1].key.value
                val k2 = pairsAndSets[2].key.value
                val k3 = pairsAndSets[3].key.value
                HandEvaluation(HandType.ONE_PAIR, "One Pair of ${pairsAndSets[0].key.displayName}'s", 200000 + pairRank * 1000 + k1 * 100 + k2 * 10 + k3)
            }
            else -> {
                val valScore = sorted.take(5).fold(0) { acc, c -> acc * 15 + c.rank.value }
                HandEvaluation(HandType.HIGH_CARD, "High Card: ${sorted[0].rank.displayName}", 100000 + sorted[0].rank.value)
            }
        }
    }

    private fun <T> combinations(list: List<T>, k: Int): List<List<T>> {
        if (k == 0) return listOf(emptyList())
        if (list.isEmpty()) return emptyList()
        val head = list[0]
        val tail = list.drop(1)
        val withHead = combinations(tail, k - 1).map { listOf(head) + it }
        val withoutHead = combinations(tail, k)
        return withHead + withoutHead
    }

    // Dynamic professional poker AI advice based on hole + table cards
    fun getDynamicCoachingAdvice(hole: List<Card>, community: List<Card>): String {
        if (hole.size < 2) return "Waiting for cards..."
        
        val totalCards = hole + community
        val handEval = evaluate7Cards(hole, community)
        val h1 = hole[0]
        val h2 = hole[1]

        val stage = when (community.size) {
            0 -> "PRE_FLOP"
            3 -> "FLOP"
            4 -> "TURN"
            5 -> "RIVER"
            else -> "PLAYING"
        }

        val isPocketPair = h1.rank == h2.rank
        val isSuited = h1.suit == h2.suit
        val distance = Math.abs(h1.rank.value - h2.rank.value)
        val isConnector = distance == 1
        val isSemiConnector = distance == 2

        if (stage == "PRE_FLOP") {
            return when {
                isPocketPair && h1.rank.value >= 10 -> {
                    "★ Premium Starting Hand! You hold a high Pocket Pair of ${h1.rank.displayName}'s. Recommended Action: Raise aggressively to build the pot and eliminate low-potential hands."
                }
                isPocketPair -> {
                    "★ Good Starting Hand. Pocket Pair of ${h1.rank.displayName}'s. Great potential to hit 'Three of a Kind' on the flop. Recommended Action: Call or small raise to see the flop cheaply."
                }
                (h1.rank.value >= 12 && h2.rank.value >= 12) -> {
                    "★ Very Strong starting cards (high cards: ${h1.rank.displayName} & ${h2.rank.displayName}). Excellent showdown value. Recommended Action: Raise or call a reasonable bet."
                }
                isSuited && isConnector && h1.rank.value >= 8 -> {
                    "★ High Potential: Suited connectors (${h1.rank.displayName}${h1.suit.symbol} and ${h2.rank.displayName}${h2.suit.symbol}). High chance for straight or flush draws. Recommended Action: Enter the hand with a limp or standard call."
                }
                isSuited && (h1.rank.value == 14 || h2.rank.value == 14) -> {
                    "★ Strong Ace-Suited cards. Excellent Flush potential with Ace kicker. Recommended Action: Play and look for nut-flush draws on the flop."
                }
                h1.rank.value <= 7 && h2.rank.value <= 7 && !isPocketPair && !isSuited && !isConnector -> {
                    "⚠ Weak Starting Hand. Low unconnected unsuited cards. Recommended Action: Fold unless in the Big Blind and checking is free."
                }
                else -> {
                    "Average starting cards. Recommended Action: Play cautiously. Fold if facing a large pre-flop raise, otherwise check or call to see the flop."
                }
            }
        }

        // FLOP, TURN, or RIVER advice
        val suitsInPlay = totalCards.groupBy { it.suit }.mapValues { it.value.size }
        val hasFlushDraw = suitsInPlay.values.any { it == 4 }
        val hasFlush = suitsInPlay.values.any { it >= 5 }

        // Straight draw check
        val distinctRanks = totalCards.map { it.rank.value }.distinct().sorted()
        var hasStraightDraw = false
        if (distinctRanks.size >= 4) {
            for (i in 0..distinctRanks.size - 4) {
                if (distinctRanks[i + 3] - distinctRanks[i] == 3) {
                    hasStraightDraw = true
                }
            }
        }

        val handRank = handEval.handType

        return when {
            handRank == HandType.ROYAL_FLUSH || handRank == HandType.STRAIGHT_FLUSH -> {
                "🏆 ABSOLUTE NUT HAND! You hold a ${handEval.description}. You are mathematically unbeatable. Recommended Action: Slow play or raise to bait opponents into committing all their chips."
            }
            handRank == HandType.FOUR_OF_A_KIND || handRank == HandType.FULL_HOUSE -> {
                "🔥 Monster Hand! You hold a ${handEval.description}. Recommended Action: Raise or bet heavily. Protect against any back-door draws, but maximize value."
            }
            handRank == HandType.FLUSH -> {
                "🌊 Strong Flush completed! Recommended Action: Bet or raise. Check if anyone is showing signs of a higher flush or a full house, but usually you are way ahead."
            }
            handRank == HandType.STRAIGHT -> {
                "⚡ Straight completed! Recommended Action: Bet confidently. Watch out if there are 3 or more of the same suit on the board which might indicate a Flush."
            }
            handRank == HandType.THREE_OF_A_KIND -> {
                "✨ Strong Hand: ${handEval.description}. Recommended Action: Raise. Excellent value. If on a wet board (flush/straight draws possible), bet high to force draws out."
            }
            handRank == HandType.TWO_PAIR -> {
                "💫 solid Two Pair! Recommended Action: Moderate to high bet. Ensure you don't slow play if the board has flush/straight possibilities."
            }
            handRank == HandType.ONE_PAIR && isPocketPair -> {
                "👍 Overpair / Strong Pocket Pair. Recommended Action: Check or call moderate bets. If opponents show heavy aggression, they might have hit a set or two-pair on the flop."
            }
            handRank == HandType.ONE_PAIR -> {
                val pairValue = totalCards.groupBy { it.rank }.filter { it.value.size == 2 }.keys.firstOrNull()?.value ?: 0
                val topBoardCardValue = community.map { it.rank.value }.maxOrNull() ?: 0
                val isTopPair = pairValue >= topBoardCardValue
                
                if (isTopPair) {
                    "👍 Top Pair! Good middle-strength hand. Recommended Action: Call or small value bet. Avoid over-committing if someone raises big."
                } else {
                    "Pair of ${totalCards.groupBy { it.rank }.filter { it.value.size == 2 }.keys.firstOrNull()?.displayName ?: ""}'s. Underpair to the board. Recommended Action: Play very defensively. Check-fold to major pressure."
                }
            }
            hasFlushDraw && stage != "RIVER" -> {
                "💧 Flush Draw in play (4 suited cards). You need just 1 more card of that suit for a Flush. Recommended Action: Call reasonable bets to chase the flush. Good implied odds."
            }
            hasStraightDraw && stage != "RIVER" -> {
                "🌈 Open-ended Straight Draw. Recommended Action: Call standard bets to see the next card. High potential return if you hit."
            }
            else -> {
                if (stage == "RIVER") {
                    "⚠ No Hand made on the River. You hold only ${handEval.description}. Recommended Action: Unless you want to attempt a high-risk bluff, you should Check-Fold."
                } else {
                    "Nothing made yet. High Card only. Recommended Action: Check or Fold. Do not spend chips chasing unless you have high-rank overcards (Ace/King) with flush backdoor potential."
                }
            }
        }
    }
}
