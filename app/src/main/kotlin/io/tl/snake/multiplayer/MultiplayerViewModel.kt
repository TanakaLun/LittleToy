package io.tl.snake.multiplayer

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tl.snake.data.GameRepository
import io.tl.snake.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

enum class MultiplayerScreen {
    ROOM_BROWSER, LOBBY, GAME, VICTORY
}

data class MultiplayerUiState(
    val showDialog: Boolean = false,
    val screen: MultiplayerScreen = MultiplayerScreen.ROOM_BROWSER,
    val isHost: Boolean = false,
    val playerName: String = "",
    val playerId: String = "",
    val hostAddress: String = "",
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val lobbyPlayers: List<PlayerInfo> = emptyList(),
    val gameStarted: Boolean = false,
    val gameState: SerializedGameState? = null,
    val winnerId: String = "",
    val winnerName: String = "",
    val isDiscovering: Boolean = false
)

class MultiplayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)
    private var server: LanServer? = null
    private var client: LanClient? = null
    private var identity = PlayerIdentity()

    var uiState by mutableStateOf(MultiplayerUiState())
        private set

    init {
        viewModelScope.launch {
            identity = repository.loadPlayerIdentity()
            uiState = uiState.copy(playerName = identity.playerName)
        }
    }

    fun updatePlayerName(name: String) {
        identity = identity.copy(playerName = name)
        uiState = uiState.copy(playerName = name)
        viewModelScope.launch { repository.savePlayerIdentity(identity) }
    }

    fun openMultiplayerDialog() {
        uiState = MultiplayerUiState(
            showDialog = true,
            screen = MultiplayerScreen.ROOM_BROWSER,
            playerName = identity.playerName,
            playerId = identity.playerId
        )
        startAutoRefresh()
    }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (uiState.showDialog && uiState.screen == MultiplayerScreen.ROOM_BROWSER) {
                refreshServerList()
                delay(3000)
            }
        }
    }

    fun hostGame() {
        autoRefreshJob?.cancel()
        uiState = uiState.copy(isHost = true, screen = MultiplayerScreen.LOBBY)
        server = LanServer(uiState.playerName, viewModelScope)
        server!!.start()

        client = LanClient(viewModelScope)
        client!!.connect(java.net.InetAddress.getLoopbackAddress(), uiState.playerName)
        observeClient()
    }

    fun joinGame(serverAddress: java.net.InetAddress) {
        autoRefreshJob?.cancel()
        uiState = uiState.copy(isHost = false, hostAddress = serverAddress.hostAddress ?: "", screen = MultiplayerScreen.LOBBY)
        client = LanClient(viewModelScope)
        client!!.connect(serverAddress, uiState.playerName)
        observeClient()
    }

    private fun observeClient() {
        viewModelScope.launch {
            client?.status?.collectLatest { status ->
                uiState = uiState.copy(
                    playerId = status.playerId,
                    lobbyPlayers = status.lobbyPlayers,
                    gameStarted = status.gameStarted
                )
                if (status.gameStarted) {
                    uiState = uiState.copy(screen = MultiplayerScreen.GAME, showDialog = false)
                }
            }
        }

        viewModelScope.launch {
            client?.gameState?.collectLatest { gs ->
                if (gs != null) uiState = uiState.copy(gameState = gs)
            }
        }

        viewModelScope.launch {
            client?.gameOver?.collectLatest { result ->
                if (result != null) {
                    val (winnerId, winnerName) = result
                    uiState = uiState.copy(
                        screen = MultiplayerScreen.VICTORY,
                        showDialog = false,
                        winnerId = winnerId,
                        winnerName = winnerName
                    )
                }
            }
        }

        viewModelScope.launch {
            server?.status?.collectLatest { serverStatus ->
                uiState = uiState.copy(
                    lobbyPlayers = serverStatus.players
                )
            }
        }
    }

    fun startMultiplayerGame() {
        if (uiState.isHost) client?.requestStartGame()
    }

    fun sendDirection(dir: String) {
        client?.sendDirection(dir)
    }

    suspend fun refreshServerList() {
        uiState = uiState.copy(isDiscovering = true)
        try {
            val discoverClient = LanClient(viewModelScope)
            val servers = discoverClient.discoverServers()
            uiState = uiState.copy(discoveredServers = servers, isDiscovering = false)
        } catch (_: Exception) {
            uiState = uiState.copy(isDiscovering = false)
        }
    }

    fun closeDialog() {
        autoRefreshJob?.cancel()
        cleanup()
        uiState = MultiplayerUiState(playerName = identity.playerName)
    }

    fun backToBrowser() {
        autoRefreshJob?.cancel()
        cleanup()
        uiState = uiState.copy(
            screen = MultiplayerScreen.ROOM_BROWSER,
            isHost = false,
            hostAddress = "",
            gameStarted = false,
            gameState = null,
            lobbyPlayers = emptyList(),
            winnerId = "",
            winnerName = ""
        )
        startAutoRefresh()
    }

    fun backToLobby() {
        uiState = uiState.copy(
            screen = MultiplayerScreen.LOBBY,
            gameStarted = false,
            gameState = null,
            winnerId = "",
            winnerName = ""
        )
    }

    private fun cleanup() {
        client?.disconnect()
        server?.stop()
        client = null
        server = null
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        cleanup()
    }
}
