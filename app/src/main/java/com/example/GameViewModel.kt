package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// --- Models ---

enum class CardSuit(val symbol: String, val colorRed: Boolean, val displayName: String) {
    HEARTS("♥", true, "Hearts"),
    DIAMONDS("♦", true, "Diamonds"),
    CLUBS("♣", false, "Clubs"),
    SPADES("♠", false, "Spades")
}

enum class CardRank(val display: String, val value: Int) {
    ACE("A", 1), TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9),
    TEN("10", 10), JACK("J", 11), QUEEN("Q", 12), KING("K", 13)
}

data class Card(val suit: CardSuit, val rank: CardRank) {
    override fun toString(): String {
        return "${rank.display}${suit.symbol}"
    }
}

data class Player(
    val id: String,
    val name: String,
    val initialContribution: Int,
    val isInitialized: Boolean = false,
    val currentBalance: Int = 0,
    val playMoneyInput: String = "",
    val savingInput: String = "",
    val warningInput: String = "", // extra saving input in error box
    val borrowAmountInput: String = "",
    val returnAmountInput: String = "",
    val warningMessage: String? = null,
    val minNeededToContinue: Int? = null,
    val debts: Map<String, Int> = emptyMap(), // Creditor Player ID -> Amount
    val colorIndex: Int = 0,
    
    // Stats for final summary log
    val totalWon: Int = 0,
    val totalLost: Int = 0,
    val totalBorrowed: Int = 0,
    val totalLended: Int = 0,
    val totalReturned: Int = 0,
    val totalSavingsAdded: Int = 0,
    val totalMassContributionsPaid: Int = 0
)

data class LogEvent(
    val seqNo: Int?, // For play sequence win/lose
    val eventType: String, // "GAME_START", "INITIALIZE", "WIN", "LOSE", "SAVING_ADD", "BORROW", "RETURN", "QUIT", "BANK_TOPUP", "NEW_PLAYER"
    val playerName: String,
    val detail: String,
    val amount: Int,
    val balanceBefore: Int,
    val balanceAfter: Int,
    val timestamp: String
)

data class DealSequence(
    val sequenceNumber: Int,
    val leftCard: Card,
    val rightCard: Card,
    val middleCard: Card? = null,
    val result: String? = null
)

sealed interface GameScreen {
    object Welcome : GameScreen
    object Setup : GameScreen
    object Lobby : GameScreen
    object OrderWheel : GameScreen
    object Play : GameScreen
    object PokerWelcome : GameScreen
    object PokerSetup : GameScreen
    object PokerLobby : GameScreen
    object PokerPlay : GameScreen
}

enum class HandRank(val displayName: String, val value: Int) {
    ROYAL_FLUSH("Royal Flush", 10),
    STRAIGHT_FLUSH("Straight Flush", 9),
    FOUR_OF_A_KIND("Four of a Kind", 8),
    FULL_HOUSE("Full House", 7),
    FLUSH("Flush", 6),
    STRAIGHT("Straight", 5),
    THREE_OF_A_KIND("Three of a Kind", 4),
    TWO_PAIR("Two Pair", 3),
    ONE_PAIR("Pair", 2),
    HIGH_CARD("High Card", 1)
}

data class HandEvaluation(
    val rank: HandRank,
    val values: List<Int>,
    val description: String
) : Comparable<HandEvaluation> {
    override fun compareTo(other: HandEvaluation): Int {
        if (this.rank.value != other.rank.value) {
            return this.rank.value.compareTo(other.rank.value)
        }
        for (i in 0 until minOf(this.values.size, other.values.size)) {
            if (this.values[i] != other.values[i]) {
                return this.values[i].compareTo(other.values[i])
            }
        }
        return 0
    }
}

data class PokerPlayer(
    val id: String,
    val name: String,
    val chips: Int,
    val currentBet: Int = 0,
    val cards: List<Card> = emptyList(),
    val hasFolded: Boolean = false,
    val isAllIn: Boolean = false,
    val isDealer: Boolean = false,
    val isSmallBlind: Boolean = false,
    val isBigBlind: Boolean = false,
    val isAI: Boolean = false,
    val avatarIndex: Int = 0,
    val hasActedThisRound: Boolean = false,
    val lastAction: String? = null
)

data class GameState(
    val screen: GameScreen = GameScreen.Welcome,
    val selectedGameMode: String = "NONE", // "NONE", "BANK", "POKER"
    val playModeSelection: String = "NONE", // "NONE", "DEVICE", "WIFI"
    
    // Poker Specific State
    val pokerPlayers: List<PokerPlayer> = emptyList(),
    val pokerCommunityCards: List<Card> = emptyList(),
    val pokerDeck: List<Card> = emptyList(),
    val pokerPot: Int = 0,
    val pokerCurrentBet: Int = 0,
    val pokerActivePlayerIndex: Int = 0,
    val pokerStage: String = "PRE_FLOP", // "PRE_FLOP", "FLOP", "TURN", "RIVER", "SHOWDOWN", "FINISHED"
    val pokerDealerIndex: Int = 0,
    val pokerLogs: List<String> = emptyList(),
    val pokerMinPlayersInput: String = "3",
    val pokerInitialChipsInput: String = "1000",
    val pokerInitialChips: Int = 1000,
    val pokerTotalChipsAdded: Int = 0,
    val pokerBetAmountInput: String = "",
    val pokerAiCountInput: String = "3",
    val isPokerMultiplayer: Boolean = false,

    val numPlayersInput: String = "3",
    val gameMoneyInput: String = "100", // መደብ
    val gameMoneyAmount: Int = 100, // መደብ parsed
    val bankAmount: Int = 0,
    val players: List<Player> = emptyList(),
    val logEvents: List<LogEvent> = emptyList(),
    val activeTab: String = "BANK", // "BANK" or "CARDS"
    val sequenceCounter: Int = 0,
    
    // Transient and Log fields
    val toastMessage: String? = null,
    val cardLogHistory: String = "",
    val shuffleCount: Int = 0,
    
    // Cards State
    val leftDeck: List<Card> = emptyList(),
    val rightDeck: List<Card> = emptyList(),
    val leftDealt: List<Card> = emptyList(),
    val rightDealt: List<Card> = emptyList(),
    val middleCard: Card? = null,
    val nextPlayDeckLeft: Boolean = true,
    
    // Bottom level Add to Bank inputs
    val bottomAddToBankInput: String = "",
    
    // New Player form inputs
    val newPlayerName: String = "",
    val newPlayerInitialSaving: String = "",
    val isAddingNewPlayer: Boolean = false,

    // Effects and overlays triggers
    val winEffectPlayerName: String? = null,
    val loseEffectPlayerName: String? = null,
    val cardsPlayResult: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val effectTriggerId: Long = 0L,
    val glowLeft: Boolean = false,
    val glowRight: Boolean = false,
    val glowMiddle: Boolean = false,
    val dealSequences: List<DealSequence> = emptyList(),
    // Settings
    val settingsOpen: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val playerBoxLayout: String = "horizontal", // "horizontal", "vertical", "grid"

    // Multiplayer specifics
    val isMultiplayer: Boolean = false,
    val isHost: Boolean = false,
    val localPlayerId: String? = null,
    val gameId: String = "",
    val multiplayerTurnState: String = "WAITING", // "WAITING", "DEAL_TWO_CARDS", "PLAY_OR_PASS", "INPUT_AMOUNT", "DEAL_MIDDLE", "RESULT", "WHEEL_SPINNING"
    val activeTurnPlayerId: String? = null,
    val currentPlayAmountInput: String = "",
    val isSpinningWheel: Boolean = false,
    val wheelAnimationAngle: Float = 0f,
    val wheelSelectedPlayerName: String? = null,
    val wheelSelectedPlayerId: String? = null,
    val orderedPlayerIds: List<String> = emptyList(),
    val lobbyPlayers: List<String> = emptyList(),
    val wheelMessage: String = "",
    val canResume: Boolean = false,
    val savedScreenString: String = "Welcome",
    val isDarkTheme: Boolean = true,
    val pokerLastActorId: String? = null,
    val pokerBotDifficulty: String = "Medium",
    val pokerTableWidthScale: Float = 1.0f,
    val pokerTableHeight: Int = 130,
    val isLocalWifi: Boolean = false
)

