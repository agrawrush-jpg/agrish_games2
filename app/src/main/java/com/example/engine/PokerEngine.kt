package com.example.engine

enum class Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
enum class Rank(val value: Int) {
    TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), 
    EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13), ACE(14)
}

data class Card(val rank: Rank, val suit: Suit) {
    fun getSymbol(): String {
        return when (suit) {
            Suit.CLUBS -> "♣"
            Suit.DIAMONDS -> "♦"
            Suit.HEARTS -> "♥"
            Suit.SPADES -> "♠"
        }
    }
}

class Deck {
    private val cards = mutableListOf<Card>()
    
    init {
        reset()
    }
    fun reset() {
        cards.clear()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                cards.add(Card(rank, suit))
            }
        }
        cards.shuffle()
    }
    
    fun deal(): Card = if (cards.isNotEmpty()) cards.removeAt(0) else throw IllegalStateException("Deck is empty")
}

enum class HandRank { HIGH_CARD, PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH }

data class HandEvaluation(val rank: HandRank, val tieBreakers: List<Int>)

object HandEvaluator {
    // Basic evaluation for Texas Hold'em (simplified 5-7 card processing)
    fun evaluate(cards: List<Card>): HandEvaluation {
        val sorted = cards.sortedByDescending { it.rank.value }
        val rankCounts = sorted.groupBy { it.rank }.mapValues { it.value.size }
        val suitCounts = sorted.groupBy { it.suit }.mapValues { it.value.size }
        
        val isFlush = suitCounts.any { it.value >= 5 }
        val uniqueRanks = sorted.map { it.rank.value }.distinct()
        
        // Simplified Straight Detection
        var isStraight = false
        var straightHigh = 0
        for (i in 0..uniqueRanks.size - 5) {
            if (uniqueRanks[i] - uniqueRanks[i + 4] == 4) {
                isStraight = true
                straightHigh = uniqueRanks[i]
                break
            }
        }
        
        return when {
            isFlush && isStraight -> HandEvaluation(HandRank.STRAIGHT_FLUSH, listOf(straightHigh))
            rankCounts.containsValue(4) -> HandEvaluation(HandRank.FOUR_OF_A_KIND, listOf(rankCounts.filterValues { it == 4 }.keys.first().value))
            rankCounts.containsValue(3) && rankCounts.containsValue(2) -> HandEvaluation(HandRank.FULL_HOUSE, listOf())
            isFlush -> HandEvaluation(HandRank.FLUSH, sorted.map { it.rank.value }.take(5))
            isStraight -> HandEvaluation(HandRank.STRAIGHT, listOf(straightHigh))
            rankCounts.containsValue(3) -> HandEvaluation(HandRank.THREE_OF_A_KIND, listOf(rankCounts.filterValues { it == 3 }.keys.first().value))
            rankCounts.filterValues { it == 2 }.size >= 2 -> HandEvaluation(HandRank.TWO_PAIR, rankCounts.filterValues { it == 2 }.keys.map { it.value }.sortedDescending())
            rankCounts.containsValue(2) -> HandEvaluation(HandRank.PAIR, listOf(rankCounts.filterValues { it == 2 }.keys.first().value))
            else -> HandEvaluation(HandRank.HIGH_CARD, sorted.map { it.rank.value }.take(5))
        }
    }
}
