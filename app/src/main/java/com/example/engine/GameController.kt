package com.example.engine

import com.example.ai.Difficulty
import com.example.ai.PokerBot

class GameController(val totalBots: Int, val difficulty: Difficulty) {
    val deck = Deck()
    val bots = mutableListOf<PokerBot>()
    val communityCards = mutableListOf<Card>()
    val playerHoleCards = mutableListOf<Card>()
    
    var playerChips = 1000
    var pot = 0
    var currentBet = 0
    var gameLog = ""
    
    // Additional state tracking for visual feedback
    var playerFolded = false
    var currentRoundStage = 0 // 0: Flop, 1: Turn, 2: River, 3: Showdown, 4: Round Finished

    init {
        for (i in 1..totalBots) {
            bots.add(PokerBot(id = "Bot-$i", difficulty = difficulty))
        }
    }

    fun startNewRound() {
        deck.reset()
        communityCards.clear()
        playerHoleCards.clear()
        
        // Initial contribution (Blinds)
        pot = 40
        playerChips -= 20
        playerFolded = false
        currentRoundStage = 0
        currentBet = 20
        
        // Deal player hole cards
        playerHoleCards.add(deck.deal())
        playerHoleCards.add(deck.deal())
        
        // Deal bot hole cards
        for (bot in bots) {
            bot.holeCards.clear()
            bot.isFolded = false
            bot.chips -= 20
            bot.holeCards.add(deck.deal())
            bot.holeCards.add(deck.deal())
        }
        
        // Deal Flop layout immediately (3 community cards)
        communityCards.add(deck.deal())
        communityCards.add(deck.deal())
        communityCards.add(deck.deal())
        
        gameLog = "Round started vs $totalBots ${difficulty.name} bots.\nBlinds posted. Flop dealt!"
    }

    fun processBotTurns() {
        val sb = StringBuilder(gameLog)
        
        if (currentRoundStage == 0) {
            // Pre-turn: Flop round bot actions
            sb.append("\n\n--- [FLOP ROUND] BOT DECISIONS ---")
            for (bot in bots) {
                if (!bot.isFolded) {
                    val action = bot.makeDecision(communityCards, currentBet, missingContribution = 20)
                    when (action) {
                        is com.example.ai.BotAction.Fold -> {
                            bot.isFolded = true
                            sb.append("\n${bot.id} FOLDED.")
                        }
                        is com.example.ai.BotAction.Check -> {
                            sb.append("\n${bot.id} CHECKED.")
                        }
                        is com.example.ai.BotAction.Call -> {
                            bot.chips -= 20
                            pot += 20
                            sb.append("\n${bot.id} CALLED (20 chips).")
                        }
                        is com.example.ai.BotAction.Raise -> {
                            val rAmt = action.amount
                            bot.chips -= rAmt
                            pot += rAmt
                            sb.append("\n${bot.id} RAISED by $rAmt.")
                        }
                    }
                }
            }
            
            // Progress to Turn stage
            currentRoundStage = 1
            if (communityCards.size == 3) {
                val turnCard = deck.deal()
                communityCards.add(turnCard)
                sb.append("\n\n--- [TURN DEALT] ---")
                sb.append("\nCommunity: ${turnCard.rank} of ${turnCard.suit}")
            }
        } else if (currentRoundStage == 1) {
            // Turn round bot actions
            sb.append("\n\n--- [TURN ROUND] BOT DECISIONS ---")
            for (bot in bots) {
                if (!bot.isFolded) {
                    val action = bot.makeDecision(communityCards, currentBet, missingContribution = 20)
                    when (action) {
                        is com.example.ai.BotAction.Fold -> {
                            bot.isFolded = true
                            sb.append("\n${bot.id} FOLDED.")
                        }
                        is com.example.ai.BotAction.Check -> {
                            sb.append("\n${bot.id} CHECKED.")
                        }
                        is com.example.ai.BotAction.Call -> {
                            bot.chips -= 20
                            pot += 20
                            sb.append("\n${bot.id} CALLED.")
                        }
                        is com.example.ai.BotAction.Raise -> {
                            val rAmt = action.amount
                            bot.chips -= rAmt
                            pot += rAmt
                            sb.append("\n${bot.id} RAISED by $rAmt.")
                        }
                    }
                }
            }
            
            // Progress to River stage
            currentRoundStage = 2
            if (communityCards.size == 4) {
                val riverCard = deck.deal()
                communityCards.add(riverCard)
                sb.append("\n\n--- [RIVER DEALT] ---")
                sb.append("\nCommunity: ${riverCard.rank} of ${riverCard.suit}")
            }
        } else if (currentRoundStage == 2) {
            // River round bot actions
            sb.append("\n\n--- [RIVER ROUND] BOT DECISIONS ---")
            for (bot in bots) {
                if (!bot.isFolded) {
                    val action = bot.makeDecision(communityCards, currentBet, missingContribution = 20)
                    when (action) {
                        is com.example.ai.BotAction.Fold -> {
                            bot.isFolded = true
                            sb.append("\n${bot.id} FOLDED.")
                        }
                        is com.example.ai.BotAction.Check -> {
                            sb.append("\n${bot.id} CHECKED.")
                        }
                        is com.example.ai.BotAction.Call -> {
                            bot.chips -= 20
                            pot += 20
                            sb.append("\n${bot.id} CALLED.")
                        }
                        is com.example.ai.BotAction.Raise -> {
                            val rAmt = action.amount
                            bot.chips -= rAmt
                            pot += rAmt
                            sb.append("\n${bot.id} RAISED by $rAmt.")
                        }
                    }
                }
            }
            
            // Progress to Showdown
            currentRoundStage = 3
            sb.append("\n\n--- [SHOWDOWN] ---")
            
            val activeBots = bots.filter { !it.isFolded }
            if (playerFolded) {
                if (activeBots.isEmpty()) {
                    sb.append("\nEveryone folded. Nobody wins.")
                } else {
                    val winner = activeBots.maxByOrNull { HandEvaluator.evaluate(it.holeCards + communityCards).rank.ordinal }
                    if (winner != null) {
                        winner.chips += pot
                        val eval = HandEvaluator.evaluate(winner.holeCards + communityCards)
                        sb.append("\n${winner.id} wins the pot of $pot chips with ${eval.rank}!")
                    }
                }
            } else {
                val playerEval = HandEvaluator.evaluate(playerHoleCards + communityCards)
                sb.append("\nYour hand strength: ${playerEval.rank}")
                
                var maxRankOrdinal = playerEval.rank.ordinal
                var winningBot: PokerBot? = null
                
                for (bot in activeBots) {
                    val botEval = HandEvaluator.evaluate(bot.holeCards + communityCards)
                    sb.append("\n${bot.id} hand strength: ${botEval.rank}")
                    if (botEval.rank.ordinal > maxRankOrdinal) {
                        maxRankOrdinal = botEval.rank.ordinal
                        winningBot = bot
                    }
                }
                
                if (winningBot != null) {
                    winningBot.chips += pot
                    sb.append("\n\n🏆 Winner: ${winningBot.id} wins $pot chips!")
                } else {
                    playerChips += pot
                    sb.append("\n\n🏆 Winner: You win $pot chips with ${playerEval.rank}!")
                }
            }
            currentRoundStage = 4
        } else {
            // Reset and start new round
            startNewRound()
            sb.setLength(0)
            sb.append(gameLog)
        }
        
        gameLog = sb.toString()
    }
}