data class ShuffledDeck(val cards: List<Card>, val algorithmName: String)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("AgrishPrefs", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val historyList = mutableListOf<GameState>()
    private var historyIndex = -1

    private val database = AppDatabase.getDatabase(application)
    private val gameSessionDao = database.gameSessionDao()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val stateAdapter = moshi.adapter(SavedStateDto::class.java)

    init {
        val savedSound = prefs.getBoolean("sound_enabled", true)
        val savedVib = prefs.getBoolean("vibration_enabled", true)
        val savedMusic = prefs.getBoolean("music_enabled", true)
        val savedDark = prefs.getBoolean("dark_theme", true)
        val savedLayout = prefs.getString("player_box_layout", "horizontal") ?: "horizontal"

        SoundPlayer.soundEnabled = savedSound
        SoundPlayer.vibrationEnabled = savedVib
        SoundPlayer.startMusicEnabled = savedMusic

        _state.update {
            it.copy(
                soundEnabled = savedSound,
                vibrationEnabled = savedVib,
                isDarkTheme = savedDark,
                playerBoxLayout = savedLayout
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedSession = gameSessionDao.getSession()
                if (savedSession != null) {
                    val dto = stateAdapter.fromJson(savedSession.stateJson)
                    if (dto != null) {
                        val domainState = dto.toDomain()
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            val savedScr = when (domainState.screen) {
                                is GameScreen.Setup -> "Setup"
                                is GameScreen.Lobby -> "Lobby"
                                is GameScreen.OrderWheel -> "OrderWheel"
                                is GameScreen.Play -> "Play"
                                else -> "Welcome"
                            }
                            val canRes = savedScr != "Welcome"
                            
                            val finalState = domainState.copy(
                                screen = GameScreen.Welcome,
                                canResume = canRes,
                                savedScreenString = savedScr,
                                soundEnabled = savedSound,
                                vibrationEnabled = savedVib,
                                isDarkTheme = savedDark,
                                playerBoxLayout = savedLayout
                            )
                            _state.value = finalState
                            historyList.clear()
                            historyList.add(finalState)
                            historyIndex = 0
                        }
                    } else {
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            resetCardDecks()
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        resetCardDecks()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        resetCardDecks()
                    }
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }

            launch(Dispatchers.IO) {
                _state.collect { currentState ->
                    try {
                        val dto = currentState.toDto()
                        val json = stateAdapter.toJson(dto)
                        gameSessionDao.saveSession(GameSessionEntity(stateJson = json))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun resetFullGame() {
        val shuffledDeck = createCombinedDeck()
        val combinedDeck = shuffledDeck.cards
        val firstShuffleHeader = "Shuffle #1 (Algorithm: ${shuffledDeck.algorithmName}) - Time: ${getCurrentTimeString()}"

        historyList.clear()
        historyIndex = -1

        val newState = GameState(
            screen = GameScreen.Setup,
            selectedGameMode = _state.value.selectedGameMode,
            numPlayersInput = "3",
            gameMoneyInput = "100",
            gameMoneyAmount = 100,
            leftDeck = combinedDeck,
            rightDeck = combinedDeck,
            cardLogHistory = firstShuffleHeader,
            shuffleCount = 1
        )
        _state.value = newState
        historyList.add(newState)
        historyIndex = 0
    }

    private fun recordHistory(newState: GameState) {
        // Truncate any forward redo history if we are overwriting
        if (historyIndex >= 0 && historyIndex < historyList.size - 1) {
            val toKeep = historyList.subList(0, historyIndex + 1).toList()
            historyList.clear()
            historyList.addAll(toKeep)
        }
        historyList.add(newState)
        historyIndex = historyList.size - 1
        _state.value = newState.copy(
            canGoBack = historyIndex > 0,
            canGoForward = false
        )
    }

    fun goBack() {
        if (historyIndex > 0) {
            historyIndex--
            val pastState = historyList[historyIndex]
            _state.value = pastState.copy(
                canGoBack = historyIndex > 0,
                canGoForward = historyIndex < historyList.size - 1
            )
        }
    }

    fun goForward() {
        if (historyIndex < historyList.size - 1) {
            historyIndex++
            val futureState = historyList[historyIndex]
            _state.value = futureState.copy(
                canGoBack = historyIndex > 0,
                canGoForward = historyIndex < historyList.size - 1
            )
        }
    }

    private fun getCurrentTimeString(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun updateScreen(screen: GameScreen) {
        _state.update { it.copy(screen = screen) }
    }

    fun resumeGame() {
        val targetScreen = when (_state.value.savedScreenString) {
            "Setup" -> GameScreen.Setup
            "Lobby" -> GameScreen.Lobby
            "OrderWheel" -> GameScreen.OrderWheel
            "Play" -> GameScreen.Play
            else -> GameScreen.Welcome
        }
        _state.update {
            it.copy(
                screen = targetScreen,
                canResume = false
            )
        }
    }

    // --- Tab Selection ---
    fun selectTab(tab: String) {
        _state.update { it.copy(activeTab = tab) }
    }

    fun clearToastMessage() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun clearActiveEffects() {
        _state.update { s ->
            s.copy(
                winEffectPlayerName = null,
                loseEffectPlayerName = null,
                cardsPlayResult = null
            )
        }
    }

    // --- Game Selection & Poker Setup ---
    fun selectPlayMode(mode: String) {
        _state.update {
            it.copy(playModeSelection = mode)
        }
    }

    fun selectGame(mode: String) {
        _state.update {
            it.copy(
                selectedGameMode = mode,
                screen = if (mode == "BANK") GameScreen.Welcome else if (mode == "POKER") GameScreen.PokerWelcome else GameScreen.Welcome
            )
        }
    }

    fun exitGameSelection() {
        _state.update {
            it.copy(
                selectedGameMode = "NONE",
                playModeSelection = "NONE",
                screen = GameScreen.Welcome
            )
        }
    }

    fun updatePokerSetupInputs(minPlayers: String, initialChips: String, aiCount: String) {
        _state.update {
            it.copy(
                pokerMinPlayersInput = minPlayers,
                pokerInitialChipsInput = initialChips,
                pokerAiCountInput = aiCount
            )
        }
    }

    fun startPokerSoloGame(aiCount: Int, initialChips: Int, playerName: String, difficulty: String = "Medium") {
        val human = PokerPlayer(
            id = "player_human",
            name = playerName.ifBlank { "You" },
            chips = initialChips,
            isAI = false,
            avatarIndex = kotlin.random.Random.nextInt(15)
        )
        val aiNames = listOf("Sophia 🤖", "Ethan 🤖", "Olivia 🤖", "Liam 🤖", "Emma 🤖")
        val ais = List(aiCount) { idx ->
            PokerPlayer(
                id = "player_ai_$idx",
                name = aiNames[idx % aiNames.size],
                chips = initialChips,
                isAI = true,
                avatarIndex = kotlin.random.Random.nextInt(15)
            )
        }
        val allPlayers = listOf(human) + ais
        
        _state.update {
            it.copy(
                pokerPlayers = allPlayers,
                pokerInitialChips = initialChips,
                pokerBotDifficulty = difficulty,
                screen = GameScreen.PokerPlay,
                pokerLogs = listOf("Poker Solo Game started with human and $aiCount bots ($difficulty difficulty)!"),
                isPokerMultiplayer = false,
                isMultiplayer = false
            )
        }
        startPokerHand()
    }

    fun hostPokerGame(playerName: String, initialChips: Int) {
        val gId = generateRandomGameId()
        localPlayerName = playerName
        
        _state.update {
            it.copy(
                isPokerMultiplayer = true,
                isMultiplayer = true,
                isHost = true,
                localPlayerId = "player_1",
                gameId = gId,
                lobbyPlayers = listOf(playerName),
                pokerInitialChips = initialChips,
                screen = GameScreen.PokerLobby
            )
        }
        
        startFirebaseHostSync(gId)
    }

    fun startPokerLobbyGame() {
        if (!_state.value.isHost) return
        val chips = _state.value.pokerInitialChips
        val updatedPlayers = _state.value.lobbyPlayers.mapIndexed { idx, name ->
            PokerPlayer(
                id = "player_${idx + 1}",
                name = name,
                chips = chips,
                isAI = false,
                avatarIndex = idx % 6
            )
        }
        
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = listOf("Poker Multiplayer Game started with ${updatedPlayers.size} players!"),
                screen = GameScreen.PokerPlay
            )
        }
        startPokerHand()
    }

    fun startPokerHand() {
        if (!_state.value.isHost && _state.value.isPokerMultiplayer) {
            sendActionToHost("POKER_START_HAND")
            return
        }
        val deck = CardSuit.values().flatMap { suit ->
            CardRank.values().map { rank -> Card(suit, rank) }
        }.shuffled().toMutableList()
        
        val stateVal = _state.value
        val playersCount = stateVal.pokerPlayers.size
        if (playersCount == 0) return
        
        val newDealerIdx = (stateVal.pokerDealerIndex + 1) % playersCount
        
        // Small and Big Blinds
        val sbIdx = (newDealerIdx + 1) % playersCount
        val bbIdx = (newDealerIdx + 2) % playersCount
        
        val sbAmount = 10
        val bbAmount = 20
        
        val updatedPlayers = stateVal.pokerPlayers.mapIndexed { idx, p ->
            val isSB = idx == sbIdx
            val isBB = idx == bbIdx
            val isD = idx == newDealerIdx
            
            val initialBet = if (isSB) minOf(sbAmount, p.chips) else if (isBB) minOf(bbAmount, p.chips) else 0
            val cards = listOf(deck.removeAt(0), deck.removeAt(0))
            
            p.copy(
                cards = cards,
                chips = p.chips - initialBet,
                currentBet = initialBet,
                hasFolded = p.chips <= 0,
                isAllIn = p.chips - initialBet <= 0,
                isDealer = isD,
                isSmallBlind = isSB,
                isBigBlind = isBB,
                hasActedThisRound = false,
                avatarIndex = kotlin.random.Random.nextInt(15),
                lastAction = null
            )
        }
        
        val logs = listOf(
            "New hand started.",
            "${updatedPlayers[sbIdx].name} posted Small Blind (10).",
            "${updatedPlayers[bbIdx].name} posted Big Blind (20)."
        )
        
        _state.update {
            it.copy(
                pokerDeck = deck,
                pokerCommunityCards = emptyList(),
                pokerPlayers = updatedPlayers,
                pokerStage = "PRE_FLOP",
                pokerDealerIndex = newDealerIdx,
                pokerPot = sbAmount + bbAmount,
                pokerCurrentBet = bbAmount,
                pokerLogs = logs,
                pokerActivePlayerIndex = (bbIdx + 1) % playersCount,
                pokerLastActorId = null
            )
        }
        
        SoundPlayer.playDealSound()
        
        broadcastState()
        
        // If the active player is an AI, trigger AI action
        val activePlayer = _state.value.pokerPlayers[_state.value.pokerActivePlayerIndex]
        if (activePlayer.isAI) {
            triggerAiAction()
        }
    }

    fun pokerFold() {
        if (!_state.value.isHost && _state.value.isPokerMultiplayer) {
            sendActionToHost("POKER_FOLD")
            return
        }
        val activeIdx = _state.value.pokerActivePlayerIndex
        val activePlayer = _state.value.pokerPlayers.getOrNull(activeIdx) ?: return
        
        val updatedPlayers = _state.value.pokerPlayers.mapIndexed { index, p ->
            if (index == activeIdx) p.copy(hasFolded = true, hasActedThisRound = true, lastAction = "FOLD") else p
        }
        val logs = _state.value.pokerLogs + "${activePlayer.name} folded."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerLastActorId = activePlayer.id
            )
        }
        checkPokerFoldWinOrAdvance()
    }

    fun pokerCheck() {
        if (!_state.value.isHost && _state.value.isPokerMultiplayer) {
            sendActionToHost("POKER_CHECK")
            return
        }
        val activeIdx = _state.value.pokerActivePlayerIndex
        val activePlayer = _state.value.pokerPlayers.getOrNull(activeIdx) ?: return
        
        val updatedPlayers = _state.value.pokerPlayers.mapIndexed { index, p ->
            if (index == activeIdx) p.copy(hasActedThisRound = true, lastAction = "CHECK") else p
        }
        val logs = _state.value.pokerLogs + "${activePlayer.name} checked."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerLastActorId = activePlayer.id
            )
        }
        advancePokerTurn()
    }

    fun pokerCall() {
        if (!_state.value.isHost && _state.value.isPokerMultiplayer) {
            sendActionToHost("POKER_CALL")
            return
        }
        val activeIdx = _state.value.pokerActivePlayerIndex
        val activePlayer = _state.value.pokerPlayers.getOrNull(activeIdx) ?: return
        
        val callAmount = _state.value.pokerCurrentBet - activePlayer.currentBet
        val actualCost = minOf(callAmount, activePlayer.chips)
        
        val updatedPlayers = _state.value.pokerPlayers.mapIndexed { index, p ->
            if (index == activeIdx) {
                p.copy(
                    chips = p.chips - actualCost,
                    currentBet = p.currentBet + actualCost,
                    hasActedThisRound = true,
                    isAllIn = p.chips - actualCost == 0,
                    lastAction = "CALL $actualCost"
                )
            } else p
        }
        val logs = _state.value.pokerLogs + "${activePlayer.name} called $actualCost."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerPot = it.pokerPot + actualCost,
                pokerLastActorId = activePlayer.id
            )
        }
        advancePokerTurn()
    }

    fun pokerRaise(amount: Int) {
        if (!_state.value.isHost && _state.value.isPokerMultiplayer) {
            sendActionToHost("POKER_RAISE:$amount")
            return
        }
        val activeIdx = _state.value.pokerActivePlayerIndex
        val activePlayer = _state.value.pokerPlayers.getOrNull(activeIdx) ?: return
        
        val callAmount = _state.value.pokerCurrentBet - activePlayer.currentBet
        val totalRaise = callAmount + amount
        val actualCost = minOf(totalRaise, activePlayer.chips)
        
        val updatedPlayers = _state.value.pokerPlayers.mapIndexed { index, p ->
            if (index == activeIdx) {
                p.copy(
                    chips = p.chips - actualCost,
                    currentBet = p.currentBet + actualCost,
                    hasActedThisRound = true,
                    isAllIn = p.chips - actualCost == 0,
                    lastAction = "RAISE $amount"
                )
            } else {
                if (!p.hasFolded && !p.isAllIn) p.copy(hasActedThisRound = false) else p
            }
        }
        val logs = _state.value.pokerLogs + "${activePlayer.name} raised by $amount."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerCurrentBet = it.pokerCurrentBet + amount,
                pokerPot = it.pokerPot + actualCost,
                pokerLastActorId = activePlayer.id
            )
        }
        advancePokerTurn()
    }

    fun pokerAddChips(amount: Int) {
        val targetId = if (_state.value.isPokerMultiplayer) _state.value.localPlayerId else "player_human"
        val updatedPlayers = _state.value.pokerPlayers.map { p ->
            if (p.id == targetId) {
                p.copy(chips = p.chips + amount)
            } else p
        }
        val name = updatedPlayers.find { it.id == targetId }?.name ?: "You"
        val logs = _state.value.pokerLogs + "Added $amount chips to $name's stack."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerTotalChipsAdded = it.pokerTotalChipsAdded + amount
            )
        }
        if (_state.value.isPokerMultiplayer) {
            if (!_state.value.isHost) {
                sendActionToHost("POKER_ADD_CHIPS:$targetId:$amount")
            } else {
                broadcastState()
            }
        }
    }

    fun pokerAddInitialChips(amount: Int) {
        val targetId = if (_state.value.isPokerMultiplayer) _state.value.localPlayerId else "player_human"
        val updatedPlayers = _state.value.pokerPlayers.map { p ->
            if (p.id == targetId) {
                p.copy(chips = p.chips + amount)
            } else p
        }
        val name = updatedPlayers.find { it.id == targetId }?.name ?: "You"
        val logs = _state.value.pokerLogs + "Added $amount chips as initial starting money. $name's stack updated."
        _state.update {
            it.copy(
                pokerPlayers = updatedPlayers,
                pokerLogs = logs,
                pokerInitialChips = it.pokerInitialChips + amount,
                pokerTotalChipsAdded = it.pokerTotalChipsAdded + amount
            )
        }
        if (_state.value.isPokerMultiplayer) {
            if (!_state.value.isHost) {
                sendActionToHost("POKER_ADD_CHIPS:$targetId:$amount")
            } else {
                broadcastState()
            }
        }
    }

    private fun checkPokerFoldWinOrAdvance() {
        val activeCount = _state.value.pokerPlayers.count { !it.hasFolded }
        if (activeCount == 1) {
            val winner = _state.value.pokerPlayers.first { !it.hasFolded }
            val logs = _state.value.pokerLogs + "${winner.name} wins the pot of ${_state.value.pokerPot} chips!"
            val updatedPlayers = _state.value.pokerPlayers.map { p ->
                if (p.id == winner.id) p.copy(chips = p.chips + _state.value.pokerPot) else p
            }
            _state.update {
                it.copy(
                    pokerPlayers = updatedPlayers,
                    pokerLogs = logs,
                    pokerPot = 0,
                    pokerStage = "FINISHED"
                )
            }
            
            val isLocalWinner = winner.id == "player_human" || (_state.value.isPokerMultiplayer && winner.id == _state.value.localPlayerId)
            if (isLocalWinner) {
                SoundPlayer.playWin(_state.value.soundEnabled)
            } else {
                SoundPlayer.playLose(_state.value.soundEnabled)
            }
            
            broadcastState()
        } else {
            advancePokerTurn()
        }
    }

    private fun advancePokerTurn() {
        val stateVal = _state.value
        val players = stateVal.pokerPlayers
        val currentIndex = stateVal.pokerActivePlayerIndex
        
        val nonFoldedNoAllIn = players.filter { !it.hasFolded && !it.isAllIn }
        val allActedAndMatched = nonFoldedNoAllIn.all { it.hasActedThisRound && it.currentBet == stateVal.pokerCurrentBet }
        
        if (allActedAndMatched || nonFoldedNoAllIn.size <= 1) {
            transitionToNextPokerStage()
        } else {
            var nextIdx = (currentIndex + 1) % players.size
            while (players[nextIdx].hasFolded || players[nextIdx].isAllIn) {
                nextIdx = (nextIdx + 1) % players.size
            }
            val updatedPlayers = players.mapIndexed { index, p ->
                if (index == nextIdx) p.copy(lastAction = null) else p
            }
            _state.update {
                it.copy(
                    pokerPlayers = updatedPlayers,
                    pokerActivePlayerIndex = nextIdx
                )
            }
            broadcastState()
            
            val nextPlayer = _state.value.pokerPlayers[nextIdx]
            if (nextPlayer.isAI) {
                triggerAiAction()
            }
        }
    }

    private fun transitionToNextPokerStage() {
        val stateVal = _state.value
        val stage = stateVal.pokerStage
        var deck = stateVal.pokerDeck.toMutableList()
        var community = stateVal.pokerCommunityCards.toMutableList()
        var nextStage = stage
        
        val updatedPlayers = stateVal.pokerPlayers.map { p ->
            p.copy(currentBet = 0, hasActedThisRound = false, lastAction = null)
        }
        
        when (stage) {
            "PRE_FLOP" -> {
                if (deck.size >= 3) {
                    val flop = deck.take(3)
                    deck = deck.drop(3).toMutableList()
                    community.addAll(flop)
                }
                nextStage = "FLOP"
            }
            "FLOP" -> {
                if (deck.isNotEmpty()) {
                    val turn = deck.removeAt(0)
                    community.add(turn)
                }
                nextStage = "TURN"
            }
            "TURN" -> {
                if (deck.isNotEmpty()) {
                    val river = deck.removeAt(0)
                    community.add(river)
                }
                nextStage = "RIVER"
            }
            "RIVER" -> {
                nextStage = "SHOWDOWN"
            }
        }
        
        if (nextStage == "SHOWDOWN") {
            val activePlayers = updatedPlayers.filter { !it.hasFolded }
            val evaluations = activePlayers.associateWith { p ->
                evaluate7CardHand(p.cards + community)
            }
            
            val maxEval = evaluations.values.maxOrNull()
            val winners = evaluations.filter { it.value == maxEval }.keys.toList()
            
            val splitPot = stateVal.pokerPot / maxOf(1, winners.size)
            val finalPlayers = updatedPlayers.map { p ->
                val wonAmount = if (winners.any { w -> w.id == p.id }) splitPot else 0
                if (wonAmount > 0) p.copy(chips = p.chips + wonAmount) else p
            }
            
            val winnerNames = winners.joinToString(", ") { it.name }
            val desc = maxEval?.description ?: "High Card"
            val logs = stateVal.pokerLogs + "Showdown! Winner: $winnerNames with $desc (Pot: ${stateVal.pokerPot} chips)."
            
            _state.update {
                it.copy(
                    pokerPlayers = finalPlayers,
                    pokerCommunityCards = community,
                    pokerDeck = deck,
                    pokerStage = "FINISHED",
                    pokerPot = 0,
                    pokerLogs = logs,
                    pokerCurrentBet = 0,
                    pokerLastActorId = null
                )
            }

            val isLocalWinner = winners.any { w -> 
                w.id == "player_human" || (stateVal.isPokerMultiplayer && w.id == stateVal.localPlayerId) 
            }
            if (isLocalWinner) {
                SoundPlayer.playWin(_state.value.soundEnabled)
            } else {
                SoundPlayer.playLose(_state.value.soundEnabled)
            }
        } else {
            var activeIdx = (stateVal.pokerDealerIndex + 1) % updatedPlayers.size
            while (updatedPlayers[activeIdx].hasFolded || updatedPlayers[activeIdx].isAllIn) {
                activeIdx = (activeIdx + 1) % updatedPlayers.size
            }
            
            _state.update {
                it.copy(
                    pokerPlayers = updatedPlayers,
                    pokerCommunityCards = community,
                    pokerDeck = deck,
                    pokerStage = nextStage,
                    pokerCurrentBet = 0,
                    pokerActivePlayerIndex = activeIdx,
                    pokerLastActorId = null
                )
            }
            
            SoundPlayer.playDealSound()
            
            val activeCount = _state.value.pokerPlayers.count { !it.hasFolded && !it.isAllIn }
            if (activeCount <= 1) {
                transitionToNextPokerStage()
            } else {
                broadcastState()
                val nextPlayer = _state.value.pokerPlayers[activeIdx]
                if (nextPlayer.isAI) {
                    triggerAiAction()
                }
            }
        }
    }

    private fun triggerAiAction() {
        val activePlayer = _state.value.pokerPlayers.getOrNull(_state.value.pokerActivePlayerIndex)
        if (activePlayer == null || !activePlayer.isAI || _state.value.pokerStage == "SHOWDOWN" || _state.value.pokerStage == "FINISHED") return
        
        viewModelScope.launch {
            delay(1500)
            val stateVal = _state.value
            val pot = stateVal.pokerPot
            val currentBet = stateVal.pokerCurrentBet
            val myBet = activePlayer.currentBet
            val callAmount = currentBet - myBet
            val difficulty = stateVal.pokerBotDifficulty
            
            val availableCards = activePlayer.cards + stateVal.pokerCommunityCards
            val eval = if (availableCards.size >= 5) {
                evaluate7CardHand(availableCards)
            } else {
                val hasPair = activePlayer.cards.size == 2 && activePlayer.cards[0].rank == activePlayer.cards[1].rank
                if (hasPair) {
                    HandEvaluation(HandRank.ONE_PAIR, listOf(getPokerValue(activePlayer.cards[0].rank)), "Pair")
                } else {
                    HandEvaluation(HandRank.HIGH_CARD, listOf(maxOf(getPokerValue(activePlayer.cards[0].rank), getPokerValue(activePlayer.cards[1].rank))), "High Card")
                }
            }
            
            val rankVal = eval.rank.value
            
            when {
                callAmount == 0 -> {
                    if (difficulty == "Expert" && rankVal >= 3 && kotlin.random.Random.nextFloat() < 0.35f) {
                        pokerRaise(40)
                    } else if (difficulty == "Hard" && rankVal >= 3 && kotlin.random.Random.nextFloat() < 0.2f) {
                        pokerRaise(30)
                    } else {
                        pokerCheck()
                    }
                }
                callAmount > 0 -> {
                    when (difficulty) {
                        "Beginner" -> {
                            if (rankVal >= 4) {
                                pokerCall()
                            } else if (rankVal == 3 || rankVal == 2) {
                                if (callAmount <= 15) pokerCall() else pokerFold()
                            } else {
                                if (callAmount <= 10) pokerCall() else pokerFold()
                            }
                        }
                        "Easy" -> {
                            if (rankVal >= 3) {
                                pokerCall()
                            } else if (rankVal == 2) {
                                if (callAmount <= 25) pokerCall() else pokerFold()
                            } else {
                                if (callAmount <= 15) pokerCall() else pokerFold()
                            }
                        }
                        "Hard" -> {
                            if (rankVal >= 4) {
                                if (kotlin.random.Random.nextFloat() < 0.4f && activePlayer.chips > callAmount + 50) {
                                    pokerRaise(50)
                                } else {
                                    pokerCall()
                                }
                            } else if (rankVal == 3 || rankVal == 2) {
                                if (kotlin.random.Random.nextFloat() < 0.2f && activePlayer.chips > callAmount + 30) {
                                    pokerRaise(30)
                                } else {
                                    pokerCall()
                                }
                            } else {
                                if (callAmount > 50 && kotlin.random.Random.nextFloat() < 0.6f) {
                                    pokerFold()
                                } else {
                                    pokerCall()
                                }
                            }
                        }
                        "Expert" -> {
                            if (rankVal >= 3) {
                                if (kotlin.random.Random.nextFloat() < 0.5f && activePlayer.chips > callAmount + 60) {
                                    pokerRaise(60)
                                } else {
                                    pokerCall()
                                }
                            } else if (rankVal == 2) {
                                if (kotlin.random.Random.nextFloat() < 0.3f && activePlayer.chips > callAmount + 30) {
                                    pokerRaise(30)
                                } else {
                                    pokerCall()
                                }
                            } else {
                                if (stateVal.pokerCommunityCards.size >= 3 && kotlin.random.Random.nextFloat() < 0.15f && activePlayer.chips > callAmount + 40) {
                                    pokerRaise(40)
                                } else if (callAmount > 80 && kotlin.random.Random.nextFloat() < 0.7f) {
                                    pokerFold()
                                } else {
                                    pokerCall()
                                }
                            }
                        }
                        else -> {
                            if (rankVal >= 3) {
                                if (kotlin.random.Random.nextFloat() < 0.3f && activePlayer.chips > callAmount + 40) {
                                    pokerRaise(40)
                                } else {
                                    pokerCall()
                                }
                            } else if (rankVal == 2) {
                                if (callAmount > activePlayer.chips * 0.5f && kotlin.random.Random.nextFloat() < 0.7f) {
                                    pokerFold()
                                } else {
                                    pokerCall()
                                }
                            } else {
                                if (callAmount > 30) {
                                    pokerFold()
                                } else {
                                    pokerCall()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun evaluate7CardHand(cards: List<Card>): HandEvaluation {
        if (cards.size < 5) {
            return HandEvaluation(HandRank.HIGH_CARD, emptyList(), "High Card")
        }
        val combos = generateCombinations(cards, 5)
        return combos.map { evaluate5CardHand(it) }.maxOrNull() ?: HandEvaluation(HandRank.HIGH_CARD, emptyList(), "High Card")
    }

    private fun generateCombinations(cards: List<Card>, k: Int): List<List<Card>> {
        val result = mutableListOf<List<Card>>()
        fun combine(start: Int, current: List<Card>) {
            if (current.size == k) {
                result.add(current)
                return
            }
            for (i in start until cards.size) {
                combine(i + 1, current + cards[i])
            }
        }
        combine(0, emptyList())
        return result
    }

    fun evaluate5CardHand(cards: List<Card>): HandEvaluation {
        val values = cards.map { getPokerValue(it.rank) }.sortedDescending()
        val isFlush = cards.map { it.suit }.distinct().size == 1
        
        var isStraight = false
        var straightHigh = 0
        val uniqueVals = values.distinct()
        if (uniqueVals.size == 5) {
            if (uniqueVals[0] - uniqueVals[4] == 4) {
                isStraight = true
                straightHigh = uniqueVals[0]
            } else if (uniqueVals == listOf(14, 5, 4, 3, 2)) {
                isStraight = true
                straightHigh = 5
            }
        }
        
        val countsMap = values.groupBy { it }.mapValues { it.value.size }
        val countsSorted = countsMap.toList().sortedWith(compareBy({ -it.second }, { -it.first }))
        
        return when {
            isFlush && isStraight -> {
                if (straightHigh == 14) {
                    HandEvaluation(HandRank.ROYAL_FLUSH, listOf(14), "Royal Flush")
                } else {
                    HandEvaluation(HandRank.STRAIGHT_FLUSH, listOf(straightHigh), "Straight Flush (${cards[0].suit.displayName} High)")
                }
            }
            countsSorted[0].second == 4 -> {
                HandEvaluation(HandRank.FOUR_OF_A_KIND, listOf(countsSorted[0].first, countsSorted[1].first), "Four of a Kind of ${countsSorted[0].first}")
            }
            countsSorted[0].second == 3 && countsSorted[1].second == 2 -> {
                HandEvaluation(HandRank.FULL_HOUSE, listOf(countsSorted[0].first, countsSorted[1].first), "Full House: ${countsSorted[0].first}s full of ${countsSorted[1].first}s")
            }
            isFlush -> {
                HandEvaluation(HandRank.FLUSH, values, "Flush (${cards[0].suit.displayName} High)")
            }
            isStraight -> {
                HandEvaluation(HandRank.STRAIGHT, listOf(straightHigh), "Straight ($straightHigh High)")
            }
            countsSorted[0].second == 3 -> {
                HandEvaluation(HandRank.THREE_OF_A_KIND, listOf(countsSorted[0].first, countsSorted[1].first, countsSorted[2].first), "Three of a Kind of ${countsSorted[0].first}s")
            }
            countsSorted[0].second == 2 && countsSorted[1].second == 2 -> {
                HandEvaluation(HandRank.TWO_PAIR, listOf(countsSorted[0].first, countsSorted[1].first, countsSorted[2].first), "Two Pair: ${countsSorted[0].first}s and ${countsSorted[1].first}s")
            }
            countsSorted[0].second == 2 -> {
                HandEvaluation(HandRank.ONE_PAIR, listOf(countsSorted[0].first) + countsSorted.drop(1).map { it.first }, "Pair of ${countsSorted[0].first}s")
            }
            else -> {
                HandEvaluation(HandRank.HIGH_CARD, values, "High Card: ${values[0]}")
            }
        }
    }

    private fun getPokerValue(rank: CardRank): Int {
        return when (rank) {
            CardRank.ACE -> 14
            CardRank.KING -> 13
            CardRank.QUEEN -> 12
            CardRank.JACK -> 11
            CardRank.TEN -> 10
            CardRank.NINE -> 9
            CardRank.EIGHT -> 8
            CardRank.SEVEN -> 7
            CardRank.SIX -> 6
            CardRank.FIVE -> 5
            CardRank.FOUR -> 4
            CardRank.THREE -> 3
            CardRank.TWO -> 2
        }
    }

    // --- Game Setup ---
    fun updateSetupInputs(numPlayers: String, gameMoney: String) {
        _state.update {
            it.copy(
                numPlayersInput = numPlayers,
                gameMoneyInput = gameMoney
            )
        }
    }

    fun startGame() {
        val count = _state.value.numPlayersInput.toIntOrNull() ?: 3
        val medeb = _state.value.gameMoneyInput.toIntOrNull() ?: 100
        
        val initialPlayers = List(count) { index ->
            Player(
                id = "player_${System.currentTimeMillis()}_$index",
                name = "Player ${index + 1}",
                initialContribution = 200, // Default contribution
                colorIndex = index % 10
            )
        }

        val firstEvent = LogEvent(
            seqNo = null,
            eventType = "GAME_START",
            playerName = "Facilitator",
            detail = "Game started with $count players. ብር (Game Money) is $medeb.",
            amount = medeb,
            balanceBefore = 0,
            balanceAfter = 0,
            timestamp = getCurrentTimeString()
        )

        historyList.clear()
        historyIndex = -1

        val newState = _state.value.copy(
            screen = GameScreen.Play,
            gameMoneyAmount = medeb,
            bankAmount = 0,
            players = initialPlayers,
            logEvents = listOf(firstEvent),
            sequenceCounter = 0
        )
        recordHistory(newState)
    }

    // --- Player Card Editing & Initialization ---
    fun updatePlayerTempName(playerId: String, newName: String) {
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(name = newName) else p
            })
        }
    }

    fun updatePlayerInitialContribution(playerId: String, contribStr: String) {
        val contrib = contribStr.toIntOrNull() ?: 0
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(initialContribution = contrib) else p
            })
        }
    }

    fun initializePlayer(playerId: String) {
        val s = _state.value
        val medeb = s.gameMoneyAmount
        var addedToBank = 0
        val updatedPlayers = s.players.map { p ->
            if (p.id == playerId && !p.isInitialized) {
                val remaining = p.initialContribution - medeb
                addedToBank = medeb
                p.copy(
                    isInitialized = true,
                    currentBalance = remaining
                )
            } else {
                p
            }
        }

        val targetPlayer = s.players.firstOrNull { it.id == playerId }
        val newEvents = s.logEvents.toMutableList()
        if (targetPlayer != null && addedToBank > 0) {
            newEvents.add(
                LogEvent(
                    seqNo = null,
                    eventType = "INITIALIZE",
                    playerName = targetPlayer.name,
                    detail = "Initialized player ${targetPlayer.name} with contribution ${targetPlayer.initialContribution}. Deducted ብር $medeb.",
                    amount = medeb,
                    balanceBefore = targetPlayer.initialContribution,
                    balanceAfter = targetPlayer.initialContribution - medeb,
                    timestamp = getCurrentTimeString()
                )
            )
        }

        val newState = s.copy(
            bankAmount = s.bankAmount + addedToBank,
            players = updatedPlayers,
            logEvents = newEvents
        )
        recordHistory(newState)
        if (addedToBank > 0) {
            SoundPlayer.playContributeSound()
        }
    }

    // --- Win & Lose Controls ---
    fun updatePlayMoneyInput(playerId: String, text: String) {
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) {
                    val playMoney = text.toIntOrNull() ?: 0
                    val isAboveBank = playMoney > s.bankAmount
                    val isAboveBalance = playMoney > p.currentBalance

                    val warning = when {
                        isAboveBank -> "current available bank amount is ${s.bankAmount} ብር. Please play with the bank amount or less amount."
                        isAboveBalance -> "your balance is not sufficient. Add ${playMoney - p.currentBalance} ብር to continue"
                        else -> null
                    }
                    p.copy(
                        playMoneyInput = text,
                        warningMessage = warning,
                        minNeededToContinue = if (isAboveBalance && !isAboveBank) (playMoney - p.currentBalance) else null
                    )
                } else p
            })
        }
    }

    fun playWin(playerId: String) {
        val s = _state.value
        val player = s.players.firstOrNull { it.id == playerId } ?: return
        val amount = player.playMoneyInput.toIntOrNull() ?: 0
        if (amount <= 0) return

        val bankAmount = s.bankAmount
        if (amount > bankAmount) {
            _state.update { state ->
                state.copy(players = state.players.map { p ->
                    if (p.id == playerId) {
                        p.copy(
                            warningMessage = "current available bank amount is ${state.bankAmount} ብር. Please play with the bank amount or less amount.",
                            minNeededToContinue = null
                        )
                    } else p
                })
            }
            return
        }

        if (amount > player.currentBalance) {
            _state.update { state ->
                state.copy(players = state.players.map { p ->
                    if (p.id == playerId) {
                        p.copy(
                            warningMessage = "your balance is not sufficient. Add ${amount - p.currentBalance} ብር to continue",
                            minNeededToContinue = amount - p.currentBalance
                        )
                    } else p
                })
            }
            return
        }

        val newSeq = s.sequenceCounter + 1
        val updatedPlayers = s.players.map { p ->
            if (p.id == playerId) {
                p.copy(
                    currentBalance = p.currentBalance + amount,
                    playMoneyInput = "",
                    warningMessage = null,
                    minNeededToContinue = null,
                    totalWon = p.totalWon + amount
                )
            } else p
        }

        val newEvent = LogEvent(
            seqNo = newSeq,
            eventType = "WIN",
            playerName = player.name,
            detail = "${player.name} won play money $amount from Bank.",
            amount = amount,
            balanceBefore = player.currentBalance,
            balanceAfter = player.currentBalance + amount,
            timestamp = getCurrentTimeString()
        )

        val newState = s.copy(
            bankAmount = (s.bankAmount - amount).coerceAtLeast(0),
            players = updatedPlayers,
            sequenceCounter = newSeq,
            logEvents = s.logEvents + newEvent,
            winEffectPlayerName = player.name,
            loseEffectPlayerName = null,
            effectTriggerId = s.effectTriggerId + 1
        )
        recordHistory(newState)
    }

    fun playLose(playerId: String) {
        val s = _state.value
        val player = s.players.firstOrNull { it.id == playerId } ?: return
        val amount = player.playMoneyInput.toIntOrNull() ?: 0
        if (amount <= 0) return

        val bankAmount = s.bankAmount
        if (amount > bankAmount) {
            _state.update { state ->
                state.copy(players = state.players.map { p ->
                    if (p.id == playerId) {
                        p.copy(
                            warningMessage = "current available bank amount is ${state.bankAmount} ብር. Please play with the bank amount or less amount.",
                            minNeededToContinue = null
                        )
                    } else p
                })
            }
            return
        }

        if (player.currentBalance < amount) {
            _state.update { state ->
                state.copy(players = state.players.map { p ->
                    if (p.id == playerId) {
                        p.copy(
                            warningMessage = "your balance is not sufficient. Add ${amount - p.currentBalance} ብር to continue",
                            minNeededToContinue = amount - p.currentBalance
                        )
                    } else p
                })
            }
            return
        }

        val newSeq = s.sequenceCounter + 1
        val updatedPlayers = s.players.map { p ->
            if (p.id == playerId) {
                p.copy(
                    currentBalance = p.currentBalance - amount,
                    playMoneyInput = "",
                    warningMessage = null,
                    minNeededToContinue = null,
                    totalLost = p.totalLost + amount
                )
            } else p
        }

        val newEvent = LogEvent(
            seqNo = newSeq,
            eventType = "LOSE",
            playerName = player.name,
            detail = "${player.name} lost play money $amount to Bank.",
            amount = amount,
            balanceBefore = player.currentBalance,
            balanceAfter = player.currentBalance - amount,
            timestamp = getCurrentTimeString()
        )

        val newState = s.copy(
            bankAmount = s.bankAmount + amount,
            players = updatedPlayers,
            sequenceCounter = newSeq,
            logEvents = s.logEvents + newEvent,
            winEffectPlayerName = null,
            loseEffectPlayerName = player.name,
            effectTriggerId = s.effectTriggerId + 1
        )
        recordHistory(newState)
        if (amount > 0) {
            SoundPlayer.playContributeSound()
        }
    }

    // --- Savings ---
    fun updateSavingInput(playerId: String, text: String, isWarningField: Boolean = false) {
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) {
                    if (isWarningField) {
                        p.copy(warningInput = text)
                    } else {
                        p.copy(savingInput = text)
                    }
                } else p
            })
        }
    }

    fun addMoneyToSaving(playerId: String, isWarningField: Boolean = false) {
        val player = _state.value.players.firstOrNull { it.id == playerId } ?: return
        val amountStr = if (isWarningField) player.warningInput else player.savingInput
        val amount = amountStr.toIntOrNull() ?: 0
        if (amount <= 0) return

        // Play bank money add sound when a player adds to savings
        SoundPlayer.playContributeSound()

        _state.update { s ->
            val updatedPlayers = s.players.map { p ->
                if (p.id == playerId) {
                    val newBalance = p.currentBalance + amount
                    val playMoney = p.playMoneyInput.toIntOrNull() ?: 0
                    
                    val isAboveBank = playMoney > s.bankAmount
                    val isAboveBalance = playMoney > newBalance

                    val warning = when {
                        isAboveBank -> "current available bank amount is ${s.bankAmount} ብር. Please play with the bank amount or less amount."
                        isAboveBalance -> "your balance is not sufficient. Add ${playMoney - newBalance} ብር to continue"
                        else -> null
                    }
                    p.copy(
                        currentBalance = newBalance,
                        savingInput = if (!isWarningField) "" else p.savingInput,
                        warningInput = if (isWarningField) "" else p.warningInput,
                        warningMessage = warning,
                        minNeededToContinue = if (isAboveBalance && !isAboveBank) (playMoney - newBalance) else null,
                        totalSavingsAdded = p.totalSavingsAdded + amount
                    )
                } else p
            }

            val newEvent = LogEvent(
                seqNo = null,
                eventType = "SAVING_ADD",
                playerName = player.name,
                detail = "Added $amount to saving.",
                amount = amount,
                balanceBefore = player.currentBalance,
                balanceAfter = player.currentBalance + amount,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                players = updatedPlayers,
                logEvents = s.logEvents + newEvent,
                toastMessage = "${player.name} added $amount ብር to savings. Current balance: ${player.currentBalance + amount} ብር"
            )
        }
    }

    // --- Borrow Money ---
    fun updateBorrowInput(playerId: String, amountStr: String) {
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(borrowAmountInput = amountStr) else p
            })
        }
    }

    fun borrowMoney(borrowerId: String, lenderId: String) {
        val borrower = _state.value.players.firstOrNull { it.id == borrowerId } ?: return
        val lender = _state.value.players.firstOrNull { it.id == lenderId } ?: return
        val amount = borrower.borrowAmountInput.toIntOrNull() ?: 0
        if (amount <= 0) return

        // We deduct from Lender and add to Borrower.
        _state.update { s ->
            val updatedPlayers = s.players.map { p ->
                when (p.id) {
                    borrowerId -> {
                        val currentDebt = p.debts[lender.name] ?: 0
                        p.copy(
                            currentBalance = p.currentBalance + amount,
                            borrowAmountInput = "",
                            debts = p.debts + (lender.name to (currentDebt + amount)),
                            totalBorrowed = p.totalBorrowed + amount
                        )
                    }
                    lenderId -> {
                        p.copy(
                            currentBalance = p.currentBalance - amount,
                            totalLended = p.totalLended + amount
                        )
                    }
                    else -> p
                }
            }

            val newEvent = LogEvent(
                seqNo = null,
                eventType = "BORROW",
                playerName = borrower.name,
                detail = "${borrower.name} borrowed $amount from ${lender.name}.",
                amount = amount,
                balanceBefore = borrower.currentBalance,
                balanceAfter = borrower.currentBalance + amount,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                players = updatedPlayers,
                logEvents = s.logEvents + newEvent,
                toastMessage = "${borrower.name} borrowed $amount ብር from ${lender.name}."
            )
        }
    }

    // --- Return Money ---
    fun updateReturnInput(playerId: String, amountStr: String) {
        _state.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(returnAmountInput = amountStr) else p
            })
        }
    }

    fun returnMoney(returningId: String, creditorName: String) {
        val returningPlayer = _state.value.players.firstOrNull { it.id == returningId } ?: return
        val amount = returningPlayer.returnAmountInput.toIntOrNull() ?: 0
        if (amount <= 0) return

        val owedAmount = returningPlayer.debts[creditorName] ?: 0
        if (owedAmount <= 0) return

        val actualReturn = amount.coerceAtMost(owedAmount)

        _state.update { s ->
            // Find the creditor player by name
            val creditor = s.players.firstOrNull { it.name == creditorName }

            val updatedPlayers = s.players.map { p ->
                when {
                    p.id == returningId -> {
                        val remainingDebt = owedAmount - actualReturn
                        val updatedDebts = p.debts.toMutableMap()
                        if (remainingDebt <= 0) {
                            updatedDebts.remove(creditorName)
                        } else {
                            updatedDebts[creditorName] = remainingDebt
                        }
                        p.copy(
                            currentBalance = p.currentBalance - actualReturn,
                            returnAmountInput = "",
                            debts = updatedDebts,
                            totalReturned = p.totalReturned + actualReturn
                        )
                    }
                    p.name == creditorName -> {
                        p.copy(
                            currentBalance = p.currentBalance + actualReturn
                        )
                    }
                    else -> p
                }
            }

            val newEvent = LogEvent(
                seqNo = null,
                eventType = "RETURN",
                playerName = returningPlayer.name,
                detail = "${returningPlayer.name} returned $actualReturn to $creditorName (Remaining debt to them: ${(owedAmount - actualReturn).coerceAtLeast(0)}).",
                amount = actualReturn,
                balanceBefore = returningPlayer.currentBalance,
                balanceAfter = returningPlayer.currentBalance - actualReturn,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                players = updatedPlayers,
                logEvents = s.logEvents + newEvent,
                toastMessage = "${returningPlayer.name} returned $actualReturn ብር to $creditorName."
            )
        }
    }

    // --- Quit Game Options ---
    fun quitAddSavingToBank(playerId: String) {
        val player = _state.value.players.firstOrNull { it.id == playerId } ?: return
        _state.update { s ->
            val playerBalance = player.currentBalance
            val updatedPlayers = s.players.filter { it.id != playerId }
            
            val newEvent = LogEvent(
                seqNo = null,
                eventType = "QUIT",
                playerName = player.name,
                detail = "${player.name} quit the game. Remaining balance $playerBalance was added to the Bank.",
                amount = playerBalance,
                balanceBefore = playerBalance,
                balanceAfter = 0,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                bankAmount = s.bankAmount + playerBalance,
                players = updatedPlayers,
                logEvents = s.logEvents + newEvent
            )
        }
        if (player.currentBalance > 0) {
            SoundPlayer.playContributeSound()
        }
    }

    fun quitLeaveWithMoney(playerId: String) {
        val player = _state.value.players.firstOrNull { it.id == playerId } ?: return
        _state.update { s ->
            val playerBalance = player.currentBalance
            val updatedPlayers = s.players.filter { it.id != playerId }
            
            val newEvent = LogEvent(
                seqNo = null,
                eventType = "QUIT",
                playerName = player.name,
                detail = "${player.name} quit the game. Took remaining balance of $playerBalance and left the game (No bank impact).",
                amount = playerBalance,
                balanceBefore = playerBalance,
                balanceAfter = playerBalance,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                players = updatedPlayers,
                logEvents = s.logEvents + newEvent
            )
        }
    }

    // --- Bottom Add to Bank Control ---
    fun updateBottomAddToBankInput(text: String) {
        _state.update { it.copy(bottomAddToBankInput = text) }
    }

    fun executeBottomAddToBank() {
        val amount = _state.value.bottomAddToBankInput.toIntOrNull() ?: 0
        val activePlayers = _state.value.players.filter { it.isInitialized }
        if (amount <= 0 || activePlayers.isEmpty()) return

        _state.update { s ->
            val totalDeducted = amount * activePlayers.size
            val updatedPlayers = s.players.map { p ->
                if (p.isInitialized) {
                    p.copy(
                        currentBalance = p.currentBalance - amount,
                        totalMassContributionsPaid = p.totalMassContributionsPaid + amount
                    )
                } else p
            }

            val newEvent = LogEvent(
                seqNo = null,
                eventType = "BANK_TOPUP",
                playerName = "Facilitator",
                detail = "Deducted $amount from each active player. Total of $totalDeducted added to Bank.",
                amount = totalDeducted,
                balanceBefore = s.bankAmount,
                balanceAfter = s.bankAmount + totalDeducted,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                bankAmount = s.bankAmount + totalDeducted,
                players = updatedPlayers,
                bottomAddToBankInput = "",
                logEvents = s.logEvents + newEvent
            )
        }
        SoundPlayer.playContributeSound()
    }

    // --- Add New Player ---
    fun updateNewPlayerInputs(name: String, initialSaving: String) {
        _state.update {
            it.copy(
                newPlayerName = name,
                newPlayerInitialSaving = initialSaving
            )
        }
    }

    fun toggleAddingNewPlayer(show: Boolean) {
        _state.update { it.copy(isAddingNewPlayer = show) }
    }

    fun addNewPlayer() {
        val name = _state.value.newPlayerName.trim()
        val initialSaving = _state.value.newPlayerInitialSaving.toIntOrNull() ?: 0
        if (name.isEmpty() || initialSaving <= 0) return

        _state.update { s ->
            val medeb = s.gameMoneyAmount
            val startingBalance = initialSaving - medeb
            
            val newPlayer = Player(
                id = "player_${System.currentTimeMillis()}",
                name = name,
                initialContribution = initialSaving,
                isInitialized = true,
                currentBalance = startingBalance,
                colorIndex = s.players.size % 10
            )

            val newEvent = LogEvent(
                seqNo = null,
                eventType = "NEW_PLAYER",
                playerName = name,
                detail = "Added new player $name with initial saving $initialSaving. Deducted መደብ $medeb immediately. Starting balance: $startingBalance.",
                amount = medeb,
                balanceBefore = initialSaving,
                balanceAfter = startingBalance,
                timestamp = getCurrentTimeString()
            )

            s.copy(
                bankAmount = s.bankAmount + medeb,
                players = s.players + newPlayer,
                newPlayerName = "",
                newPlayerInitialSaving = "",
                isAddingNewPlayer = false,
                logEvents = s.logEvents + newEvent
            )
        }
        SoundPlayer.playContributeSound()
    }

    // --- Settings ---
    fun toggleSettings(show: Boolean) {
        _state.update { it.copy(settingsOpen = show) }
    }

    fun toggleSound() {
        _state.update { 
            val next = !it.soundEnabled
            SoundPlayer.soundEnabled = next
            prefs.edit().putBoolean("sound_enabled", next).apply()
            it.copy(soundEnabled = next)
        }
    }

    fun toggleVibration() {
        _state.update { 
            val next = !it.vibrationEnabled
            SoundPlayer.vibrationEnabled = next
            prefs.edit().putBoolean("vibration_enabled", next).apply()
            it.copy(vibrationEnabled = next)
        }
    }

    fun setPlayerBoxLayout(layout: String) {
        prefs.edit().putString("player_box_layout", layout).apply()
        _state.update { it.copy(playerBoxLayout = layout) }
    }

    fun toggleStartMusic(enabled: Boolean) {
        SoundPlayer.toggleStartMusic(enabled)
        prefs.edit().putBoolean("music_enabled", enabled).apply()
    }

    fun setPokerTableWidthScale(scale: Float) {
        _state.update { it.copy(pokerTableWidthScale = scale) }
    }

    fun setPokerTableHeight(height: Int) {
        _state.update { it.copy(pokerTableHeight = height) }
    }

    fun setPokerBotDifficulty(difficulty: String) {
        _state.update { it.copy(pokerBotDifficulty = difficulty) }
    }

    fun toggleTheme() {
        _state.update { s ->
            val next = !s.isDarkTheme
            prefs.edit().putBoolean("dark_theme", next).apply()
            s.copy(isDarkTheme = next)
        }
    }

    // --- Interactive Reordering of Players ---
    fun movePlayerUp(playerId: String) {
        _state.update { s ->
            val index = s.players.indexOfFirst { it.id == playerId }
            if (index > 0) {
                val list = s.players.toMutableList()
                val temp = list[index]
                list[index] = list[index - 1]
                list[index - 1] = temp
                s.copy(players = list)
            } else s
        }
    }

    fun movePlayerDown(playerId: String) {
        _state.update { s ->
            val index = s.players.indexOfFirst { it.id == playerId }
            if (index >= 0 && index < s.players.size - 1) {
                val list = s.players.toMutableList()
                val temp = list[index]
                list[index] = list[index + 1]
                list[index + 1] = temp
                s.copy(players = list)
            } else s
        }
    }

    // --- Playing Cards Decks Logic ---
    private fun createCombinedDeck(): ShuffledDeck {
        val suitList = CardSuit.values()
        val rankList = CardRank.values()
        val combined = mutableListOf<Card>()
        // Three complete decks of 52 cards each (104 + 52 = 156 cards)
        for (i in 1..3) {
            for (suit in suitList) {
                for (rank in rankList) {
                    combined.add(Card(suit, rank))
                }
            }
        }
        val secureRandom = java.security.SecureRandom()
        val shuffleType = secureRandom.nextInt(5)
        val algorithmName: String
        val shuffledResult: List<Card>
        
        when (shuffleType) {
            0 -> {
                algorithmName = "Cryptographic Fisher-Yates"
                val temp = combined.toMutableList()
                fisherYatesShuffle(temp, secureRandom)
                shuffledResult = temp
            }
            1 -> {
                algorithmName = "Gilbert-Shannon-Reeds Riffle"
                shuffledResult = riffleShuffle(combined, secureRandom)
            }
            2 -> {
                algorithmName = "Chaos Faro Interleave"
                shuffledResult = faroShuffle(combined, secureRandom)
            }
            3 -> {
                algorithmName = "Chunked Overhand & Multi-Cut Mix"
                shuffledResult = overhandShuffle(combined, secureRandom)
            }
            else -> {
                algorithmName = "Chaotic Combined Pipeline"
                shuffledResult = pipelineShuffle(combined, secureRandom)
            }
        }
        
        return ShuffledDeck(shuffledResult, algorithmName)
    }

    private fun fisherYatesShuffle(deck: MutableList<Card>, secureRandom: java.security.SecureRandom) {
        for (i in deck.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = deck[i]
            deck[i] = deck[j]
            deck[j] = temp
        }
    }

    private fun riffleShuffle(deck: List<Card>, secureRandom: java.security.SecureRandom): List<Card> {
        var current = deck.toMutableList()
        val passes = 5 + secureRandom.nextInt(4) // 5 to 8 passes
        for (p in 0 until passes) {
            val n = current.size
            val cut = n / 2 + secureRandom.nextInt(11) - 5 // deviation +/- 5
            val left = current.subList(0, maxOf(0, minOf(n, cut))).toMutableList()
            val right = current.subList(maxOf(0, minOf(n, cut)), n).toMutableList()
            
            val shuffled = mutableListOf<Card>()
            while (left.isNotEmpty() || right.isNotEmpty()) {
                val leftSize = left.size.toDouble()
                val rightSize = right.size.toDouble()
                val total = leftSize + rightSize
                if (secureRandom.nextDouble() < (leftSize / total)) {
                    shuffled.add(left.removeAt(0))
                } else {
                    shuffled.add(right.removeAt(0))
                }
            }
            current = shuffled
        }
        return current
    }

    private fun faroShuffle(deck: List<Card>, secureRandom: java.security.SecureRandom): List<Card> {
        var current = deck.toMutableList()
        val passes = 4 + secureRandom.nextInt(3) // 4 to 6 passes
        for (p in 0 until passes) {
            val n = current.size
            val half = n / 2
            val firstHalf = current.subList(0, half).toMutableList()
            val secondHalf = current.subList(half, n).toMutableList()
            
            val shuffled = mutableListOf<Card>()
            var i = 0
            var j = 0
            val outFaro = secureRandom.nextBoolean()
            while (i < firstHalf.size || j < secondHalf.size) {
                if (outFaro) {
                    if (i < firstHalf.size) {
                        shuffled.add(firstHalf[i++])
                        if (secureRandom.nextDouble() < 0.1 && i < firstHalf.size) {
                            shuffled.add(firstHalf[i++])
                        }
                    }
                    if (j < secondHalf.size) {
                        shuffled.add(secondHalf[j++])
                        if (secureRandom.nextDouble() < 0.1 && j < secondHalf.size) {
                            shuffled.add(secondHalf[j++])
                        }
                    }
                } else {
                    if (j < secondHalf.size) {
                        shuffled.add(secondHalf[j++])
                        if (secureRandom.nextDouble() < 0.1 && j < secondHalf.size) {
                            shuffled.add(secondHalf[j++])
                        }
                    }
                    if (i < firstHalf.size) {
                        shuffled.add(firstHalf[i++])
                        if (secureRandom.nextDouble() < 0.1 && i < firstHalf.size) {
                            shuffled.add(firstHalf[i++])
                        }
                    }
                }
            }
            current = shuffled
        }
        return current
    }

    private fun overhandShuffle(deck: List<Card>, secureRandom: java.security.SecureRandom): List<Card> {
        var current = deck.toMutableList()
        val passes = 8 + secureRandom.nextInt(5) // 8 to 12 passes
        for (p in 0 until passes) {
            val shuffled = mutableListOf<Card>()
            while (current.isNotEmpty()) {
                val chunkSize = minOf(current.size, 4 + secureRandom.nextInt(12))
                val chunk = current.takeLast(chunkSize)
                shuffled.addAll(chunk)
                current = current.dropLast(chunkSize).toMutableList()
            }
            current = shuffled
        }
        return current
    }

    private fun pipelineShuffle(deck: List<Card>, secureRandom: java.security.SecureRandom): List<Card> {
        var result = deck.toMutableList()
        val cutPoint = 20 + secureRandom.nextInt(result.size - 40)
        val part1 = result.subList(0, cutPoint)
        val part2 = result.subList(cutPoint, result.size)
        result = (part2 + part1).toMutableList()
        
        result = riffleShuffle(result, secureRandom).toMutableList()
        result = overhandShuffle(result, secureRandom).toMutableList()
        return result
    }

    private fun formatSequences(sequences: List<DealSequence>): String {
        if (sequences.isEmpty()) return ""
        val sb = java.lang.StringBuilder()
        sb.append(String.format("%-5s | %-6s | %-6s | %-8s | %-5s\n", "Seq", "C1", "C2", "Played", "Stat"))
        sb.append("---------------------------------------------------\n")
        sequences.forEach { seq ->
            val left = seq.leftCard.rank.display
            val right = seq.rightCard.rank.display
            val middle = if (seq.middleCard != null) seq.middleCard.rank.display else "skipped"
            val statusSymbol = when (seq.result) {
                "WIN" -> "W"
                "LOSE" -> "L"
                else -> "S"
            }
            sb.append(String.format("%-5s | %-6s | %-6s | %-8s | %-5s\n", 
                "#${seq.sequenceNumber}", left, right, middle, statusSymbol))
        }
        return sb.toString()
    }

    private fun handleShuffleLog(s: GameState, algoName: String): Pair<String, Int> {
        val previousLogs = formatSequences(s.dealSequences)
        val updatedHistory = if (previousLogs.isNotEmpty()) {
            if (s.cardLogHistory.isEmpty()) previousLogs else "${s.cardLogHistory}\n$previousLogs"
        } else {
            s.cardLogHistory
        }
        val newShuffleCount = s.shuffleCount + 1
        val header = "Shuffle #$newShuffleCount (Algorithm: $algoName) - Time: ${getCurrentTimeString()}"
        val finalHistory = if (updatedHistory.isEmpty()) header else "$updatedHistory\n\n$header"
        return Pair(finalHistory, newShuffleCount)
    }

    fun resetCardDecks() {
        val s = _state.value
        val shuffledDeck = createCombinedDeck()
        val (finalHistory, newShuffleCount) = handleShuffleLog(s, shuffledDeck.algorithmName)
        val combinedDeck = shuffledDeck.cards

        val newState = s.copy(
            leftDeck = combinedDeck,
            rightDeck = combinedDeck,
            leftDealt = emptyList(),
            rightDealt = emptyList(),
            middleCard = null,
            dealSequences = emptyList(),
            cardLogHistory = finalHistory,
            shuffleCount = newShuffleCount
        )
        recordHistory(newState)
    }

    fun dealCards() {
        val s = _state.value
        if (s.leftDeck.isEmpty() || s.rightDeck.isEmpty()) {
            return
        }

        if (s.leftDealt.size == s.rightDealt.size) {
            // Step 1: Deal left card first and glow it
            if (s.leftDeck.isNotEmpty()) {
                var workingDeck = s.leftDeck
                val secureRandom = java.security.SecureRandom()
                val isBigGapTarget = secureRandom.nextInt(100) < 25
                if (isBigGapTarget && workingDeck.size >= 2) {
                    val leftVal = workingDeck[0].rank.value
                    val rightVal = workingDeck[1].rank.value
                    if (java.lang.Math.abs(leftVal - rightVal) < 6) {
                        // Find a card in the rest of the deck that satisfies the big gap
                        var foundIndex = -1
                        for (idx in 2 until workingDeck.size) {
                            if (java.lang.Math.abs(leftVal - workingDeck[idx].rank.value) >= 6) {
                                foundIndex = idx
                                break
                            }
                        }
                        if (foundIndex != -1) {
                            // Swap card at index 1 and foundIndex
                            val list = workingDeck.toMutableList()
                            val temp = list[1]
                            list[1] = list[foundIndex]
                            list[foundIndex] = temp
                            workingDeck = list
                        }
                    }
                }

                val nextLeftCard = workingDeck.first()
                val updatedDeck = workingDeck.drop(1)
                val updatedLeftDealt = s.leftDealt + nextLeftCard
                val check6Msg = if (updatedDeck.size == 6) "⚠️ Warning: Only 6 cards left in the deck!" else null
                val finalState = s.copy(
                    leftDeck = updatedDeck,
                    rightDeck = updatedDeck,
                    leftDealt = updatedLeftDealt,
                    middleCard = null,
                    cardsPlayResult = null,
                    glowLeft = true,
                    glowRight = false,
                    glowMiddle = false,
                    toastMessage = check6Msg ?: s.toastMessage
                )
                _state.value = finalState
                recordHistory(finalState)

                viewModelScope.launch {
                    delay(1000L)
                    clearGlows()
                }
            }
        } else if (s.leftDealt.size > s.rightDealt.size) {
            // Step 2: Deal right card and glow it
            if (s.rightDeck.isNotEmpty()) {
                val nextRightCard = s.rightDeck.first()
                val updatedDeck = s.rightDeck.drop(1)
                val updatedRightDealt = s.rightDealt + nextRightCard
                val check6Msg = if (updatedDeck.size == 6) "⚠️ Warning: Only 6 cards left in the deck!" else null
                
                // Construct a new DealSequence
                val leftDealtCard = s.leftDealt.lastOrNull()
                val newSeq = if (leftDealtCard != null) {
                    DealSequence(
                        sequenceNumber = s.dealSequences.size + 1,
                        leftCard = leftDealtCard,
                        rightCard = nextRightCard,
                        middleCard = null,
                        result = null
                    )
                } else null
                
                val updatedSequences = if (newSeq != null) {
                    s.dealSequences + newSeq
                } else {
                    s.dealSequences
                }

                val finalState = s.copy(
                    leftDeck = updatedDeck,
                    rightDeck = updatedDeck,
                    rightDealt = updatedRightDealt,
                    glowLeft = false,
                    glowRight = true,
                    glowMiddle = false,
                    dealSequences = updatedSequences,
                    toastMessage = check6Msg ?: s.toastMessage
                )
                _state.value = finalState
                recordHistory(finalState)

                viewModelScope.launch {
                    delay(1000L)
                    clearGlows()
                }
            }
        }
    }

    fun clearGlows() {
        _state.update { s ->
            s.copy(glowLeft = false, glowRight = false, glowMiddle = false)
        }
    }

    fun playMiddleCard() {
        val s = _state.value
        if (s.leftDeck.isEmpty()) return

        val drawnCard = s.leftDeck.first()
        val updatedDeck = s.leftDeck.drop(1)
        val nextPlayLeft = !s.nextPlayDeckLeft
        val check6Msg = if (updatedDeck.size == 6) "⚠️ Warning: Only 6 cards left in the deck!" else null

        val leftDealtCard = s.leftDealt.lastOrNull()
        val rightDealtCard = s.rightDealt.lastOrNull()
        var playResult: String? = null
        var newEffectId = s.effectTriggerId

        if (leftDealtCard != null && rightDealtCard != null) {
            val midVal = drawnCard.rank.value
            val leftVal = leftDealtCard.rank.value
            val rightVal = rightDealtCard.rank.value
            
            val minVal = minOf(leftVal, rightVal)
            val maxVal = maxOf(leftVal, rightVal)
            
            if (midVal > minVal && midVal < maxVal) {
                playResult = "WIN"
                SoundPlayer.playWin(s.soundEnabled)
            } else {
                playResult = "LOSE"
                SoundPlayer.playLose(s.soundEnabled)
            }
            newEffectId += 1
        }

        val updatedSequences = s.dealSequences.mapIndexed { idx, seq ->
            if (idx == s.dealSequences.lastIndex) {
                seq.copy(middleCard = drawnCard, result = playResult)
            } else {
                seq
            }
        }

        val newState = s.copy(
            middleCard = drawnCard,
            leftDeck = updatedDeck,
            rightDeck = updatedDeck,
            nextPlayDeckLeft = nextPlayLeft,
            cardsPlayResult = playResult,
            effectTriggerId = newEffectId,
            glowMiddle = true,
            glowLeft = false,
            glowRight = false,
            dealSequences = updatedSequences,
            toastMessage = check6Msg ?: s.toastMessage
        )
        recordHistory(newState)

        // Clear glows after a delay
        viewModelScope.launch {
            delay(1500L)
            clearGlows()
        }
    }

    // --- Log Text Generation ---
    fun generateGameLogString(): String {
        val s = _state.value
        val sb = StringBuilder()

        sb.append("=====================================================\n")
        sb.append("            GAME LOG: ባንክ ከአግርሽ ጋር 5.0\n")
        sb.append("=====================================================\n")
        sb.append("Generated On: ${getCurrentTimeString()}\n")
        sb.append("ብር (Game Money Amount): ${s.gameMoneyAmount}\n")
        sb.append("Final Current Bank Amount: ${s.bankAmount}\n")
        sb.append("Active Players Count: ${s.players.size}\n\n")

        sb.append("-----------------------------------------------------\n")
        sb.append("                  RANKING OF PLAYERS                 \n")
        sb.append("-----------------------------------------------------\n")
        
        // Rankings are based on the difference between the amount won and lost (totalWon - totalLost)
        val playersWithPerformance = s.players.map { p ->
            val perf = p.totalWon - p.totalLost
            Triple(p, perf, p.currentBalance <= 0 && p.totalLost == 0)
        }
        
        // Active/Real players ranked by performance descending (excluding those who finished money just by contributing to the bank)
        val activeRanked = playersWithPerformance
            .filter { !it.third }
            .sortedByDescending { it.second }
            
        val contributorsOnly = playersWithPerformance
            .filter { it.third }
            .sortedByDescending { it.second }
            
        sb.append("REAL PLAYERS RANKING (Based on Won - Lost difference):\n")
        activeRanked.forEachIndexed { idx, item ->
            val p = item.first
            val perf = item.second
            val perfSign = if (perf >= 0) "+" else ""
            val status = if (p.currentBalance <= 0) "REAL LOSER (Out of Money)" else "Active"
            sb.append("${idx + 1}. Name: ${p.name} | Net Performance: $perfSign$perf ብር (Won: ${p.totalWon} | Lost: ${p.totalLost}) | Current Balance: ${p.currentBalance} [${status}]\n")
        }
        
        if (contributorsOnly.isNotEmpty()) {
            sb.append("\nBANK CONTRIBUTORS CATEGORY (Finished money just by contributing to Bank):\n")
            contributorsOnly.forEachIndexed { idx, item ->
                val p = item.first
                sb.append("${idx + 1}. Name: ${p.name} | Current Balance: ${p.currentBalance} | Paid Contributions/Savings to Bank | (Never lost a play)\n")
            }
        }
        sb.append("\n")

        sb.append("-----------------------------------------------------\n")
        sb.append("                 PLAYERS TRANSACTION SUMMARIES        \n")
        sb.append("-----------------------------------------------------\n")
        s.players.forEach { p ->
            val netResult = p.currentBalance - p.initialContribution
            val netStr = if (netResult >= 0) "Won +$netResult" else "Lost $netResult"
            sb.append("Player: ${p.name} | Initial: ${p.initialContribution} | Balance: ${p.currentBalance} | Won: ${p.totalWon} | Lost: ${p.totalLost} | Borrowed: ${p.totalBorrowed} | Returned: ${p.totalReturned} | Net: $netStr\n")
        }
        sb.append("\n")

        sb.append("-----------------------------------------------------\n")
        sb.append("                  PLAY SEQUENCES (WIN/LOSE)          \n")
        sb.append("-----------------------------------------------------\n")
        sb.append(String.format("%-6s | %-12s | %-6s | %-8s | %-12s | %-11s | %-19s\n", 
            "Seq No", "Player Name", "Result", "Amount", "Bal Before", "Bal After", "Timestamp"))
        sb.append("----------------------------------------------------------------------------------------\n")
        s.logEvents.filter { it.seqNo != null }.forEach { e ->
            val cleanDetail = e.detail.replace("\n", " ").replace("\r", " ")
            sb.append(String.format("%-6d | %-12s | %-6s | %-8d | %-12d | %-11d | %-19s\n",
                e.seqNo, e.playerName, e.eventType, e.amount, e.balanceBefore, e.balanceAfter, e.timestamp))
        }
        sb.append("\n")

        sb.append("-----------------------------------------------------\n")
        sb.append("                  ALL HISTORICAL EVENTS              \n")
        sb.append("-----------------------------------------------------\n")
        s.logEvents.forEachIndexed { index, e ->
            val cleanDetail = e.detail.replace("\n", " ").replace("\r", " ")
            sb.append("[${index + 1}] [${e.timestamp}] [${e.eventType}] Player: ${e.playerName} - $cleanDetail\n")
        }
        sb.append("\n=====================================================\n")

        return sb.toString()
    }

    fun generateCardLogString(): String {
        val s = _state.value
        val sb = java.lang.StringBuilder()
        sb.append("=====================================================\n")
        sb.append("            CARD DEAL SEQUENCES REPORT\n")
        sb.append("=====================================================\n")
        sb.append("Generated On: ${getCurrentTimeString()}\n\n")

        if (s.cardLogHistory.isEmpty() && s.dealSequences.isEmpty()) {
            sb.append("No card deals have been completed yet.\n")
        } else {
            if (s.cardLogHistory.isNotEmpty()) {
                sb.append(s.cardLogHistory)
            }
            if (s.dealSequences.isNotEmpty()) {
                val currentFormatted = formatSequences(s.dealSequences)
                if (s.cardLogHistory.isNotEmpty()) {
                    sb.append("\n\n")
                }
                sb.append("Current Shuffled Deck Sequences:\n")
                sb.append(currentFormatted)
            }
        }
        sb.append("=====================================================\n")
        return sb.toString()
    }

    // ==========================================
    // MULTIPLAYER NETWORK FIREBASE ENGINE
    // ==========================================
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var firebasePollJob: kotlinx.coroutines.Job? = null
    private var localPlayerName = ""

    fun generateRandomGameId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun firebaseGet(path: String): String? {
        return try {
            val request = okhttp3.Request.Builder()
                .url("https://playerbankpoker-default-rtdb.firebaseio.com/$path.json")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun firebasePut(path: String, json: String): Boolean {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = okhttp3.RequestBody.create(mediaType, json)
            val request = okhttp3.Request.Builder()
                .url("https://playerbankpoker-default-rtdb.firebaseio.com/$path.json")
                .put(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun firebasePost(path: String, json: String): String? {
        return try {
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = okhttp3.RequestBody.create(mediaType, json)
            val request = okhttp3.Request.Builder()
                .url("https://playerbankpoker-default-rtdb.firebaseio.com/$path.json")
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun firebaseDelete(path: String): Boolean {
        return try {
            val request = okhttp3.Request.Builder()
                .url("https://playerbankpoker-default-rtdb.firebaseio.com/$path.json")
                .delete()
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hostGame(playerName: String) {
        val gId = generateRandomGameId()
        localPlayerName = playerName
        
        _state.update {
            it.copy(
                isMultiplayer = true,
                isHost = true,
                localPlayerId = "player_1",
                gameId = gId,
                lobbyPlayers = listOf(playerName),
                screen = GameScreen.Lobby
            )
        }
        
        startFirebaseHostSync(gId)
    }

    private fun startFirebaseHostSync(gameId: String) {
        firebasePollJob?.cancel()
        firebasePollJob = viewModelScope.launch(Dispatchers.IO) {
            firebaseDelete("sessions/$gameId")
            
            val stateJson = stateAdapter.toJson(_state.value.toDto())
            firebasePut("sessions/$gameId/state", stateJson)
            
            val mapType = com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                String::class.java
            )
            val actionsAdapter = moshi.adapter<Map<String, String>>(mapType)
            
            while (true) {
                delay(1200)
                try {
                    val actionsJson = firebaseGet("sessions/$gameId/actions")
                    if (!actionsJson.isNullOrBlank() && actionsJson != "null") {
                        val actionsMap = actionsAdapter.fromJson(actionsJson)
                        if (actionsMap != null && actionsMap.isNotEmpty()) {
                            val sortedActions = actionsMap.toSortedMap()
                            firebaseDelete("sessions/$gameId/actions")
                            
                            viewModelScope.launch(Dispatchers.Main) {
                                sortedActions.forEach { (_, actionStr) ->
                                    handleIncomingAction(actionStr)
                                }
                            }.join()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun joinGame(gameId: String, playerName: String) {
        localPlayerName = playerName
        val gId = gameId.uppercase().trim()
        
        _state.update {
            it.copy(
                isMultiplayer = true,
                isHost = false,
                gameId = gId,
                screen = GameScreen.Lobby
            )
        }
        
        startFirebaseClientSync(gId)
    }

    fun joinPokerGame(gameId: String, playerName: String, initialChips: Int = 1000) {
        localPlayerName = playerName
        val gId = gameId.uppercase().trim()
        
        _state.update {
            it.copy(
                isPokerMultiplayer = true,
                isMultiplayer = true,
                isHost = false,
                gameId = gId,
                pokerInitialChips = initialChips,
                pokerInitialChipsInput = initialChips.toString(),
                screen = GameScreen.PokerLobby
            )
        }
        
        startFirebaseClientSync(gId)
    }

    private fun startFirebaseClientSync(gameId: String) {
        firebasePollJob?.cancel()
        firebasePollJob = viewModelScope.launch(Dispatchers.IO) {
            val actionJson = moshi.adapter(String::class.java).toJson("JOIN:$localPlayerName")
            firebasePost("sessions/$gameId/actions", actionJson)
            
            while (true) {
                delay(1200)
                try {
                    val stateJson = firebaseGet("sessions/$gameId/state")
                    if (!stateJson.isNullOrBlank() && stateJson != "null") {
                        val receivedDto = stateAdapter.fromJson(stateJson)
                        if (receivedDto != null) {
                            val receivedState = receivedDto.toDomain()
                            
                            var resolvedId = _state.value.localPlayerId
                            if (receivedState.selectedGameMode == "POKER") {
                                val me = receivedState.pokerPlayers.find { it.name == localPlayerName }
                                if (me != null) {
                                    resolvedId = me.id
                                }
                            } else {
                                val index = receivedState.lobbyPlayers.indexOf(localPlayerName)
                                if (index != -1) {
                                    resolvedId = "player_${index + 1}"
                                }
                            }
                            
                            viewModelScope.launch(Dispatchers.Main) {
                                _state.update {
                                    receivedState.copy(
                                        localPlayerId = resolvedId,
                                        isHost = false,
                                        isMultiplayer = true,
                                        isPokerMultiplayer = (receivedState.selectedGameMode == "POKER"),
                                        selectedGameMode = receivedState.selectedGameMode,
                                        soundEnabled = it.soundEnabled,
                                        vibrationEnabled = it.vibrationEnabled,
                                        playerBoxLayout = it.playerBoxLayout
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun broadcastState() {
        if (!_state.value.isHost) return
        val stateJson = stateAdapter.toJson(_state.value.toDto())
        if (_state.value.isLocalWifi) {
            com.example.network.PokerNetworkManager.broadcast("STATE_JSON:$stateJson")
        } else {
            val gameId = _state.value.gameId
            if (gameId.isBlank()) return
            viewModelScope.launch(Dispatchers.IO) {
                firebasePut("sessions/$gameId/state", stateJson)
            }
        }
    }

    fun sendActionToHost(actionStr: String) {
        if (_state.value.isHost) {
            handleIncomingAction(actionStr)
        } else {
            if (_state.value.isLocalWifi) {
                com.example.network.PokerNetworkManager.sendToServer("ACTION_STR:$actionStr")
            } else {
                val gameId = _state.value.gameId
                if (gameId.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val actionJson = moshi.adapter(String::class.java).toJson(actionStr)
                            firebasePost("sessions/$gameId/actions", actionJson)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    fun startLocalWifiHost(playerName: String, port: Int, isPoker: Boolean, chips: Int = 1000, gameMoney: Int = 100) {
        localPlayerName = playerName
        com.example.network.PokerNetworkManager.stopAll()
        com.example.network.WebGameServer.stop()
        com.example.network.PokerNetworkManager.currentPort = port
        
        _state.update {
            it.copy(
                isMultiplayer = true,
                isHost = true,
                isLocalWifi = true,
                localPlayerId = "player_1",
                gameId = "WIFI",
                lobbyPlayers = listOf(playerName),
                selectedGameMode = if (isPoker) "POKER" else "BANK",
                pokerInitialChips = chips,
                pokerInitialChipsInput = chips.toString(),
                gameMoneyAmount = gameMoney,
                screen = if (isPoker) GameScreen.PokerLobby else GameScreen.Lobby,
                isPokerMultiplayer = isPoker
            )
        }
        
        com.example.network.PokerNetworkManager.startHost()
        com.example.network.WebGameServer.start(this, port = 8080)
        observeLocalWifiEvents()
    }

    fun connectLocalWifiClient(playerName: String, hostIp: String, port: Int, isPoker: Boolean, chips: Int = 1000) {
        localPlayerName = playerName
        com.example.network.PokerNetworkManager.stopAll()
        com.example.network.WebGameServer.stop()
        com.example.network.PokerNetworkManager.currentPort = port
        
        _state.update {
            it.copy(
                isMultiplayer = true,
                isHost = false,
                isLocalWifi = true,
                gameId = "WIFI",
                selectedGameMode = if (isPoker) "POKER" else "BANK",
                pokerInitialChips = chips,
                pokerInitialChipsInput = chips.toString(),
                screen = if (isPoker) GameScreen.PokerLobby else GameScreen.Lobby,
                isPokerMultiplayer = isPoker
            )
        }
        
        com.example.network.PokerNetworkManager.connectToHost(hostIp, playerName, chips)
        observeLocalWifiEvents()
    }

    private var localWifiJob: kotlinx.coroutines.Job? = null

    private fun observeLocalWifiEvents() {
        localWifiJob?.cancel()
        localWifiJob = viewModelScope.launch(Dispatchers.IO) {
            com.example.network.PokerNetworkManager.events.collect { event ->
                when (event) {
                    is com.example.network.NetworkEvent.ConnectionStatus -> {
                        viewModelScope.launch(Dispatchers.Main) {
                            _state.update { it.copy(toastMessage = event.message) }
                        }
                    }
                    is com.example.network.NetworkEvent.PlayerJoined -> {
                        if (_state.value.isHost) {
                            viewModelScope.launch(Dispatchers.Main) {
                                val clientName = event.name
                                val currentList = _state.value.lobbyPlayers
                                if (!currentList.contains(clientName)) {
                                    val updatedList = currentList + clientName
                                    _state.update {
                                        it.copy(lobbyPlayers = updatedList)
                                    }
                                    broadcastState()
                                }
                            }
                        }
                    }
                    is com.example.network.NetworkEvent.GameStateReceived -> {
                        val data = event.stateJson
                        if (data.startsWith("ACTION_STR:")) {
                            if (_state.value.isHost) {
                                val actionStr = data.substringAfter("ACTION_STR:")
                                viewModelScope.launch(Dispatchers.Main) {
                                    handleIncomingAction(actionStr)
                                }
                            }
                        } else if (data.startsWith("STATE_JSON:")) {
                            if (!_state.value.isHost) {
                                val stateJson = data.substringAfter("STATE_JSON:")
                                try {
                                    val receivedDto = stateAdapter.fromJson(stateJson)
                                    if (receivedDto != null) {
                                        val receivedState = receivedDto.toDomain()
                                        var resolvedId = _state.value.localPlayerId
                                        if (receivedState.selectedGameMode == "POKER") {
                                            val me = receivedState.pokerPlayers.find { it.name == localPlayerName }
                                            if (me != null) {
                                                resolvedId = me.id
                                            }
                                        } else {
                                            val index = receivedState.lobbyPlayers.indexOf(localPlayerName)
                                            if (index != -1) {
                                                resolvedId = "player_${index + 1}"
                                            }
                                        }
                                        viewModelScope.launch(Dispatchers.Main) {
                                            _state.update {
                                                receivedState.copy(
                                                    localPlayerId = resolvedId,
                                                    isHost = false,
                                                    isMultiplayer = true,
                                                    isPokerMultiplayer = (receivedState.selectedGameMode == "POKER"),
                                                    selectedGameMode = receivedState.selectedGameMode,
                                                    soundEnabled = it.soundEnabled,
                                                    vibrationEnabled = it.vibrationEnabled,
                                                    playerBoxLayout = it.playerBoxLayout,
                                                    isLocalWifi = true
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun handleIncomingAction(actionStr: String) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val parts = actionStr.split(":", limit = 4)
                if (parts.isEmpty() || parts[0].isBlank()) return@launch
                val actionType = parts[0]
                when (actionType) {
                    "JOIN" -> {
                        val clientName = parts[1]
                        val currentList = _state.value.lobbyPlayers
                        if (!currentList.contains(clientName)) {
                            val updatedList = currentList + clientName
                            _state.update {
                                it.copy(lobbyPlayers = updatedList)
                            }
                            broadcastState()
                        }
                    }
                    "POKER_START_LOBBY_GAME" -> {
                        startPokerLobbyGame()
                    }
                    "POKER_START_HAND" -> {
                        startPokerHand()
                    }
                    "POKER_FOLD" -> {
                        pokerFold()
                    }
                    "POKER_CHECK" -> {
                        pokerCheck()
                    }
                    "POKER_CALL" -> {
                        pokerCall()
                    }
                    "POKER_RAISE" -> {
                        val amt = parts[1].toIntOrNull() ?: 10
                        pokerRaise(amt)
                    }
                    "POKER_ADD_CHIPS" -> {
                        val senderId = parts[1]
                        val amt = parts[2].toIntOrNull() ?: 100
                        val updatedPlayers = _state.value.pokerPlayers.map { p ->
                            if (p.id == senderId) p.copy(chips = p.chips + amt) else p
                        }
                        val name = updatedPlayers.find { it.id == senderId }?.name ?: "A player"
                        val logs = _state.value.pokerLogs + "$name added $amt chips to their stack."
                        _state.update {
                            it.copy(
                                pokerPlayers = updatedPlayers,
                                pokerLogs = logs
                            )
                        }
                        broadcastState()
                    }
                    "START_LOBBY_GAME" -> {
                        startMultiplayerLobbyGame()
                    }
                    "SPIN_WHEEL" -> {
                        spinWheelForOrder()
                    }
                    "PLAY" -> {
                        multiplayerChoosePlay()
                    }
                    "PASS" -> {
                        multiplayerChoosePass()
                    }
                    "ENTER_AMOUNT" -> {
                        val amountStr = parts[1]
                        multiplayerSubmitPlayAmount(amountStr)
                    }
                    "DEAL_MIDDLE" -> {
                        multiplayerDealMiddleCard()
                    }
                    "NEXT_PLAYER" -> {
                        advanceToNextMultiplayerTurn()
                    }
                    "BORROW" -> {
                        val borrowerId = parts[1]
                        val lenderId = parts[2]
                        borrowMoney(borrowerId, lenderId)
                        broadcastState()
                    }
                    "RETURN" -> {
                        val returningId = parts[1]
                        val creditorName = parts[2]
                        returnMoney(returningId, creditorName)
                        broadcastState()
                    }
                    "ADD_SAVING" -> {
                        val pId = parts[1]
                        val isWarning = parts[2].toBoolean()
                        addMoneyToSaving(pId, isWarning)
                        broadcastState()
                    }
                    "QUIT_ADD_SAVING" -> {
                        val pId = parts[1]
                        quitAddSavingToBank(pId)
                        broadcastState()
                    }
                    "QUIT_LEAVE_MONEY" -> {
                        val pId = parts[1]
                        quitLeaveWithMoney(pId)
                        broadcastState()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startMultiplayerLobbyGame() {
        if (!_state.value.isHost) return
        val medeb = _state.value.gameMoneyInput.toIntOrNull() ?: 100
        val lobbyList = _state.value.lobbyPlayers
        
        val initialPlayers = lobbyList.mapIndexed { index, name ->
            val pId = "player_${index + 1}"
            Player(
                id = pId,
                name = name,
                initialContribution = 200,
                colorIndex = index % 10
            )
        }

        val firstEvent = LogEvent(
            seqNo = null,
            eventType = "GAME_START",
            playerName = "Facilitator",
            detail = "Multiplayer game started with ${lobbyList.size} players. Game Money (መደብ) is $medeb.",
            amount = medeb,
            balanceBefore = 0,
            balanceAfter = 0,
            timestamp = getCurrentTimeString()
        )

        historyList.clear()
        historyIndex = -1

        _state.update { s ->
            s.copy(
                screen = GameScreen.OrderWheel,
                gameMoneyAmount = medeb,
                bankAmount = 0,
                players = initialPlayers,
                logEvents = listOf(firstEvent),
                sequenceCounter = 0
            )
        }

        _state.value.players.forEach { p ->
            initializePlayer(p.id)
        }

        broadcastState()
    }

    fun spinWheelForOrder() {
        if (!_state.value.isHost) return
        viewModelScope.launch {
            val playersList = _state.value.players
            if (playersList.isEmpty()) return@launch
            
            _state.update { it.copy(isSpinningWheel = true, wheelSelectedPlayerName = null) }
            broadcastState()
            
            var angle = 0f
            for (i in 1..25) {
                angle += (30..120).random()
                val tempIndex = (angle / 60).toInt() % playersList.size
                val tempPlayer = playersList[tempIndex]
                _state.update { 
                    it.copy(
                        wheelAnimationAngle = angle,
                        wheelSelectedPlayerName = tempPlayer.name
                    ) 
                }
                broadcastState()
                delay(100L + i * 5L)
            }
            
            val shuffled = playersList.shuffled()
            val orderedIds = shuffled.map { it.id }
            val firstPlayer = shuffled.first()
            
            _state.update {
                it.copy(
                    isSpinningWheel = false,
                    orderedPlayerIds = orderedIds,
                    activeTurnPlayerId = firstPlayer.id,
                    multiplayerTurnState = "DEAL_TWO_CARDS",
                    wheelSelectedPlayerName = "Turn order is set! First player: ${firstPlayer.name}"
                )
            }
            broadcastState()
            
            delay(3000L)
            
            _state.update {
                it.copy(
                    screen = GameScreen.Play,
                    activeTab = "CARDS"
                )
            }
            
            resetCardDecks()
            dealCards()
            
            _state.update {
                it.copy(
                    multiplayerTurnState = "PLAY_OR_PASS"
                )
            }
            broadcastState()
        }
    }

    fun multiplayerChoosePlay() {
        _state.update { it.copy(multiplayerTurnState = "INPUT_AMOUNT") }
        broadcastState()
    }

    fun multiplayerChoosePass() {
        val s = _state.value
        val currentPlayer = s.players.firstOrNull { it.id == s.activeTurnPlayerId }
        val newEvent = LogEvent(
            seqNo = null,
            eventType = "PASS",
            playerName = currentPlayer?.name ?: "Unknown",
            detail = "${currentPlayer?.name ?: "Player"} passed their turn.",
            amount = 0,
            balanceBefore = currentPlayer?.currentBalance ?: 0,
            balanceAfter = currentPlayer?.currentBalance ?: 0,
            timestamp = getCurrentTimeString()
        )
        _state.update { 
            it.copy(
                logEvents = it.logEvents + newEvent
            )
        }
        advanceToNextMultiplayerTurn()
    }

    fun multiplayerSubmitPlayAmount(amountStr: String) {
        val amount = amountStr.toIntOrNull() ?: 0
        if (amount <= 0) return
        
        val s = _state.value
        val player = s.players.firstOrNull { it.id == s.activeTurnPlayerId } ?: return
        
        if (amount > s.bankAmount) {
            _state.update { it.copy(wheelMessage = "Bet exceeds available bank amount (${s.bankAmount} ብር)!") }
            broadcastState()
            return
        }
        if (amount > player.currentBalance) {
            _state.update { it.copy(wheelMessage = "Bet exceeds your balance (${player.currentBalance} ብር)!") }
            broadcastState()
            return
        }
        
        _state.update { 
            it.copy(
                currentPlayAmountInput = amountStr,
                multiplayerTurnState = "DEAL_MIDDLE",
                wheelMessage = ""
            ) 
        }
        broadcastState()
    }

    fun multiplayerDealMiddleCard() {
        if (!_state.value.isHost) return
        viewModelScope.launch {
            val s = _state.value
            if (s.leftDeck.isEmpty() && s.rightDeck.isEmpty()) return@launch

            var drawnCard: Card? = null
            var updatedLeftDeck = s.leftDeck
            var updatedRightDeck = s.rightDeck
            var nextPlayLeft = s.nextPlayDeckLeft

            if (s.nextPlayDeckLeft) {
                if (s.leftDeck.isNotEmpty()) {
                    drawnCard = s.leftDeck.first()
                    updatedLeftDeck = s.leftDeck.drop(1)
                    nextPlayLeft = false
                } else if (s.rightDeck.isNotEmpty()) {
                    drawnCard = s.rightDeck.first()
                    updatedRightDeck = s.rightDeck.drop(1)
                    nextPlayLeft = true
                }
            } else {
                if (s.rightDeck.isNotEmpty()) {
                    drawnCard = s.rightDeck.first()
                    updatedRightDeck = s.rightDeck.drop(1)
                    nextPlayLeft = true
                } else if (s.leftDeck.isNotEmpty()) {
                    drawnCard = s.leftDeck.first()
                    updatedLeftDeck = s.leftDeck.drop(1)
                    nextPlayLeft = false
                }
            }

            if (drawnCard != null) {
                val leftDealtCard = s.leftDealt.lastOrNull()
                val rightDealtCard = s.rightDealt.lastOrNull()
                var playResult: String? = null
                var newEffectId = s.effectTriggerId

                if (leftDealtCard != null && rightDealtCard != null) {
                    val midVal = drawnCard.rank.value
                    val leftVal = leftDealtCard.rank.value
                    val rightVal = rightDealtCard.rank.value
                    
                    val minVal = minOf(leftVal, rightVal)
                    val maxVal = maxOf(leftVal, rightVal)
                    
                    if (midVal > minVal && midVal < maxVal) {
                        playResult = "WIN"
                        SoundPlayer.playWin(s.soundEnabled)
                    } else {
                        playResult = "LOSE"
                        SoundPlayer.playLose(s.soundEnabled)
                    }
                    newEffectId += 1
                }

                val updatedSequences = s.dealSequences.mapIndexed { idx, seq ->
                    if (idx == s.dealSequences.lastIndex) {
                        seq.copy(middleCard = drawnCard, result = playResult)
                    } else {
                        seq
                    }
                }

                val amount = s.currentPlayAmountInput.toIntOrNull() ?: 0
                val activeId = s.activeTurnPlayerId ?: ""
                val updatedPlayers = s.players.map { p ->
                    if (p.id == activeId) {
                        if (playResult == "WIN") {
                            p.copy(
                                currentBalance = p.currentBalance + amount,
                                totalWon = p.totalWon + amount
                            )
                        } else {
                            p.copy(
                                currentBalance = p.currentBalance - amount,
                                totalLost = p.totalLost + amount
                            )
                        }
                    } else p
                }

                val targetPlayerName = s.players.firstOrNull { it.id == activeId }?.name ?: "Unknown"
                val newEvent = LogEvent(
                    seqNo = s.sequenceCounter + 1,
                    eventType = playResult ?: "LOSE",
                    playerName = targetPlayerName,
                    detail = "$targetPlayerName played $amount and ${if (playResult == "WIN") "WON from Bank" else "LOST to Bank"}.",
                    amount = amount,
                    balanceBefore = s.players.firstOrNull { it.id == activeId }?.currentBalance ?: 0,
                    balanceAfter = if (playResult == "WIN") {
                        (s.players.firstOrNull { it.id == activeId }?.currentBalance ?: 0) + amount
                    } else {
                        (s.players.firstOrNull { it.id == activeId }?.currentBalance ?: 0) - amount
                    },
                    timestamp = getCurrentTimeString()
                )

                _state.update { state ->
                    state.copy(
                        middleCard = drawnCard,
                        leftDeck = updatedLeftDeck,
                        rightDeck = updatedRightDeck,
                        nextPlayDeckLeft = nextPlayLeft,
                        cardsPlayResult = playResult,
                        effectTriggerId = newEffectId,
                        dealSequences = updatedSequences,
                        players = updatedPlayers,
                        bankAmount = if (playResult == "WIN") {
                            (state.bankAmount - amount).coerceAtLeast(0)
                        } else {
                            state.bankAmount + amount
                        },
                        sequenceCounter = state.sequenceCounter + 1,
                        logEvents = state.logEvents + newEvent,
                        multiplayerTurnState = "RESULT",
                        winEffectPlayerName = if (playResult == "WIN") targetPlayerName else null,
                        loseEffectPlayerName = if (playResult == "LOSE") targetPlayerName else null,
                        glowMiddle = true,
                        glowLeft = false,
                        glowRight = false
                    )
                }
                broadcastState()
                viewModelScope.launch {
                    delay(1500L)
                    clearGlows()
                    broadcastState()
                }
            }
        }
    }

    fun advanceToNextMultiplayerTurn() {
        val s = _state.value
        val orderedList = s.orderedPlayerIds
        if (orderedList.isEmpty()) return
        
        val currentIndex = orderedList.indexOf(s.activeTurnPlayerId)
        val nextIndex = (currentIndex + 1) % orderedList.size
        val nextPlayerId = orderedList[nextIndex]
        
        _state.update {
            it.copy(
                activeTurnPlayerId = nextPlayerId,
                multiplayerTurnState = "DEAL_TWO_CARDS",
                currentPlayAmountInput = "",
                middleCard = null,
                cardsPlayResult = null,
                winEffectPlayerName = null,
                loseEffectPlayerName = null
            )
        }
        
        resetCardDecks()
        dealCards()
        
        _state.update {
            it.copy(
                multiplayerTurnState = "PLAY_OR_PASS"
            )
        }
        broadcastState()
    }

    fun addPlayerFromWeb(name: String, initialChips: Int) {
        com.example.network.PokerNetworkManager.simulateLocalJoin(name, initialChips)
    }

    fun addPlayerBorrowFromWeb(playerName: String, amount: Int) {
        val playerObj = _state.value.players.firstOrNull { it.name == playerName } ?: return
        updateBorrowInput(playerObj.id, amount.toString())
        borrowMoney(playerObj.id, "player_1")
        broadcastState()
    }

    fun addPlayerReturnFromWeb(playerName: String, amount: Int) {
        val playerObj = _state.value.players.firstOrNull { it.name == playerName } ?: return
        val hostName = _state.value.players.firstOrNull { it.id == "player_1" }?.name ?: "Facilitator"
        updateReturnInput(playerObj.id, amount.toString())
        returnMoney(playerObj.id, hostName)
        broadcastState()
    }
}
