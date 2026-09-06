package com.example.network

import android.util.Log
import com.example.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import org.json.JSONObject
import org.json.JSONArray

object WebGameServer {
    private const val TAG = "WebGameServer"
    private var serverSocket: ServerSocket? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var gameViewModel: GameViewModel? = null
    var webPort: Int = 8080

    fun start(viewModel: GameViewModel, port: Int = 8080) {
        gameViewModel = viewModel
        webPort = port
        stop()
        job = scope.launch {
            try {
                serverSocket = ServerSocket(webPort)
                Log.d(TAG, "Web server started on port $webPort")
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleConnection(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        job?.cancel()
        serverSocket = null
        job = null
    }

    private fun handleConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()
            val requestLine = reader.readLine() ?: return
            
            // Read headers to consume them
            var line: String?
            var contentLength = 0
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            if (path == "/" || path == "/index.html") {
                serveHtml(out)
            } else if (path.startsWith("/api/state")) {
                serveStateJson(out, path)
            } else if (path.startsWith("/api/action")) {
                // Parse params
                val query = path.substringAfter("?", "")
                handleApiAction(query, out)
            } else {
                sendResponse(out, "404 Not Found", "text/plain", "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun serveHtml(out: OutputStream) {
        val isPoker = gameViewModel?.state?.value?.selectedGameMode == "POKER"
        val html = getHtmlContent(isPoker)
        sendResponse(out, "200 OK", "text/html; charset=utf-8", html)
    }

    private fun serveStateJson(out: OutputStream, path: String = "") {
        val vm = gameViewModel
        if (vm == null) {
            sendResponse(out, "200 OK", "application/json", "{}")
            return
        }
        val state = vm.state.value
        val json = JSONObject().apply {
            put("selectedGameMode", state.selectedGameMode)
            put("pokerStage", state.pokerStage)
            put("pokerPot", state.pokerPot)
            put("bankAmount", state.bankAmount)

            // Parse player name query
            val query = path.substringAfter("?", "")
            val params = query.split("&").associate {
                val pair = it.split("=")
                (pair.getOrNull(0) ?: "") to java.net.URLDecoder.decode(pair.getOrNull(1) ?: "", "UTF-8")
            }
            val playerName = params["name"] ?: ""

            if (state.selectedGameMode == "POKER") {
                val p = state.pokerPlayers.find { it.name == playerName }
                if (p != null && p.cards.size >= 2) {
                    val bestHand = vm.evaluate7CardHand(p.cards + state.pokerCommunityCards)
                    val help = com.example.getPokerDetailedHelp(p.cards, state.pokerCommunityCards, bestHand)
                    put("suggestions", help)
                } else {
                    put("suggestions", "Detailed poker assistant will analyze your hands here once dealt.")
                }
            } else {
                // Bank mode stats
                put("hostScore", 0)
                put("clientScore", 0)
                put("bankValue", state.bankAmount)
                put("round", 1)
            }
            
            // Players list
            val playersArr = JSONArray()
            if (state.selectedGameMode == "POKER") {
                state.pokerPlayers.forEach { p ->
                    val pJson = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("chips", p.chips)
                        put("currentBet", p.currentBet)
                        put("hasFolded", p.hasFolded)
                        put("isAllIn", p.isAllIn)
                        put("isDealer", p.isDealer)
                        put("isSmallBlind", p.isSmallBlind)
                        put("isBigBlind", p.isBigBlind)
                        
                        // Hand (Only show if cards are exposed or it's the specific player)
                        val cardsArr = JSONArray()
                        p.cards.forEach { c ->
                            cardsArr.put(JSONObject().apply {
                                put("suit", c.suit.name)
                                put("rank", c.rank.name)
                                put("display", c.rank.display)
                                put("colorRed", c.suit.colorRed)
                            })
                        }
                        put("cards", cardsArr)
                    }
                    playersArr.put(pJson)
                }
                
                val communityArr = JSONArray()
                state.pokerCommunityCards.forEach { c ->
                    communityArr.put(JSONObject().apply {
                        put("suit", c.suit.name)
                        put("rank", c.rank.name)
                        put("display", c.rank.display)
                        put("colorRed", c.suit.colorRed)
                    })
                }
                put("pokerCommunityCards", communityArr)
                put("pokerActivePlayerIndex", state.pokerActivePlayerIndex)
            } else {
                // Bank Mode
                state.players.filter { it.isInitialized }.forEach { p ->
                    val pJson = JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("balance", p.currentBalance)
                    }
                    playersArr.put(pJson)
                }
                put("bankAmount", state.bankAmount)
            }
            put("players", playersArr)
        }
        sendResponse(out, "200 OK", "application/json", json.toString())
    }

    private fun handleApiAction(query: String, out: OutputStream) {
        val params = query.split("&").associate {
            val pair = it.split("=")
            val key = pair.getOrNull(0) ?: ""
            val value = java.net.URLDecoder.decode(pair.getOrNull(1) ?: "", "UTF-8")
            key to value
        }

        val action = params["action"] ?: ""
        val name = params["name"] ?: ""
        val amount = params["amount"]?.toIntOrNull() ?: 0

        Log.d(TAG, "API Action: action=$action, name=$name, amount=$amount")
        val vm = gameViewModel
        if (vm != null) {
            // Run on Main thread
            kotlinx.coroutines.MainScope().launch {
                try {
                    when (action) {
                        "join" -> {
                            vm.addPlayerFromWeb(name, amount)
                        }
                        "fold" -> {
                            vm.pokerFold()
                        }
                        "check" -> {
                            vm.pokerCheck()
                        }
                        "call" -> {
                            vm.pokerCall()
                        }
                        "raise" -> {
                            vm.pokerRaise(amount)
                        }
                        "bank_borrow" -> {
                            vm.addPlayerBorrowFromWeb(name, amount)
                        }
                        "bank_return" -> {
                            vm.addPlayerReturnFromWeb(name, amount)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error performing web action", e)
                }
            }
        }
        serveStateJson(out)
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun getHtmlContent(isPoker: Boolean): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>Neon Game Portal</title>
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: #0c0f12;
                    color: #ffffff;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                .container {
                    max-width: 600px;
                    margin: 0 auto;
                    padding: 16px;
                }
                h1 {
                    text-align: center;
                    color: #ffd700;
                    font-size: 24px;
                    text-shadow: 0 0 10px rgba(255, 215, 0, 0.3);
                }
                .card {
                    background-color: #1a1f26;
                    border: 1px solid rgba(255, 255, 255, 0.1);
                    border-radius: 12px;
                    padding: 16px;
                    margin-bottom: 16px;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
                }
                .btn {
                    display: block;
                    width: 100%;
                    padding: 12px;
                    border: none;
                    border-radius: 8px;
                    font-weight: bold;
                    font-size: 16px;
                    cursor: pointer;
                    margin-top: 8px;
                    text-align: center;
                }
                .btn-primary {
                    background-color: #27ae60;
                    color: white;
                }
                .btn-secondary {
                    background-color: #ffd700;
                    color: black;
                }
                .btn-danger {
                    background-color: #c0392b;
                    color: white;
                }
                input {
                    width: 100%;
                    padding: 10px;
                    border-radius: 6px;
                    border: 1px solid #ffd700;
                    background-color: #0c0f12;
                    color: white;
                    box-sizing: border-box;
                    margin-bottom: 10px;
                }
                .row {
                    display: flex;
                    gap: 8px;
                }
                .playing-card {
                    display: inline-block;
                    width: 45px;
                    height: 65px;
                    background-color: white;
                    color: black;
                    border-radius: 4px;
                    text-align: center;
                    font-size: 14px;
                    font-weight: bold;
                    line-height: 65px;
                    margin: 4px;
                    box-shadow: 0 2px 5px rgba(0,0,0,0.3);
                }
                .suit-red {
                    color: #c0392b;
                }
                .player-row {
                    display: flex;
                    justify-content: space-between;
                    padding: 8px 0;
                    border-bottom: 1px solid rgba(255,255,255,0.05);
                }
                .badge {
                    background-color: #ffd700;
                    color: black;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-size: 10px;
                    font-weight: bold;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>${if (isPoker) "⚡ NEON POKER HUB ⚡" else "🏦 NEON BANK PORTAL 🏦"}</h1>
                
                <div id="joinSection" class="card">
                    <h3>Join Game</h3>
                    <input type="text" id="playerName" placeholder="Enter Your Name">
                    <button class="btn btn-primary" onclick="joinGame()">JOIN GAME</button>
                </div>

                <div id="gameSection" class="card" style="display:none;">
                    <div id="gameDetails"></div>
                    <div id="controls" style="margin-top: 16px;"></div>
                </div>
            </div>

            <script>
                let localPlayerName = '';
                const isPoker = ${isPoker};

                function joinGame() {
                    const name = document.getElementById('playerName').value.trim();
                    if (!name) return alert('Enter a name');
                    localPlayerName = name;
                    
                    fetch('/api/action?action=join&name=' + encodeURIComponent(name) + '&amount=1000')
                        .then(r => r.json())
                        .then(data => {
                            document.getElementById('joinSection').style.display = 'none';
                            document.getElementById('gameSection').style.display = 'block';
                            updateUI(data);
                            setInterval(pollState, 1500);
                        });
                }

                function pollState() {
                    fetch('/api/state?name=' + encodeURIComponent(localPlayerName))
                        .then(r => r.json())
                        .then(data => updateUI(data));
                }

                function performAction(action, amount = 0) {
                    fetch('/api/action?action=' + action + '&name=' + encodeURIComponent(localPlayerName) + '&amount=' + amount)
                        .then(r => r.json())
                        .then(data => {
                            pollState();
                        });
                }

                function updateUI(data) {
                    let html = '';
                    if (isPoker) {
                        // Pot and Stage on one single line, keeping current fonts and colors
                        html += '<div style="display: flex; gap: 12px; font-weight: bold; margin-bottom: 8px; font-size: 15px; color: #ffd700;">';
                        html += '<div>Pot: ' + data.pokerPot + ' CHIPS</div>';
                        html += '<div style="color: rgba(255, 255, 255, 0.4);">•</div>';
                        html += '<div style="color: rgba(255, 255, 255, 0.7); font-size: 13px;">Stage: ' + data.pokerStage + '</div>';
                        html += '</div>';

                        // Detailed Hand Assistant (Suggestions Box)
                        if (data.suggestions) {
                            html += '<div style="margin: 12px 0; padding: 12px; background: rgba(255, 215, 0, 0.08); border: 1.5dp solid #ffd700; border-radius: 8px; border-left: 4px solid #ffd700;">';
                            html += '<div style="font-size: 10px; font-weight: bold; color: #ffd700; text-transform: uppercase; margin-bottom: 4px; letter-spacing: 0.5px;">Detailed Assistant</div>';
                            html += '<div style="font-size: 12px; color: #e2e8f0; line-height: 1.4;">' + data.suggestions + '</div>';
                            html += '</div>';
                        }
                        
                        // Community cards
                        html += '<div style="margin: 10px 0;"><strong>Community:</strong><br>';
                        if (data.pokerCommunityCards && data.pokerCommunityCards.length > 0) {
                            data.pokerCommunityCards.forEach(c => {
                                const red = c.colorRed ? 'suit-red' : '';
                                html += '<span class="playing-card ' + red + '">' + c.display + '</span>';
                            });
                        } else {
                            html += 'None';
                        }
                        html += '</div>';

                        // Players List
                        html += '<h3>Players</h3>';
                        data.players.forEach(p => {
                            const isMe = p.name === localPlayerName;
                            const bold = isMe ? 'font-weight: bold; color: #ffd700;' : '';
                            const dealer = p.isDealer ? ' <span class="badge">D</span>' : '';
                            const sb = p.isSmallBlind ? ' <span class="badge" style="background:#27ae60;color:white">SB</span>' : '';
                            const bb = p.isBigBlind ? ' <span class="badge" style="background:#c0392b;color:white">BB</span>' : '';
                            
                            html += '<div class="player-row" style="' + bold + '">';
                            html += '<div>' + p.name + dealer + sb + bb + '</div>';
                            html += '<div>' + p.chips + ' chips (' + p.currentBet + ' bet)</div>';
                            html += '</div>';

                            if (isMe && p.cards && p.cards.length > 0) {
                                html += '<div style="padding: 8px; background: rgba(0,0,0,0.2); border-radius: 8px; margin: 4px 0;">';
                                html += '<strong>Your Hand:</strong><br>';
                                p.cards.forEach(c => {
                                    const red = c.colorRed ? 'suit-red' : '';
                                    html += '<span class="playing-card ' + red + '">' + c.display + '</span>';
                                });
                                html += '</div>';
                            }
                        });

                        // Show actions if it's my turn
                        const myPlayer = data.players.find(p => p.name === localPlayerName);
                        const activePlayer = data.players[data.pokerActivePlayerIndex];
                        const isMyTurn = activePlayer && activePlayer.name === localPlayerName;

                        let controlsHtml = '';
                        if (isMyTurn && data.pokerStage !== 'FINISHED') {
                            controlsHtml += '<div class="row">';
                            controlsHtml += '<button class="btn btn-danger" onclick="performAction(\'fold\')">FOLD</button>';
                            controlsHtml += '<button class="btn btn-primary" onclick="performAction(\'check\')">CHECK/CALL</button>';
                            controlsHtml += '</div>';
                            controlsHtml += '<div style="margin-top:10px;" class="row">';
                            controlsHtml += '<input type="number" id="raiseAmt" placeholder="Raise Amount" style="width:70%;margin:0;">';
                            controlsHtml += '<button class="btn btn-secondary" style="width:30%;margin:0;" onclick="raiseClick()">RAISE</button>';
                            controlsHtml += '</div>';
                        } else {
                            controlsHtml += '<div style="text-align:center;color:gray;">Waiting for turn...</div>';
                        }
                        document.getElementById('controls').innerHTML = controlsHtml;
                    } else {
                        // Bank mode UI with Scroll-sensitive Realtime Winner Ticker
                        let leaderText = "Realtime Leader: ";
                        if (data.players && data.players.length > 0) {
                            let maxBal = -999999;
                            let leaderName = "None";
                            data.players.forEach(p => {
                                const bal = p.chips;
                                if (bal > maxBal) {
                                    maxBal = bal;
                                    leaderName = p.name;
                                }
                            });
                            leaderText += leaderName + " (" + maxBal + " ብር)";
                        } else {
                            leaderText += "None";
                        }
                        html += '<div class="winner-ticker" id="bank-winner-ticker" style="background-color: #1e293b; color: #ffffff; padding: 6px 12px; border-radius: 6px; text-align: center; font-weight: bold; margin-bottom: 12px; font-size: 13px; border: 1px solid #475569;">' + leaderText + '</div>';

                        html += '<div><strong>Current Bank Vault:</strong> ' + data.bankAmount + ' ብር</div>';
                        html += '<h3>Player Ledger</h3>';
                        data.players.forEach(p => {
                            const isMe = p.name === localPlayerName;
                            const style = isMe ? 'font-weight:bold;color:#ffd700;' : '';
                            html += '<div class="player-row" style="' + style + '">';
                            html += '<div>' + p.name + '</div>';
                            html += '<div>' + p.balance + ' ብር</div>';
                            html += '</div>';
                        });

                        let controlsHtml = '';
                        controlsHtml += '<h3>My Banker Actions</h3>';
                        controlsHtml += '<div class="row">';
                        controlsHtml += '<button class="btn btn-primary" onclick="performAction(\'bank_borrow\', 100)">BORROW 100</button>';
                        controlsHtml += '<button class="btn btn-secondary" onclick="performAction(\'bank_return\', 100)">RETURN 100</button>';
                        controlsHtml += '</div>';
                        document.getElementById('controls').innerHTML = controlsHtml;
                    }

                    document.getElementById('gameDetails').innerHTML = html;
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

                function raiseClick() {
                    const amt = parseInt(document.getElementById('raiseAmt').value);
                    if (!amt || amt <= 0) return alert('Enter valid raise amount');
                    performAction('raise', amt);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }
}
