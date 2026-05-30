package io.tl.snake.network

import io.tl.snake.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.NetworkInterface
import kotlin.random.Random

data class ConnectedPlayer(
    val id: String,
    val name: String,
    val address: String,
    val socket: Socket,
    val writer: PrintWriter,
    val reader: BufferedReader,
    val colorIndex: Int
)

data class ServerStatus(
    val running: Boolean = false,
    val players: List<PlayerInfo> = emptyList(),
    val gameStarted: Boolean = false
)

class LanServer(private val hostName: String, private val scope: CoroutineScope) {
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private val connectedPlayers = mutableListOf<ConnectedPlayer>()
    private var gameState = MultiplayerGameState()
    private var gameJob: Job? = null
    private var isRunning = false

    private val _status = MutableStateFlow(ServerStatus())
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _gameStateFlow = MutableStateFlow(gameState)
    val gameStateFlow: StateFlow<MultiplayerGameState> = _gameStateFlow.asStateFlow()

    companion object {
        private const val TCP_PORT = 54321
        private const val DISCOVERY_PORT = 54322
        private const val DISCOVERY_MAGIC = "SNAKE_DISCOVER"

        val COLORS = listOf(
            0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
            0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF8BC34A,
            0xFF607D8B, 0xFF795548, 0xFFCDDC39, 0xFF03A9F4
        )
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                serverSocket = ServerSocket(TCP_PORT)
                startDiscoveryBroadcast()

                while (isRunning) {
                    val client = serverSocket!!.accept()
                    scope.launch { handleClient(client) }
                }
            } catch (e: Exception) {
                if (isRunning) e.printStackTrace()
            }
        }
    }

    private fun startDiscoveryBroadcast() {
        scope.launch(Dispatchers.IO) {
            try {
                discoverySocket = DatagramSocket(DISCOVERY_PORT)
                discoverySocket!!.broadcast = true
                val buffer = ByteArray(256)

                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    discoverySocket!!.receive(packet)
                    val msg = String(packet.data, 0, packet.length)

                    if (msg == DISCOVERY_MAGIC) {
                        val response = "$DISCOVERY_MAGIC|$hostName|${connectedPlayers.size}".toByteArray()
                        val responsePacket = DatagramPacket(
                            response, response.size,
                            packet.address, packet.port
                        )
                        discoverySocket!!.send(responsePacket)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) e.printStackTrace()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val joinLine = reader.readLine() ?: return@withContext
            val joinMsg = networkJson.decodeFromString<WireMessage>(joinLine)
            if (joinMsg.type != "Join") return@withContext

            val join = networkJson.decodeFromString<ClientMessage.Join>(joinMsg.json)
            val playerId = "player_${Random.nextInt(100000, 999999)}"
            val colorIndex = connectedPlayers.size % COLORS.size

            val clientAddress = socket.inetAddress?.hostAddress ?: "unknown"
            val player = ConnectedPlayer(
                id = playerId,
                name = join.name,
                address = clientAddress,
                socket = socket,
                writer = writer,
                reader = reader,
                colorIndex = colorIndex
            )

            val isHost = synchronized(connectedPlayers) { connectedPlayers.size == 1 }
            synchronized(connectedPlayers) { connectedPlayers.add(player) }
            sendToClient(player, ServerMessage.Welcome(playerId, colorIndex))
            broadcast(ServerMessage.PlayerJoined(PlayerInfo(playerId, join.name, clientAddress, isHost = isHost), colorIndex))
            updateStatus()

            while (isRunning) {
                val line = reader.readLine() ?: break
                try {
                    val wire = networkJson.decodeFromString<WireMessage>(line)
                    when (wire.type) {
                        "Direction" -> {
                            val dir = networkJson.decodeFromString<ClientMessage.Direction>(wire.json)
                            synchronized(gameState) {
                                gameState = gameState.updateDirection(playerId, dir.dir)
                            }
                        }
                        "StartGame" -> {
                            if (!gameState.isGameOver) {
                                scope.launch { startGame() }
                            }
                        }
                        "Leave" -> break
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        finally {
            val p = synchronized(connectedPlayers) {
                connectedPlayers.find { it.socket == socket }
            }
            if (p != null) {
                synchronized(connectedPlayers) { connectedPlayers.remove(p) }
                broadcast(ServerMessage.PlayerLeft(p.id))
                updateStatus()
            }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private suspend fun startGame() {
        if (gameJob?.isActive == true) return
        synchronized(gameState) {
            val players = synchronized(connectedPlayers) { connectedPlayers.toList() }
            gameState = MultiplayerGameState.create(players.map { it.id to it.name })
        }
        broadcast(ServerMessage.GameStarted)

        _gameStateFlow.value = gameState
        gameJob = scope.launch {
            while (isRunning) {
                val currentState = synchronized(gameState) { gameState }
                if (currentState.isGameOver) {
                    val winner = currentState.players.maxByOrNull { it.score }
                    if (winner != null) {
                        broadcast(ServerMessage.GameOver(winner.id, winner.name))
                    }
                    break
                }

                delay(gameTickIntervalMs(currentState))
                val updatedState = synchronized(gameState) {
                    gameState = multiplayerGameTick(gameState)
                    gameState
                }
                _gameStateFlow.value = updatedState

                val serialized = serializeGameState(updatedState)
                broadcast(ServerMessage.GameState(serialized))
            }
        }
    }

    private fun serializeGameState(gs: MultiplayerGameState): SerializedGameState {
        return SerializedGameState(
            players = gs.players.map { p ->
                SerializedPlayerState(
                    id = p.id,
                    name = p.name,
                    snake = p.snake.map { SerializedSnakeSegment(it.first, it.second) },
                    direction = p.direction.name,
                    score = p.score,
                    isAlive = p.isAlive,
                    shieldCount = p.shieldCount,
                    ghostTimeRemaining = p.ghostTimeRemaining,
                    invincibleTimeRemaining = p.invincibleTimeRemaining,
                    colorIndex = gs.players.indexOf(p)
                )
            },
            objects = gs.objects.map { SerializedGameObject(it.pos.first, it.pos.second, it.type.name, it.timeLeft) },
            gridWidth = gs.gridWidth,
            gridHeight = gs.gridHeight,
            isGameOver = gs.isGameOver,
            winnerId = gs.winnerId
        )
    }

    private fun sendToClient(player: ConnectedPlayer, msg: ServerMessage) {
        scope.launch(Dispatchers.IO) {
            try { player.writer.println(serializeServerMessage(msg)) }
            catch (_: Exception) {}
        }
    }

    private fun broadcast(msg: ServerMessage) {
        val msgStr = serializeServerMessage(msg)
        scope.launch(Dispatchers.IO) {
            synchronized(connectedPlayers) {
                connectedPlayers.forEach { p ->
                    try { p.writer.println(msgStr) }
                    catch (_: Exception) {}
                }
            }
        }
    }

    private fun updateStatus() {
        synchronized(connectedPlayers) {
            _status.value = ServerStatus(
                running = true,
                players = connectedPlayers.mapIndexed { i, p -> PlayerInfo(p.id, p.name, p.address, isHost = i == 0) },
                gameStarted = gameJob?.isActive == true
            )
        }
    }

    fun stop() {
        isRunning = false
        gameJob?.cancel()
        synchronized(connectedPlayers) {
            connectedPlayers.forEach {
                try { it.socket.close() } catch (_: Exception) {}
            }
            connectedPlayers.clear()
        }
        try { serverSocket?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        _status.value = ServerStatus()
    }
}

private fun gameTickIntervalMs(state: MultiplayerGameState): Long {
    val maxScore = state.players.maxOfOrNull { it.score } ?: 0
    return (GameConfig.BASE_SPEED - (maxScore / 100 * 5)).coerceAtLeast(GameConfig.MIN_SPEED)
}
