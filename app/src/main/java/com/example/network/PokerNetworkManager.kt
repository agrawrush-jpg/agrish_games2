package com.example.network

import android.util.Log
import com.example.model.Card
import com.example.model.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

sealed class NetworkEvent {
    data class PlayerJoined(val name: String, val initialMoney: Int, val isSimulated: Boolean = false) : NetworkEvent()
    data class PlayerAction(val playerName: String, val action: String, val amount: Int) : NetworkEvent()
    data class MessageReceived(val from: String, val text: String) : NetworkEvent()
    data class ConnectionStatus(val isConnected: Boolean, val message: String) : NetworkEvent()
    data class GameStateReceived(val stateJson: String) : NetworkEvent()
    data class BankerRequestReceived(val playerName: String, val requestedAmount: Int, val isRebuy: Boolean) : NetworkEvent()
    data class BankerResponseReceived(val approved: Boolean, val newAmount: Int) : NetworkEvent()
}

object PokerNetworkManager {
    private const val TAG = "PokerNetworkManager"
    var currentPort = 8888

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null
    private val activeClientWriters = mutableListOf<PrintWriter>()

    private val _events = MutableSharedFlow<NetworkEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<NetworkEvent> = _events

    var isHost = false
    var localIpAddress = "127.0.0.1"

    init {
        resolveLocalIpAddress()
    }

    private fun resolveLocalIpAddress() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val element = interfaces.nextElement()
                val addresses = element.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        localIpAddress = address.hostAddress ?: "127.0.0.1"
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving IP", e)
        }
    }

    fun startHost() {
        isHost = true
        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(currentPort)
                _events.emit(NetworkEvent.ConnectionStatus(true, "Hosting on $localIpAddress:$currentPort"))
                while (true) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleConnectedClient(socket)
                    }
                }
            } catch (e: SocketException) {
                _events.emit(NetworkEvent.ConnectionStatus(false, "Server stopped"))
            } catch (e: Exception) {
                _events.emit(NetworkEvent.ConnectionStatus(false, "Error: ${e.message}"))
            }
        }
    }

    private suspend fun handleConnectedClient(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val writer = PrintWriter(socket.getOutputStream(), true)
        synchronized(activeClientWriters) {
            activeClientWriters.add(writer)
        }

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val message = line ?: break
                Log.d(TAG, "Server received: $message")
                parseIncomingMessage(message, writer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client disconnected or error", e)
        } finally {
            synchronized(activeClientWriters) {
                activeClientWriters.remove(writer)
            }
            try { socket.close() } catch (e: Exception) {}
        }
    }

    fun connectToHost(ip: String, playerName: String, initialMoney: Int) {
        isHost = false
        clientJob?.cancel()
        clientJob = scope.launch {
            try {
                _events.emit(NetworkEvent.ConnectionStatus(false, "Connecting to $ip..."))
                val socket = Socket(ip, currentPort)
                clientSocket = socket
                clientWriter = PrintWriter(socket.getOutputStream(), true)
                
                // Immediately register
                sendToServer("JOIN:$playerName:$initialMoney")
                _events.emit(NetworkEvent.ConnectionStatus(true, "Connected to host!"))

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val message = line ?: break
                    Log.d(TAG, "Client received: $message")
                    parseIncomingMessage(message, null)
                }
            } catch (e: Exception) {
                _events.emit(NetworkEvent.ConnectionStatus(false, "Failed to connect: ${e.message}"))
            }
        }
    }

    private suspend fun parseIncomingMessage(message: String, fromWriter: PrintWriter?) {
        val parts = message.split(":")
        if (parts.isEmpty()) return

        when (parts[0]) {
            "ACTION_STR" -> {
                _events.emit(NetworkEvent.GameStateReceived(message))
            }
            "STATE_JSON" -> {
                _events.emit(NetworkEvent.GameStateReceived(message))
            }
            "JOIN" -> {
                val name = parts.getOrNull(1) ?: "Guest"
                val money = parts.getOrNull(2)?.toIntOrNull() ?: 1000
                _events.emit(NetworkEvent.PlayerJoined(name, money, isSimulated = false))
            }
            "ACTION" -> {
                val name = parts.getOrNull(1) ?: "Guest"
                val action = parts.getOrNull(2) ?: "check"
                val amount = parts.getOrNull(3)?.toIntOrNull() ?: 0
                _events.emit(NetworkEvent.PlayerAction(name, action, amount))
            }
            "BANK_REQUEST" -> {
                val name = parts.getOrNull(1) ?: "Guest"
                val amount = parts.getOrNull(2)?.toIntOrNull() ?: 500
                val isRebuy = parts.getOrNull(3) == "REBUY"
                _events.emit(NetworkEvent.BankerRequestReceived(name, amount, isRebuy))
            }
            "BANK_RESPONSE" -> {
                val approved = parts.getOrNull(1) == "APPROVED"
                val newAmount = parts.getOrNull(2)?.toIntOrNull() ?: 0
                _events.emit(NetworkEvent.BankerResponseReceived(approved, newAmount))
            }
            "STATE" -> {
                // Whole state json or simplified text
                val data = parts.drop(1).joinToString(":")
                _events.emit(NetworkEvent.GameStateReceived(data))
            }
        }
    }

    fun broadcast(message: String) {
        synchronized(activeClientWriters) {
            for (writer in activeClientWriters) {
                scope.launch {
                    try {
                        writer.println(message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Broadcast fail", e)
                    }
                }
            }
        }
    }

    fun sendToServer(message: String) {
        scope.launch {
            try {
                clientWriter?.println(message)
            } catch (e: Exception) {
                Log.e(TAG, "Send to server fail", e)
            }
        }
    }

    // Helper to simulate a second player joining locally for split-screen testing
    fun simulateLocalJoin(name: String, money: Int) {
        scope.launch {
            _events.emit(NetworkEvent.PlayerJoined(name, money, isSimulated = true))
        }
    }

    fun stopAll() {
        try {
            serverSocket?.close()
            clientSocket?.close()
        } catch (e: Exception) {}
        serverJob?.cancel()
        clientJob?.cancel()
        serverSocket = null
        clientSocket = null
        clientWriter = null
        activeClientWriters.clear()
        isHost = false
    }
}
