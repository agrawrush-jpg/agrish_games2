package com.example

import android.util.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import com.example.web.GameType
import com.example.web.PokerState
import com.example.web.BankState
import com.example.web.PokerStage

class GameServer(
    private val port: Int = 8080,
    private val onActionReceived: (String, Map<String, String>) -> Unit
) {
    private var server: HttpServer? = null
    private var activeGameType: GameType = GameType.NONE
    private var currentPokerState: PokerState = PokerState()
    private var currentBankState: BankState = BankState()

    fun start(gameType: GameType) {
        activeGameType = gameType
        if (server != null) return

        try {
            server = HttpServer.create(InetSocketAddress(port), 0).apply {
                createContext("/", RootHandler())
                createContext("/api/state", StateHandler())
                createContext("/api/action", ActionHandler())
                executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                start()
            }
            Log.d("GameServer", "Server started on port $port for game $gameType")
        } catch (e: Exception) {
            Log.e("GameServer", "Error starting server: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            server?.stop(0)
            server = null
            Log.d("GameServer", "Server stopped")
        } catch (e: Exception) {
            Log.e("GameServer", "Error stopping server: ${e.message}", e)
        }
    }

    fun updatePokerState(state: PokerState) {
        currentPokerState = state
    }

    fun updateBankState(state: BankState) {
        currentBankState = state
    }

    private inner class RootHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val response = getWebPageHtml()
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            val os: OutputStream = exchange.responseBody
            os.write(bytes)
            os.close()
        }
    }

    private inner class StateHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val responseJson = JSONObject().apply {
                put("gameType", activeGameType.name)
                if (activeGameType == GameType.POKER) {
                    val pState = currentPokerState
                    put("clientConnected", pState.clientConnected)
                    put("clientName", pState.clientName)
                    put("hostChips", pState.hostChips)
                    put("clientChips", pState.clientChips)
                    put("hostBet", pState.hostBet)
                    put("clientBet", pState.clientBet)
                    put("pot", pState.pot)
                    put("currentBetToMatch", pState.currentBetToMatch)
                    put("stage", pState.stage.name)
                    put("stageDisplay", pState.stage.displayName)
                    put("currentTurn", pState.currentTurn)
                    put("winner", pState.winner ?: JSONObject.NULL)
                    put("suggestions", pState.suggestions)
                    
                    // Community Cards
                    val commArr = JSONArray()
                    pState.communityCards.forEach { card ->
                        commArr.put(JSONObject().apply {
                            put("suit", card.suit.name)
                            put("suitSymbol", card.suit.symbol)
                            put("suitColor", card.suit.color)
                            put("rank", card.rank.name)
                            put("rankDisplay", card.rank.displayName)
                        })
                    }
                    put("communityCards", commArr)

                    // Client Hole Cards
                    val holeArr = JSONArray()
                    pState.clientHoleCards.forEach { card ->
                        holeArr.put(JSONObject().apply {
                            put("suit", card.suit.name)
                            put("suitSymbol", card.suit.symbol)
                            put("suitColor", card.suit.color)
                            put("rank", card.rank.name)
                            put("rankDisplay", card.rank.displayName)
                        })
                    }
                    put("clientHoleCards", holeArr)
                } else if (activeGameType == GameType.BANK) {
                    val bState = currentBankState
                    put("clientConnected", bState.clientConnected)
                    put("clientName", bState.clientName)
                    put("round", bState.round)
                    put("bankValue", bState.bankValue)
                    put("hostScore", bState.hostScore)
                    put("clientScore", bState.clientScore)
                    put("currentTurn", bState.currentTurn)
                    put("isHostBanked", bState.isHostBanked)
                    put("isClientBanked", bState.isClientBanked)
                    put("lastWinnerName", bState.lastWinnerName ?: JSONObject.NULL)
                    put("gameEnded", bState.gameEnded)

                    val rollsArr = JSONArray()
                    bState.rolls.forEach { roll ->
                        rollsArr.put(JSONObject().apply {
                            put("player", roll.player)
                            put("roll", roll.roll)
                            put("resultText", roll.resultText)
                        })
                    }
                    put("rolls", rollsArr)
                }
            }

            val bytes = responseJson.toString().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.apply {
                set("Content-Type", "application/json")
                set("Access-Control-Allow-Origin", "*")
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            val os: OutputStream = exchange.responseBody
            os.write(bytes)
            os.close()
        }
    }

    private inner class ActionHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val query = exchange.requestURI.query ?: ""
            val params = parseQuery(query)
            val action = params["action"] ?: ""

            Log.d("GameServer", "Received action: $action, params: $params")
            onActionReceived(action, params)

            val response = JSONObject().apply { put("status", "success") }
            val bytes = response.toString().toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.apply {
                set("Content-Type", "application/json")
                set("Access-Control-Allow-Origin", "*")
            }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            val os: OutputStream = exchange.responseBody
            os.write(bytes)
            os.close()
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (query.isEmpty()) return params
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            try {
                val key = if (idx > 0) URLDecoder.decode(pair.substring(0, idx), "UTF-8") else pair
                val value = if (idx > 0 && pair.length > idx + 1) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
                params[key] = value
            } catch (e: Exception) {
                // Ignore decoding issues
            }
        }
        return params
    }

    private fun getWebPageHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Agrish_main - Web Player</title>
                <style>
                    :root {
                        --bg-dark: #0F172A;
                        --bg-card: #1E293B;
                        --bg-input: #334155;
                        --primary: #F59E0B;
                        --secondary: #3B82F6;
                        --danger: #EF4444;
                        --success: #10B981;
                        --text-main: #F8FAFC;
                        --text-muted: #94A3B8;
                        --border-color: #475569;
                    }
                    
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    }

                    body {
                        background-color: var(--bg-dark);
                        color: var(--text-main);
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                        padding: 16px;
                    }

                    header {
                        width: 100%;
                        max-width: 500px;
                        text-align: center;
                        margin-bottom: 20px;
                        border-bottom: 1px solid var(--border-color);
                        padding-bottom: 12px;
                    }

                    h1 {
                        color: var(--primary);
                        font-size: 24px;
                        font-weight: 700;
                        letter-spacing: 1px;
                    }

                    .subtitle {
                        color: var(--text-muted);
                        font-size: 12px;
                        margin-top: 4px;
                    }

                    main {
                        width: 100%;
                        max-width: 500px;
                        display: flex;
                        flex-direction: column;
                        gap: 16px;
                    }

                    .card {
                        background-color: var(--bg-card);
                        border-radius: 12px;
                        padding: 16px;
                        border: 1px solid var(--border-color);
                        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
                    }

                    .card-title {
                        font-size: 16px;
                        font-weight: 600;
                        color: var(--primary);
                        margin-bottom: 12px;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        border-bottom: 1px solid rgba(255,255,255,0.1);
                        padding-bottom: 6px;
                    }

                    /* Join screen styles */
                    .join-container {
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                        text-align: center;
                    }

                    input[type="text"], input[type="number"] {
                        width: 100%;
                        background-color: var(--bg-input);
                        border: 1px solid var(--border-color);
                        color: var(--text-main);
                        padding: 12px;
                        border-radius: 8px;
                        font-size: 16px;
                        outline: none;
                        transition: border-color 0.2s;
                    }

                    input[type="text"]:focus, input[type="number"]:focus {
                        border-color: var(--primary);
                    }

                    button {
                        background-color: var(--primary);
                        color: var(--bg-dark);
                        border: none;
                        padding: 12px 24px;
                        font-size: 16px;
                        font-weight: bold;
                        border-radius: 8px;
                        cursor: pointer;
                        transition: transform 0.1s, opacity 0.2s;
                        text-transform: uppercase;
                    }

                    button:active {
                        transform: scale(0.98);
                    }

                    button:disabled {
                        opacity: 0.5;
                        cursor: not-allowed;
                    }

                    button.secondary {
                        background-color: var(--secondary);
                        color: white;
                    }

                    button.danger {
                        background-color: var(--danger);
                        color: white;
                    }

                    /* Poker Table Styles */
                    .poker-pot-row {
                        display: flex;
                        justify-content: space-between;
                        font-weight: bold;
                        font-size: 15px;
                        margin-bottom: 12px;
                        background: rgba(255, 255, 255, 0.05);
                        padding: 8px 12px;
                        border-radius: 6px;
                    }

                    .player-box {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        padding: 10px;
                        border-radius: 8px;
                        background-color: rgba(255, 255, 255, 0.03);
                        margin-bottom: 8px;
                        border: 1px solid transparent;
                    }

                    .player-box.active-turn {
                        border-color: var(--primary);
                        background-color: rgba(245, 158, 11, 0.05);
                    }

                    .player-box.winner {
                        border-color: var(--success);
                        background-color: rgba(16, 185, 129, 0.05);
                    }

                    .badge {
                        font-size: 10px;
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-weight: bold;
                        margin-left: 6px;
                    }
                    .badge.sb { background-color: #3B82F6; color: white; }
                    .badge.bb { background-color: #EF4444; color: white; }
                    .badge.d { background-color: #F59E0B; color: black; }

                    .card-row {
                        display: flex;
                        gap: 8px;
                        justify-content: center;
                        margin: 16px 0;
                    }

                    .poker-card {
                        width: 50px;
                        height: 72px;
                        background: white;
                        color: black;
                        border-radius: 6px;
                        display: flex;
                        flex-direction: column;
                        justify-content: space-between;
                        padding: 6px;
                        font-size: 16px;
                        font-weight: bold;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.15);
                        position: relative;
                        border: 1px solid #CBD5E1;
                    }

                    .poker-card .card-suit {
                        font-size: 20px;
                        align-self: center;
                        margin-top: -4px;
                    }

                    .poker-card .card-bottom {
                        align-self: flex-end;
                        transform: rotate(180deg);
                    }

                    .action-buttons {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                        margin-top: 12px;
                    }

                    .raise-container {
                        grid-column: span 2;
                        display: flex;
                        gap: 8px;
                        margin-top: 6px;
                    }

                    .raise-input-wrapper {
                        flex: 1;
                        display: flex;
                        align-items: center;
                        background-color: var(--bg-input);
                        border-radius: 8px;
                        border: 1px solid var(--border-color);
                        padding-left: 12px;
                    }

                    .raise-input-wrapper span {
                        color: var(--text-muted);
                        font-weight: bold;
                    }

                    .raise-input-wrapper input {
                        border: none !important;
                        background: transparent !important;
                        padding: 10px 6px !important;
                    }

                    /* Bank Game Styles */
                    .bank-score-board {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 12px;
                        margin-bottom: 12px;
                    }

                    .score-card {
                        background: rgba(255,255,255,0.03);
                        padding: 12px;
                        border-radius: 8px;
                        text-align: center;
                    }

                    .score-value {
                        font-size: 20px;
                        font-weight: bold;
                        color: var(--primary);
                        margin-top: 4px;
                    }

                    .bank-display {
                        text-align: center;
                        padding: 20px;
                        background: radial-gradient(circle, var(--bg-input) 0%, rgba(30,41,59,1) 100%);
                        border-radius: 12px;
                        margin-bottom: 16px;
                        border: 1px solid var(--primary);
                    }

                    .bank-value {
                        font-size: 36px;
                        font-weight: 800;
                        color: var(--primary);
                        margin-top: 6px;
                    }

                    .turns-banner {
                        background: rgba(59, 130, 246, 0.1);
                        border: 1px solid var(--secondary);
                        color: var(--text-main);
                        padding: 10px;
                        border-radius: 8px;
                        text-align: center;
                        font-weight: bold;
                        margin-bottom: 12px;
                    }

                    .turns-banner.your-turn {
                        background: rgba(245, 158, 11, 0.1);
                        border: 1px solid var(--primary);
                    }

                    .rolls-history {
                        max-height: 150px;
                        overflow-y: auto;
                        display: flex;
                        flex-direction: column;
                        gap: 6px;
                        padding-right: 4px;
                    }

                    .roll-item {
                        padding: 8px 12px;
                        background: rgba(255, 255, 255, 0.02);
                        border-radius: 6px;
                        font-size: 13px;
                        display: flex;
                        justify-content: space-between;
                        border-left: 3px solid var(--border-color);
                    }

                    .roll-item.host-roll {
                        border-left-color: var(--secondary);
                    }

                    .roll-item.client-roll {
                        border-left-color: var(--primary);
                    }

                    .roll-item.bust {
                        border-left-color: var(--danger);
                        background: rgba(239, 68, 68, 0.05);
                    }

                    .winner-ticker {
                        background-color: #1e293b;
                        color: #ffffff;
                        padding: 6px 12px;
                        border-radius: 6px;
                        text-align: center;
                        font-weight: bold;
                        margin-bottom: 12px;
                        font-size: 13px;
                        border: 1px solid #475569;
                    }

                    .hide {
                        display: none !important;
                    }
                </style>
            </head>
            <body>
                <header>
                    <h1 id="header-title">Agrish_main</h1>
                    <div class="subtitle" id="header-subtitle">Local Web Game Client</div>
                </header>

                <main>
                    <!-- JOIN SCREEN -->
                    <div id="join-screen" class="card">
                        <div class="card-title">Join Game</div>
                        <div class="join-container">
                            <p style="margin-bottom: 8px; font-size: 14px; color: var(--text-muted)">
                                Enter your name to join the game hosted on this local network.
                            </p>
                            <input type="text" id="player-name-input" placeholder="Your Name" value="Guest Player">
                            <button id="join-btn" onclick="joinGame()">Join Game</button>
                        </div>
                    </div>

                    <!-- POKER SCREEN -->
                    <div id="poker-screen" class="hide">
                        <div class="card">
                            <div class="poker-pot-row">
                                <span style="color: var(--primary)" id="poker-pot-text">POT: $0</span>
                                <span style="color: var(--text-muted)" id="poker-stage-text">STAGE: Pre-Flop</span>
                            </div>

                            <!-- Players -->
                            <div id="poker-players">
                                <div id="host-player-box" class="player-box">
                                    <span>Host (P1)<span id="host-role-badge"></span></span>
                                    <span id="host-chips" style="font-weight: bold">$1000</span>
                                </div>
                                <div id="client-player-box" class="player-box">
                                    <span>You (P2)<span id="client-role-badge"></span></span>
                                    <span id="client-chips" style="font-weight: bold">$1000</span>
                                </div>
                            </div>
                        </div>

                        <!-- Community Cards -->
                        <div class="card">
                            <div class="card-title" style="text-align: center">Community Cards</div>
                            <div class="card-row" id="community-cards-row">
                                <div style="color: var(--text-muted); font-size: 14px; margin: 12px 0;">No community cards dealt yet</div>
                            </div>
                        </div>

                        <!-- Web Player's Hole Cards -->
                        <div class="card" style="border-color: var(--secondary)">
                            <div class="card-title" style="text-align: center; color: var(--secondary)">Your Hole Cards</div>
                            <div class="card-row" id="hole-cards-row"></div>
                        </div>

                        <!-- Action Controls -->
                        <div class="card" id="poker-actions-card">
                            <div class="turns-banner" id="poker-turn-indicator">Waiting for turn...</div>
                            <div class="action-buttons">
                                <button class="danger" id="fold-btn" onclick="pokerAction('fold')">Fold</button>
                                <button class="secondary" id="call-btn" onclick="pokerAction('call')">Call/Check</button>
                                <div class="raise-container">
                                    <div class="raise-input-wrapper">
                                        <span>$</span>
                                        <input type="number" id="raise-amount-input" min="10" step="10" value="40">
                                    </div>
                                    <button style="flex: 1;" id="raise-btn" onclick="pokerAction('raise')">Raise</button>
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- BANK SCREEN -->
                    <div id="bank-screen" class="hide">
                        <!-- Realtime Winner (No Emojis, reduced font size) -->
                        <div class="winner-ticker hide" id="bank-winner-ticker">
                            Realtime Leader: Draw
                        </div>

                        <div class="card">
                            <div class="bank-score-board">
                                <div class="score-card">
                                    <div style="font-size: 12px; color: var(--text-muted)">Host Score</div>
                                    <div class="score-value" id="bank-host-score">0</div>
                                </div>
                                <div class="score-card">
                                    <div style="font-size: 12px; color: var(--text-muted)">Your Score</div>
                                    <div class="score-value" id="bank-client-score">0</div>
                                </div>
                            </div>

                            <div class="bank-display">
                                <div style="font-size: 12px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px">Round Bank</div>
                                <div class="bank-value" id="bank-current-value">$0</div>
                            </div>

                            <div class="turns-banner" id="bank-turn-indicator">Waiting for turn...</div>

                            <div class="action-buttons" id="bank-actions" style="grid-template-columns: 1fr 1fr">
                                <button class="secondary" id="bank-roll-btn" onclick="bankAction('roll')">Roll Dice</button>
                                <button style="background-color: var(--success); color: white" id="bank-collect-btn" onclick="bankAction('bank')">BANK</button>
                            </div>
                        </div>

                        <!-- History logs -->
                        <div class="card">
                            <div class="card-title">Roll & Event Log</div>
                            <div class="rolls-history" id="bank-rolls-history">
                                <div style="color: var(--text-muted); font-size: 13px; text-align: center; width: 100%; padding: 12px 0;">No rolls yet. Let's start!</div>
                            </div>
                        </div>
                    </div>
                </main>

                <script>
                    let clientName = "";
                    let isJoined = false;
                    let pollTimer = null;

                    function joinGame() {
                        const nameInput = document.getElementById("player-name-input");
                        const name = nameInput.value.trim() || "Web Player";
                        clientName = name;
                        
                        fetch('/api/action?action=join&name=' + encodeURIComponent(clientName))
                            .then(res => res.json())
                            .then(data => {
                                isJoined = true;
                                document.getElementById("join-screen").classList.add("hide");
                                startPolling();
                            })
                            .catch(err => {
                                alert("Failed to connect to host. Make sure you are on the same Wi-Fi!");
                            });
                    }

                    function startPolling() {
                        pollState();
                        pollTimer = setInterval(pollState, 500);
                    }

                    function pollState() {
                        if (!isJoined) return;

                        fetch('/api/state')
                            .then(res => res.json())
                            .then(state => {
                                if (state.gameType === "POKER") {
                                    showPoker(state);
                                } else if (state.gameType === "BANK") {
                                    showBank(state);
                                }
                            })
                            .catch(err => {
                                console.error("Poll error:", err);
                            });
                    }

                    function showPoker(state) {
                        document.getElementById("poker-screen").classList.remove("hide");
                        document.getElementById("bank-screen").classList.add("hide");
                        document.getElementById("header-title").innerText = "Poker Room";
                        document.getElementById("header-subtitle").innerText = "Playing against Host";

                        // Update Pot & Stage
                        document.getElementById("poker-pot-text").innerText = "POT: $" + state.pot;
                        document.getElementById("poker-stage-text").innerText = "STAGE: " + state.stageDisplay;

                        // Update Chips
                        document.getElementById("host-chips").innerText = "$" + state.hostChips + " (Bet: $" + state.hostBet + ")";
                        document.getElementById("client-chips").innerText = "$" + state.clientChips + " (Bet: $" + state.clientBet + ")";

                        // Roles/Dealer Badges
                        const hostBox = document.getElementById("host-player-box");
                        const clientBox = document.getElementById("client-player-box");
                        
                        let hostBadgeHtml = "";
                        let clientBadgeHtml = "";
                        
                        // Small/Big Blinds & Dealer badges
                        // Since there are only 2 players, SB is also the dealer (D) typically, or they alternate.
                        // Let's read role designations if appropriate, or we can look at turn
                        
                        // Active Turn highlights
                        if (state.currentTurn === "Host") {
                            hostBox.classList.add("active-turn");
                            clientBox.classList.remove("active-turn");
                            document.getElementById("poker-turn-indicator").innerText = "Host's Turn...";
                            document.getElementById("poker-turn-indicator").classList.remove("your-turn");
                            togglePokerButtons(false);
                        } else {
                            hostBox.classList.remove("active-turn");
                            clientBox.classList.add("active-turn");
                            document.getElementById("poker-turn-indicator").innerText = "YOUR TURN!";
                            document.getElementById("poker-turn-indicator").classList.add("your-turn");
                            togglePokerButtons(true);
                        }

                        // Winner highlight
                        if (state.winner) {
                            if (state.winner === "Host") {
                                hostBox.classList.add("winner");
                                clientBox.classList.remove("winner");
                                document.getElementById("poker-turn-indicator").innerText = "Host Won the Hand!";
                            } else if (state.winner === "Client") {
                                clientBox.classList.add("winner");
                                hostBox.classList.remove("winner");
                                document.getElementById("poker-turn-indicator").innerText = "YOU WON THE HAND!";
                            } else {
                                hostBox.classList.add("winner");
                                clientBox.classList.add("winner");
                                document.getElementById("poker-turn-indicator").innerText = "TIE HAND!";
                            }
                            togglePokerButtons(false);
                        } else {
                            hostBox.classList.remove("winner");
                            clientBox.classList.remove("winner");
                        }

                        // Display community cards
                        const commRow = document.getElementById("community-cards-row");
                        if (state.communityCards && state.communityCards.length > 0) {
                            commRow.innerHTML = "";
                            state.communityCards.forEach(card => {
                                commRow.appendChild(createCardElement(card));
                            });
                        } else {
                            commRow.innerHTML = '<div style="color: var(--text-muted); font-size: 14px; margin: 12px 0;">No community cards dealt yet</div>';
                        }

                        // Display client cards
                        const holeRow = document.getElementById("hole-cards-row");
                        if (state.clientHoleCards && state.clientHoleCards.length > 0) {
                            holeRow.innerHTML = "";
                            state.clientHoleCards.forEach(card => {
                                holeRow.appendChild(createCardElement(card));
                            });
                        } else {
                            holeRow.innerHTML = '<div style="color: var(--text-muted); font-size: 14px; margin: 12px 0;">Dealing cards...</div>';
                        }
                    }

                    function createCardElement(card) {
                        const cardDiv = document.createElement("div");
                        cardDiv.className = "poker-card";
                        if (card.suitColor) {
                            cardDiv.style.color = card.suitColor;
                        }
                        
                        cardDiv.innerHTML = `
                            <div>${'$'}{card.rankDisplay}</div>
                            <div class="card-suit">${'$'}{card.suitSymbol}</div>
                            <div class="card-bottom">${'$'}{card.rankDisplay}</div>
                        `;
                        return cardDiv;
                    }

                    function togglePokerButtons(enabled) {
                        document.getElementById("fold-btn").disabled = !enabled;
                        document.getElementById("call-btn").disabled = !enabled;
                        document.getElementById("raise-btn").disabled = !enabled;
                        document.getElementById("raise-amount-input").disabled = !enabled;
                    }

                    function pokerAction(act) {
                        let url = '/api/action?action=' + act;
                        if (act === 'raise') {
                            const amt = document.getElementById("raise-amount-input").value;
                            url += '&amount=' + amt;
                        }
                        fetch(url)
                            .then(res => res.json())
                            .then(data => {
                                pollState();
                            });
                    }

                    function showBank(state) {
                        document.getElementById("poker-screen").classList.add("hide");
                        document.getElementById("bank-screen").classList.remove("hide");
                        document.getElementById("header-title").innerText = "Bank Room";
                        document.getElementById("header-subtitle").innerText = "Round " + state.round + " / 5";

                        // Realtime Leader ticker (Compact, no emojis)
                        const ticker = document.getElementById("bank-winner-ticker");
                        ticker.classList.remove("hide");
                        let leaderText = "Realtime Leader: ";
                        if (state.hostScore > state.clientScore) {
                            leaderText += "Host (" + state.hostScore + " pts)";
                        } else if (state.clientScore > state.hostScore) {
                            leaderText += "You (" + state.clientScore + " pts)";
                        } else {
                            leaderText += "Tie (" + state.hostScore + " pts)";
                        }
                        ticker.innerText = leaderText;

                        // Scoreboard
                        document.getElementById("bank-host-score").innerText = state.hostScore;
                        document.getElementById("bank-client-score").innerText = state.clientScore;

                        // Bank Value
                        document.getElementById("bank-current-value").innerText = "$" + state.bankValue;

                        // Current Turn Banner
                        const indicator = document.getElementById("bank-turn-indicator");
                        const rollBtn = document.getElementById("bank-roll-btn");
                        const collectBtn = document.getElementById("bank-collect-btn");

                        if (state.gameEnded) {
                            indicator.innerText = "Game Ended!";
                            indicator.classList.remove("your-turn");
                            rollBtn.disabled = true;
                            collectBtn.disabled = true;
                            
                            if (state.lastWinnerName) {
                                indicator.innerText = "Game Winner: " + state.lastWinnerName;
                            }
                            return;
                        }

                        if (state.currentTurn === "Client") {
                            indicator.innerText = "YOUR TURN TO ROLL OR BANK!";
                            indicator.classList.add("your-turn");
                            rollBtn.disabled = state.isClientBanked;
                            collectBtn.disabled = state.isClientBanked;
                        } else {
                            indicator.innerText = "Host's Turn...";
                            indicator.classList.remove("your-turn");
                            rollBtn.disabled = true;
                            collectBtn.disabled = true;
                        }

                        if (state.isClientBanked) {
                            indicator.innerText = "You have Banked! Waiting for Host...";
                        }

                        // Rolls log history
                        const historyDiv = document.getElementById("bank-rolls-history");
                        if (state.rolls && state.rolls.length > 0) {
                            historyDiv.innerHTML = "";
                            // Show latest rolls first
                            state.rolls.slice().reverse().forEach(item => {
                                const rollItem = document.createElement("div");
                                let classType = item.player === "Host" ? "host-roll" : "client-roll";
                                if (item.roll === 1) {
                                    classType = "bust";
                                }
                                rollItem.className = "roll-item " + classType;
                                
                                const nameDisp = item.player === "Host" ? "Host" : "You";
                                rollItem.innerHTML = `
                                    <span style="font-weight: bold">${'$'}{nameDisp}</span>
                                    <span>${'$'}{item.resultText}</span>
                                `;
                                historyDiv.appendChild(rollItem);
                            });
                        } else {
                            historyDiv.innerHTML = '<div style="color: var(--text-muted); font-size: 13px; text-align: center; width: 100%; padding: 12px 0;">No rolls yet. Let\'s start!</div>';
                        }
                    }

                    function bankAction(act) {
                        fetch('/api/action?action=' + act)
                            .then(res => res.json())
                            .then(data => {
                                pollState();
                            });
                    }

                    // Scroll detection to fully hide ticker when page is scrolled, and show at top/bottom
                    window.addEventListener('scroll', () => {
                        const ticker = document.getElementById("bank-winner-ticker");
                        if (!ticker) return;
                        const isAtTop = window.scrollY === 0;
                        const isAtBottom = (window.innerHeight + window.scrollY) >= document.documentElement.scrollHeight - 5;
                        if (isAtTop || isAtBottom) {
                            ticker.style.display = 'block';
                        } else {
                            ticker.style.display = 'none';
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
