package io.tl.snake.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.NetworkInterface

data class DiscoveredServer(
    val address: InetAddress,
    val name: String,
    val playerCount: Int
)

data class ClientStatus(
    val connected: Boolean = false,
    val playerId: String = "",
    val colorIndex: Int = 0,
    val gameStarted: Boolean = false,
    val lobbyPlayers: List<PlayerInfo> = emptyList()
)

class LanClient(private val scope: CoroutineScope) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private var isRunning = false

    private val _status = MutableStateFlow(ClientStatus())
    val status: StateFlow<ClientStatus> = _status.asStateFlow()

    private val _gameState = MutableStateFlow<SerializedGameState?>(null)
    val gameState: StateFlow<SerializedGameState?> = _gameState.asStateFlow()

    private val _gameOver = MutableStateFlow<Pair<String, String>?>(null)
    val gameOver: StateFlow<Pair<String, String>?> = _gameOver.asStateFlow()

    companion object {
        private const val TCP_PORT = 54321
        private const val DISCOVERY_PORT = 54322
        private const val DISCOVERY_MAGIC = "SNAKE_DISCOVER"
        const val DISCOVERY_TIMEOUT = 2000L
    }

    fun connect(serverAddress: InetAddress, playerName: String) {
        if (isRunning) return
        isRunning = true

        scope.launch(Dispatchers.IO) {
            try {
                socket = Socket()
                socket?.connect(InetSocketAddress(serverAddress, TCP_PORT), 5000)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                writer!!.println(serializeClientMessage(ClientMessage.Join(playerName)))

                while (isRunning) {
                    val line = reader!!.readLine() ?: break
                    try {
                        val wire = networkJson.decodeFromString<WireMessage>(line)
                        processServerMessage(wire)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            finally {
                disconnect()
            }
        }
    }

    private fun processServerMessage(wire: WireMessage) {
        try {
            when (wire.type) {
                "Welcome" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.Welcome>(wire.json)
                    _status.value = _status.value.copy(
                        connected = true,
                        playerId = msg.playerId,
                        colorIndex = msg.colorIndex
                    )
                }
                "PlayerJoined" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.PlayerJoined>(wire.json)
                    _status.value = _status.value.copy(
                        lobbyPlayers = _status.value.lobbyPlayers + msg.player
                    )
                }
                "PlayerLeft" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.PlayerLeft>(wire.json)
                    _status.value = _status.value.copy(
                        lobbyPlayers = _status.value.lobbyPlayers.filter { it.id != msg.playerId }
                    )
                }
                "LobbyUpdate" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.LobbyUpdate>(wire.json)
                    _status.value = _status.value.copy(lobbyPlayers = msg.players)
                }
                "GameStarted" -> {
                    _status.value = _status.value.copy(gameStarted = true)
                }
                "GameState" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.GameState>(wire.json)
                    _gameState.value = msg.state
                }
                "GameOver" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.GameOver>(wire.json)
                    _gameOver.value = msg.winnerId to msg.winnerName
                }
                "Error" -> {
                    val msg = networkJson.decodeFromString<ServerMessage.Error>(wire.json)
                    println("Server error: ${msg.message}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendDirection(dir: String) {
        scope.launch(Dispatchers.IO) {
            try {
                writer?.println(serializeClientMessage(ClientMessage.Direction(dir)))
            } catch (_: Exception) {}
        }
    }

    fun requestStartGame() {
        scope.launch(Dispatchers.IO) {
            try {
                writer?.println(serializeClientMessage(ClientMessage.StartGame))
            } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        isRunning = false
        try {
            writer?.println(serializeClientMessage(ClientMessage.Leave))
        } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        reader = null
        socket = null
        _status.value = ClientStatus()
        _gameState.value = null
        _gameOver.value = null
    }

    suspend fun discoverServers(): List<DiscoveredServer> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<DiscoveredServer>()
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT.toInt()

            val broadcastAddr = getBroadcastAddress()
            val packet = DatagramPacket(
                DISCOVERY_MAGIC.toByteArray(),
                DISCOVERY_MAGIC.length,
                broadcastAddr, DISCOVERY_PORT
            )
            socket.send(packet)

            val buffer = ByteArray(256)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val data = String(response.data, 0, response.length)
                    val parts = data.split("|")
                    if (parts.size >= 3 && parts[0] == DISCOVERY_MAGIC) {
                        servers.add(DiscoveredServer(
                            address = response.address,
                            name = parts[1],
                            playerCount = parts[2].toIntOrNull() ?: 0
                        ))
                    }
                } catch (_: Exception) { break }
            }
            socket.close()
        } catch (_: Exception) {}
        servers.distinctBy { it.address }
    }

    private fun getBroadcastAddress(): InetAddress {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isLoopback && ni.isUp) {
                    val addresses = ni.interfaceAddresses
                    for (addr in addresses) {
                        val broadcast = addr.broadcast ?: continue
                        return broadcast
                    }
                }
            }
        } catch (_: Exception) {}
        return InetAddress.getByName("255.255.255.255")
    }
}
