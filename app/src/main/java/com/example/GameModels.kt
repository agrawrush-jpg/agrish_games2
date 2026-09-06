package com.example.web

import java.util.UUID

// --- General Models ---
enum class GameType { NONE, POKER, BANK }

data class Player(
    val id: String,
    val name: String,
    val chips: Int,
    val currentBet: Int = 0,
    val isFolded: Boolean = false,
    val isDealer: Boolean = false,
    val isSmallBlind: Boolean = false,
    val isBigBlind: Boolean = false
)

// --- Card representation ---
enum class Suit(val symbol: String, val color: String) {
    SPADES("♠", "#E2E8F0"),
    HEARTS("♥", "#F87171"),
    DIAMONDS("♦", "#60A5FA"),
    CLUBS("♣", "#34D399")
}

enum class Rank(val value: Int, val displayName: String) {
    TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
    SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"),
    TEN(10, "10"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"),
    ACE(14, "A")
}

data class Card(val suit: Suit, val rank: Rank) {
    override fun toString(): String = "${rank.displayName}${suit.symbol}"
}

// --- Poker Game Logic & States ---
enum class PokerStage(val displayName: String) {
    PRE_FLOP("Pre-Flop"),
    FLOP("Flop"),
    TURN("Turn"),
    RIVER("River"),
    SHOWDOWN("Showdown")
}

data class PokerState(
    val hostChips: Int = 1000,
    val clientChips: Int = 1000,
    val hostBet: Int = 0,
    val clientBet: Int = 0,
    val pot: Int = 0,
    val currentBetToMatch: Int = 0,
    val stage: PokerStage = PokerStage.PRE_FLOP,
    val hostHoleCards: List<Card> = emptyList(),
    val clientHoleCards: List<Card> = emptyList(),
    val communityCards: List<Card> = emptyList(),
    val currentTurn: String = "Host", // "Host" or "Client"
    val isHostDealer: Boolean = true,
    val winner: String? = null, // "Host", "Client", or "Tie"
    val showWinnerOverlay: Boolean = false,
    val suggestions: String = "Welcome to Texas Hold'em. Waiting for cards...",
    val clientConnected: Boolean = false,
    val clientName: String = "Guest"
)

// --- Bank Game Logic & States ---
data class RollHistoryItem(
    val player: String,
    val roll: Int,
    val resultText: String
)

data class BankState(
    val round: Int = 1,
    val bankValue: Int = 0,
    val hostScore: Int = 0,
    val clientScore: Int = 0,
    val currentTurn: String = "Host", // "Host" or "Client"
    val rolls: List<RollHistoryItem> = emptyList(),
    val isHostBanked: Boolean = false,
    val isClientBanked: Boolean = false,
    val lastWinnerName: String? = null,
    val clientConnected: Boolean = false,
    val clientName: String = "Guest",
    val gameEnded: Boolean = false
)
