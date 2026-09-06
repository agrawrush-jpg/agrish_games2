package com.example.ai

import com.example.engine.Card
import com.example.engine.HandEvaluator
import com.example.engine.HandRank
import kotlin.random.Random

enum class Difficulty { BEGINNER, EASY, MEDIUM, HARD, EXPERT }

class PokerBot(val id: String, val difficulty: Difficulty) {
    var chips: Int = 1000
    val holeCards = mutableListOf<Card>()
    var isFolded: Boolean = false

    fun makeDecision(communityCards: List<Card>, currentBet: Int, missingContribution: Int): BotAction {
        if (isFolded) return BotAction.Fold
        
        val totalCards = holeCards + communityCards
        val eval = HandEvaluator.evaluate(totalCards)
        
        // Introduce variance/mistakes based on difficulty
        val randomness = Random.nextFloat()
        
        return funDecision(eval.rank, missingContribution, currentBet, randomness)
    }

    private fun funDecision(rank: HandRank, missing: Int, currentBet: Int, rand: Float): BotAction {
        return when (difficulty) {
            Difficulty.BEGINNER -> {
                // Highly random, calls almost anything, rarely folds
                if (rand < 0.4f) BotAction.Call 
                else if (rand < 0.7f && missing == 0) BotAction.Check
                else if (rank >= HandRank.PAIR) BotAction.Raise(missing + 20)
                else BotAction.Fold
            }
            Difficulty.EASY -> {
                if (rank >= HandRank.PAIR) BotAction.Call
                else if (missing <= 40) BotAction.Call
                else BotAction.Fold
            }
            Difficulty.MEDIUM -> {
                if (rank >= HandRank.TWO_PAIR) BotAction.Raise(missing + 50)
                else if (rank == HandRank.PAIR && missing <= 60) BotAction.Call
                else if (missing == 0) BotAction.Check
                else BotAction.Fold
            }
            Difficulty.HARD -> {
                // Plays tightly, folds weak high cards under pressure
                if (rank >= HandRank.THREE_OF_A_KIND) BotAction.Raise(missing + 100)
                else if (rank >= HandRank.PAIR && missing <= 100) BotAction.Call
                else if (missing == 0) BotAction.Check
                else BotAction.Fold
            }
            Difficulty.EXPERT -> {
                // Mathematically aggressive, bluffs occasionally (5% of the time)
                if (rand < 0.05f) BotAction.Raise(missing + 150) 
                else if (rank >= HandRank.THREE_OF_A_KIND) BotAction.Raise(missing + 200)
                else if (rank >= HandRank.TWO_PAIR) BotAction.Call
                else if (rank == HandRank.PAIR && missing <= 40) BotAction.Call
                else if (missing == 0) BotAction.Check
                else BotAction.Fold
            }
        }
    }
}

sealed class BotAction {
    object Fold : BotAction()
    object Check : BotAction()
    object Call : BotAction()
    data class Raise(val amount: Int) : BotAction()
}
