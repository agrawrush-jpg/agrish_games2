package com.example

import android.content.Context
import androidx.room.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "game_session")
data class GameSessionEntity(
    @PrimaryKey val id: Int = 1,
    val stateJson: String
)

@Dao
interface GameSessionDao {
    @Query("SELECT * FROM game_session WHERE id = 1 LIMIT 1")
    suspend fun getSession(): GameSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: GameSessionEntity)

    @Query("DELETE FROM game_session WHERE id = 1")
    suspend fun deleteSession()
}

@Database(entities = [GameSessionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameSessionDao(): GameSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bank_game_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class SavedPlayerDto(
    val id: String,
    val name: String,
    val initialContribution: Int,
    val isInitialized: Boolean,
    val currentBalance: Int,
    val playMoneyInput: String,
    val savingInput: String,
    val warningInput: String,
    val borrowAmountInput: String,
    val returnAmountInput: String,
    val warningMessage: String?,
    val minNeededToContinue: Int?,
    val debts: Map<String, Int>,
    val colorIndex: Int,
    val totalWon: Int,
    val totalLost: Int,
    val totalBorrowed: Int,
    val totalLended: Int = 0,
    val totalReturned: Int,
    val totalSavingsAdded: Int,
    val totalMassContributionsPaid: Int
)

@JsonClass(generateAdapter = true)
data class SavedLogEventDto(
    val seqNo: Int?,
    val eventType: String,
    val playerName: String,
    val detail: String,
    val amount: Int,
    val balanceBefore: Int,
    val balanceAfter: Int,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class SavedCardDto(
    val suitName: String,
    val rankName: String
)

@JsonClass(generateAdapter = true)
data class SavedPokerPlayerDto(
    val id: String,
    val name: String,
    val chips: Int,
    val currentBet: Int,
    val cards: List<SavedCardDto>,
    val hasFolded: Boolean,
    val isAllIn: Boolean,
    val isDealer: Boolean,
    val isSmallBlind: Boolean,
    val isBigBlind: Boolean,
    val isAI: Boolean,
    val avatarIndex: Int,
    val hasActedThisRound: Boolean,
    val lastAction: String? = null
)

@JsonClass(generateAdapter = true)
data class SavedDealSequenceDto(
    val sequenceNumber: Int,
    val leftCard: SavedCardDto,
    val rightCard: SavedCardDto,
    val middleCard: SavedCardDto? = null,
    val result: String? = null
)

@JsonClass(generateAdapter = true)
data class SavedStateDto(
    val screen: String,
    val selectedGameMode: String = "NONE",
    val pokerPlayers: List<SavedPokerPlayerDto> = emptyList(),
    val pokerCommunityCards: List<SavedCardDto> = emptyList(),
    val pokerDeck: List<SavedCardDto> = emptyList(),
    val pokerPot: Int = 0,
    val pokerCurrentBet: Int = 0,
    val pokerActivePlayerIndex: Int = 0,
    val pokerStage: String = "PRE_FLOP",
    val pokerDealerIndex: Int = 0,
    val pokerLogs: List<String> = emptyList(),
    val pokerMinPlayersInput: String = "3",
    val pokerInitialChipsInput: String = "1000",
    val pokerInitialChips: Int = 1000,
    val pokerTotalChipsAdded: Int = 0,
    val pokerBetAmountInput: String = "",
    val pokerAiCountInput: String = "3",
    val isPokerMultiplayer: Boolean = false,

    val numPlayersInput: String,
    val gameMoneyInput: String,
    val gameMoneyAmount: Int,
    val bankAmount: Int,
    val players: List<SavedPlayerDto>,
    val logEvents: List<SavedLogEventDto>,
    val activeTab: String,
    val sequenceCounter: Int,
    val cardLogHistory: String = "",
    val shuffleCount: Int = 0,
    val leftDeck: List<SavedCardDto>,
    val rightDeck: List<SavedCardDto>,
    val leftDealt: List<SavedCardDto>,
    val rightDealt: List<SavedCardDto>,
    val middleCard: SavedCardDto?,
    val nextPlayDeckLeft: Boolean,
    val bottomAddToBankInput: String,
    val newPlayerName: String,
    val newPlayerInitialSaving: String,
    val isAddingNewPlayer: Boolean,
    val glowLeft: Boolean = false,
    val glowRight: Boolean = false,
    val glowMiddle: Boolean = false,
    val dealSequences: List<SavedDealSequenceDto> = emptyList(),
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val playerBoxLayout: String = "horizontal",
    val isMultiplayer: Boolean = false,
    val isHost: Boolean = false,
    val localPlayerId: String? = null,
    val gameId: String = "",
    val multiplayerTurnState: String = "WAITING",
    val activeTurnPlayerId: String? = null,
    val currentPlayAmountInput: String = "",
    val isSpinningWheel: Boolean = false,
    val wheelAnimationAngle: Float = 0f,
    val wheelSelectedPlayerName: String? = null,
    val wheelSelectedPlayerId: String? = null,
    val orderedPlayerIds: List<String> = emptyList(),
    val lobbyPlayers: List<String> = emptyList(),
    val isDarkTheme: Boolean = true,
    val pokerLastActorId: String? = null,
    val pokerBotDifficulty: String = "Medium",
    val pokerTableWidthScale: Float = 1.0f,
    val pokerTableHeight: Int = 130,
    val isLocalWifi: Boolean = false
)

fun GameState.toDto(): SavedStateDto {
    return SavedStateDto(
        screen = when (this.screen) {
            is GameScreen.Welcome -> "Welcome"
            is GameScreen.Setup -> "Setup"
            is GameScreen.Lobby -> "Lobby"
            is GameScreen.OrderWheel -> "OrderWheel"
            is GameScreen.Play -> "Play"
            is GameScreen.PokerWelcome -> "PokerWelcome"
            is GameScreen.PokerSetup -> "PokerSetup"
            is GameScreen.PokerLobby -> "PokerLobby"
            is GameScreen.PokerPlay -> "PokerPlay"
        },
        selectedGameMode = this.selectedGameMode,
        pokerPlayers = this.pokerPlayers.map { p ->
            SavedPokerPlayerDto(
                id = p.id,
                name = p.name,
                chips = p.chips,
                currentBet = p.currentBet,
                cards = p.cards.map { SavedCardDto(it.suit.name, it.rank.name) },
                hasFolded = p.hasFolded,
                isAllIn = p.isAllIn,
                isDealer = p.isDealer,
                isSmallBlind = p.isSmallBlind,
                isBigBlind = p.isBigBlind,
                isAI = p.isAI,
                avatarIndex = p.avatarIndex,
                hasActedThisRound = p.hasActedThisRound,
                lastAction = p.lastAction
            )
        },
        pokerCommunityCards = this.pokerCommunityCards.map { SavedCardDto(it.suit.name, it.rank.name) },
        pokerDeck = this.pokerDeck.map { SavedCardDto(it.suit.name, it.rank.name) },
        pokerPot = this.pokerPot,
        pokerCurrentBet = this.pokerCurrentBet,
        pokerActivePlayerIndex = this.pokerActivePlayerIndex,
        pokerStage = this.pokerStage,
        pokerDealerIndex = this.pokerDealerIndex,
        pokerLogs = this.pokerLogs,
        pokerMinPlayersInput = this.pokerMinPlayersInput,
        pokerInitialChipsInput = this.pokerInitialChipsInput,
        pokerInitialChips = this.pokerInitialChips,
        pokerTotalChipsAdded = this.pokerTotalChipsAdded,
        pokerBetAmountInput = this.pokerBetAmountInput,
        pokerAiCountInput = this.pokerAiCountInput,
        isPokerMultiplayer = this.isPokerMultiplayer,

        numPlayersInput = this.numPlayersInput,
        gameMoneyInput = this.gameMoneyInput,
        gameMoneyAmount = this.gameMoneyAmount,
        bankAmount = this.bankAmount,
        players = this.players.map { p ->
            SavedPlayerDto(
                id = p.id,
                name = p.name,
                initialContribution = p.initialContribution,
                isInitialized = p.isInitialized,
                currentBalance = p.currentBalance,
                playMoneyInput = p.playMoneyInput,
                savingInput = p.savingInput,
                warningInput = p.warningInput,
                borrowAmountInput = p.borrowAmountInput,
                returnAmountInput = p.returnAmountInput,
                warningMessage = p.warningMessage,
                minNeededToContinue = p.minNeededToContinue,
                debts = p.debts,
                colorIndex = p.colorIndex,
                totalWon = p.totalWon,
                totalLost = p.totalLost,
                totalBorrowed = p.totalBorrowed,
                totalLended = p.totalLended,
                totalReturned = p.totalReturned,
                totalSavingsAdded = p.totalSavingsAdded,
                totalMassContributionsPaid = p.totalMassContributionsPaid
            )
        },
        logEvents = this.logEvents.map { e ->
            SavedLogEventDto(
                seqNo = e.seqNo,
                eventType = e.eventType,
                playerName = e.playerName,
                detail = e.detail,
                amount = e.amount,
                balanceBefore = e.balanceBefore,
                balanceAfter = e.balanceAfter,
                timestamp = e.timestamp
            )
        },
        activeTab = this.activeTab,
        sequenceCounter = this.sequenceCounter,
        cardLogHistory = this.cardLogHistory,
        shuffleCount = this.shuffleCount,
        leftDeck = this.leftDeck.map { SavedCardDto(it.suit.name, it.rank.name) },
        rightDeck = this.rightDeck.map { SavedCardDto(it.suit.name, it.rank.name) },
        leftDealt = this.leftDealt.map { SavedCardDto(it.suit.name, it.rank.name) },
        rightDealt = this.rightDealt.map { SavedCardDto(it.suit.name, it.rank.name) },
        middleCard = this.middleCard?.let { SavedCardDto(it.suit.name, it.rank.name) },
        nextPlayDeckLeft = this.nextPlayDeckLeft,
        bottomAddToBankInput = this.bottomAddToBankInput,
        newPlayerName = this.newPlayerName,
        newPlayerInitialSaving = this.newPlayerInitialSaving,
        isAddingNewPlayer = this.isAddingNewPlayer,
        glowLeft = this.glowLeft,
        glowRight = this.glowRight,
        glowMiddle = this.glowMiddle,
        dealSequences = this.dealSequences.map { ds ->
            SavedDealSequenceDto(
                sequenceNumber = ds.sequenceNumber,
                leftCard = SavedCardDto(ds.leftCard.suit.name, ds.leftCard.rank.name),
                rightCard = SavedCardDto(ds.rightCard.suit.name, ds.rightCard.rank.name),
                middleCard = ds.middleCard?.let { SavedCardDto(it.suit.name, it.rank.name) },
                result = ds.result
            )
        },
        soundEnabled = this.soundEnabled,
        vibrationEnabled = this.vibrationEnabled,
        playerBoxLayout = this.playerBoxLayout,
        isMultiplayer = this.isMultiplayer,
        isHost = this.isHost,
        localPlayerId = this.localPlayerId,
        gameId = this.gameId,
        multiplayerTurnState = this.multiplayerTurnState,
        activeTurnPlayerId = this.activeTurnPlayerId,
        currentPlayAmountInput = this.currentPlayAmountInput,
        isSpinningWheel = this.isSpinningWheel,
        wheelAnimationAngle = this.wheelAnimationAngle,
        wheelSelectedPlayerName = this.wheelSelectedPlayerName,
        wheelSelectedPlayerId = this.wheelSelectedPlayerId,
        orderedPlayerIds = this.orderedPlayerIds,
        lobbyPlayers = this.lobbyPlayers,
        isDarkTheme = this.isDarkTheme,
        pokerLastActorId = this.pokerLastActorId,
        pokerBotDifficulty = this.pokerBotDifficulty,
        pokerTableWidthScale = this.pokerTableWidthScale,
        pokerTableHeight = this.pokerTableHeight,
        isLocalWifi = this.isLocalWifi
    )
}

fun SavedStateDto.toDomain(): GameState {
    return GameState(
        screen = when (this.screen) {
            "Welcome" -> GameScreen.Welcome
            "Setup" -> GameScreen.Setup
            "Lobby" -> GameScreen.Lobby
            "OrderWheel" -> GameScreen.OrderWheel
            "Play" -> GameScreen.Play
            "PokerWelcome" -> GameScreen.PokerWelcome
            "PokerSetup" -> GameScreen.PokerSetup
            "PokerLobby" -> GameScreen.PokerLobby
            "PokerPlay" -> GameScreen.PokerPlay
            else -> GameScreen.Welcome
        },
        selectedGameMode = this.selectedGameMode,
        pokerPlayers = this.pokerPlayers.map { p ->
            PokerPlayer(
                id = p.id,
                name = p.name,
                chips = p.chips,
                currentBet = p.currentBet,
                cards = p.cards.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
                hasFolded = p.hasFolded,
                isAllIn = p.isAllIn,
                isDealer = p.isDealer,
                isSmallBlind = p.isSmallBlind,
                isBigBlind = p.isBigBlind,
                isAI = p.isAI,
                avatarIndex = p.avatarIndex,
                hasActedThisRound = p.hasActedThisRound,
                lastAction = p.lastAction
            )
        },
        pokerCommunityCards = this.pokerCommunityCards.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        pokerDeck = this.pokerDeck.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        pokerPot = this.pokerPot,
        pokerCurrentBet = this.pokerCurrentBet,
        pokerActivePlayerIndex = this.pokerActivePlayerIndex,
        pokerStage = this.pokerStage,
        pokerDealerIndex = this.pokerDealerIndex,
        pokerLogs = this.pokerLogs,
        pokerMinPlayersInput = this.pokerMinPlayersInput,
        pokerInitialChipsInput = this.pokerInitialChipsInput,
        pokerInitialChips = this.pokerInitialChips,
        pokerTotalChipsAdded = this.pokerTotalChipsAdded,
        pokerBetAmountInput = this.pokerBetAmountInput,
        pokerAiCountInput = this.pokerAiCountInput,
        isPokerMultiplayer = this.isPokerMultiplayer,

        numPlayersInput = this.numPlayersInput,
        gameMoneyInput = this.gameMoneyInput,
        gameMoneyAmount = this.gameMoneyAmount,
        bankAmount = this.bankAmount,
        players = this.players.map { p ->
            Player(
                id = p.id,
                name = p.name,
                initialContribution = p.initialContribution,
                isInitialized = p.isInitialized,
                currentBalance = p.currentBalance,
                playMoneyInput = p.playMoneyInput,
                savingInput = p.savingInput,
                warningInput = p.warningInput,
                borrowAmountInput = p.borrowAmountInput,
                returnAmountInput = p.returnAmountInput,
                warningMessage = p.warningMessage,
                minNeededToContinue = p.minNeededToContinue,
                debts = p.debts,
                colorIndex = p.colorIndex,
                totalWon = p.totalWon,
                totalLost = p.totalLost,
                totalBorrowed = p.totalBorrowed,
                totalLended = p.totalLended,
                totalReturned = p.totalReturned,
                totalSavingsAdded = p.totalSavingsAdded,
                totalMassContributionsPaid = p.totalMassContributionsPaid
            )
        },
        logEvents = this.logEvents.map { e ->
            LogEvent(
                seqNo = e.seqNo,
                eventType = e.eventType,
                playerName = e.playerName,
                detail = e.detail,
                amount = e.amount,
                balanceBefore = e.balanceBefore,
                balanceAfter = e.balanceAfter,
                timestamp = e.timestamp
            )
        },
        activeTab = this.activeTab,
        sequenceCounter = this.sequenceCounter,
        cardLogHistory = this.cardLogHistory,
        shuffleCount = this.shuffleCount,
        leftDeck = this.leftDeck.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        rightDeck = this.rightDeck.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        leftDealt = this.leftDealt.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        rightDealt = this.rightDealt.map { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        middleCard = this.middleCard?.let { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
        nextPlayDeckLeft = this.nextPlayDeckLeft,
        bottomAddToBankInput = this.bottomAddToBankInput,
        newPlayerName = this.newPlayerName,
        newPlayerInitialSaving = this.newPlayerInitialSaving,
        isAddingNewPlayer = this.isAddingNewPlayer,
        glowLeft = this.glowLeft,
        glowRight = this.glowRight,
        glowMiddle = this.glowMiddle,
        dealSequences = this.dealSequences.map { ds ->
            DealSequence(
                sequenceNumber = ds.sequenceNumber,
                leftCard = Card(CardSuit.valueOf(ds.leftCard.suitName), CardRank.valueOf(ds.leftCard.rankName)),
                rightCard = Card(CardSuit.valueOf(ds.rightCard.suitName), CardRank.valueOf(ds.rightCard.rankName)),
                middleCard = ds.middleCard?.let { Card(CardSuit.valueOf(it.suitName), CardRank.valueOf(it.rankName)) },
                result = ds.result
            )
        },
        soundEnabled = this.soundEnabled,
        vibrationEnabled = this.vibrationEnabled,
        playerBoxLayout = this.playerBoxLayout,
        isMultiplayer = this.isMultiplayer,
        isHost = this.isHost,
        localPlayerId = this.localPlayerId,
        gameId = this.gameId,
        multiplayerTurnState = this.multiplayerTurnState,
        activeTurnPlayerId = this.activeTurnPlayerId,
        currentPlayAmountInput = this.currentPlayAmountInput,
        isSpinningWheel = this.isSpinningWheel,
        wheelAnimationAngle = this.wheelAnimationAngle,
        wheelSelectedPlayerName = this.wheelSelectedPlayerName,
        wheelSelectedPlayerId = this.wheelSelectedPlayerId,
        orderedPlayerIds = this.orderedPlayerIds,
        lobbyPlayers = this.lobbyPlayers,
        isDarkTheme = this.isDarkTheme,
        pokerLastActorId = this.pokerLastActorId,
        pokerBotDifficulty = this.pokerBotDifficulty,
        pokerTableWidthScale = this.pokerTableWidthScale,
        pokerTableHeight = this.pokerTableHeight,
        isLocalWifi = this.isLocalWifi
    )
}
