package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class PokerHostServer(private val context: Context, private val onDataReceived: (String) -> Unit) {
    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private val clientHandlers = mutableListOf<ClientHandler>()
    private val SERVICE_TYPE = "_neonpoker._tcp."

    fun startServer() {
        thread {
            try {
                serverSocket = ServerSocket(0) // Automatically assigns a free port
                registerService(serverSocket!!.localPort)
                
                while (serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket!!.accept()
                        val handler = ClientHandler(clientSocket, onDataReceived)
                        clientHandlers.add(handler)
                        thread { handler.run() }
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "NeonPokerHost"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            })
        }
    }

    fun broadcast(message: String) {
        for (handler in clientHandlers) {
            handler.send(message)
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // In Android, unregistering is safer with non-null checks
        nsdManager?.let { manager ->
            try {
                // Do safe cleanup
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

class ClientHandler(private val socket: Socket, private val onDataReceived: (String) -> Unit) {
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val writer = PrintWriter(socket.getOutputStream(), true)

    fun run() {
        try {
            var inputLine: String?
            while (reader.readLine().also { inputLine = it } != null) {
                onDataReceived(inputLine!!)
            }
        } catch (e: Exception) {
            // Handle disconnect logic
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun send(message: String) {
        thread { 
            try {
                writer.println(message) 
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
