package io.tl.snake

import android.graphics.Paint
import android.os.*
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tl.snake.logic.*
import io.tl.snake.multiplayer.MultiplayerScreen
import io.tl.snake.multiplayer.MultiplayerViewModel
import io.tl.snake.network.DiscoveredServer
import io.tl.snake.network.PlayerInfo
import io.tl.snake.network.SerializedGameState
import io.tl.snake.network.SerializedPlayerState
import io.tl.snake.ui.GameViewModel
import io.tl.snake.ui.theme.MyTheme
import kotlin.random.Random

val ItemType.icon: ImageVector get() = when(this) {
    ItemType.FOOD_BASIC -> Icons.Default.Fastfood; ItemType.FOOD_GOLD -> Icons.Default.Star
    ItemType.FOOD_POISON -> Icons.Default.Dangerous; ItemType.DIAMOND -> Icons.Default.Diamond
    ItemType.COFFEE -> Icons.Default.Coffee; ItemType.CHILI -> Icons.Default.Whatshot
    ItemType.SHIELD -> Icons.Default.Shield; ItemType.CLOVER -> Icons.Default.LocalFlorist
    ItemType.SLOW -> Icons.Default.AvTimer; ItemType.GHOST -> Icons.Default.Deblur
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTheme {
                val vm: GameViewModel = viewModel()
                val mpVm: MultiplayerViewModel = viewModel()
                val state = vm.state
                val settings = vm.settings
                var showSettings by remember { mutableStateOf(false) }
                var showPlayerNameDialog by remember { mutableStateOf(false) }
                val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)!!
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(state.lastEvent) {
                    if (settings.enableVibration && state.lastEvent != null) {
                        val ms = when (state.lastEvent) {
                            GameEvent.EAT_GOOD -> 30L; GameEvent.EAT_BAD -> 80L
                            GameEvent.SHIELD_BREAK -> 150L; GameEvent.HIT_WALL -> 250L
                        }
                        if (ms > 0) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }

                LaunchedEffect(settings.isTVMode, state.isPaused, state.isStarted, showSettings) {
                    if (settings.isTVMode && !state.isPaused && state.isStarted && !showSettings) {
                        focusRequester.requestFocus()
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    val mpScreen = mpVm.uiState.screen
                    val showMpFullScreen = !mpVm.uiState.showDialog &&
                        (mpScreen == MultiplayerScreen.GAME || mpScreen == MultiplayerScreen.VICTORY)

                    if (showMpFullScreen) {
                        when (mpScreen) {
                            MultiplayerScreen.GAME -> MultiplayerGameView(mpVm)
                            MultiplayerScreen.VICTORY -> MultiplayerVictoryScreen(
                                winnerId = mpVm.uiState.winnerId,
                                winnerName = mpVm.uiState.winnerName,
                                myId = mpVm.uiState.playerId,
                                onBackToLobby = { mpVm.backToLobby() },
                                onBackToMenu = { mpVm.closeDialog() }
                            )
                        }
                    } else {
                        Scaffold(
                            topBar = { GameTopBar(state, settings, { vm.togglePause() }, { vm.setPaused(true); showSettings = true }, { vm.resetHighScore() }) }
                        ) { p ->
                            Box(Modifier.padding(p).fillMaxSize()) {
                                GameContent(state, settings, vm, focusRequester, onSwitchToMultiplayer = {
                                    if (mpVm.uiState.playerName.isBlank()) showPlayerNameDialog = true
                                    else mpVm.openMultiplayerDialog()
                                })

                                if (showSettings) {
                                    SettingsDialog(
                                        settings = settings,
                                        playerName = mpVm.uiState.playerName,
                                        onDismiss = { showSettings = false; vm.startResumeCountdown() },
                                        onUpdate = { vm.updateSettings(it) },
                                        onRename = { showSettings = false; showPlayerNameDialog = true }
                                    )
                                }
                                if (state.isGameOver) {
                                    ResultDialog(state, settings, onRestart = { vm.restartGame() }, onOpenSettings = { showSettings = true })
                                } else if (state.isStarted && state.isPaused && vm.countdown == 0 && !showSettings) {
                                    PauseStatsDialog(state) { vm.togglePause() }
                                }
                            }
                        }
                        if (vm.countdown > 0) {
                            CountdownOverlay(vm.countdown)
                        }
                    }

                    if (mpVm.uiState.showDialog) {
                        MultiplayerDialog(mpVm)
                    }

                    if (showPlayerNameDialog) {
                        PlayerNameDialog(
                            currentName = mpVm.uiState.playerName,
                            onConfirm = { name ->
                                mpVm.updatePlayerName(name)
                                showPlayerNameDialog = false
                            },
                            onDismiss = { showPlayerNameDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MultiplayerDialog(mpVm: MultiplayerViewModel) {
    when (mpVm.uiState.screen) {
        MultiplayerScreen.ROOM_BROWSER -> RoomBrowserDialog(mpVm)
        MultiplayerScreen.LOBBY -> MultiplayerLobbyDialog(mpVm)
        else -> {}
    }
}

@Composable
fun PlayerNameDialog(currentName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Your Name", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                label = { Text("Player Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name.trim()) })
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("CONFIRM") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@Composable
fun RoomBrowserDialog(mpVm: MultiplayerViewModel) {
    AlertDialog(
        onDismissRequest = { mpVm.closeDialog() },
        title = { Text("LAN Multiplayer", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Playing as: ${mpVm.uiState.playerName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { mpVm.hostGame() },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Wifi, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Room", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open Rooms", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (mpVm.uiState.isDiscovering) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))

                val servers = mpVm.uiState.discoveredServers
                if (servers.isEmpty() && !mpVm.uiState.isDiscovering) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("No rooms found", color = MaterialTheme.colorScheme.onSurface.copy(0.4f), fontSize = 13.sp)
                    }
                } else {
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        servers.forEach { server ->
                            Surface(
                                onClick = { mpVm.joinGame(server.address) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
                            ) {
                                Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Computer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(server.address.hostAddress ?: "?", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    }
                                    Surface(color = MaterialTheme.colorScheme.primary.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                                        Text("${server.playerCount}", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { mpVm.closeDialog() }) { Text("CLOSE") } }
    )
}

@Composable
fun MultiplayerLobbyDialog(mpVm: MultiplayerViewModel) {
    val isHost = mpVm.uiState.isHost
    val players = remember(mpVm.uiState.lobbyPlayers) {
        mpVm.uiState.lobbyPlayers.sortedByDescending { it.isHost }
    }

    AlertDialog(
        onDismissRequest = { mpVm.backToBrowser() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Game Room", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    if (isHost) "Waiting for players... (min 2 to start)"
                    else "Waiting for host to start the game...",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Spacer(Modifier.height(16.dp))

                Text("Participants (${players.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    players.forEach { player ->
                        val isHostPlayer = player.isHost
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            color = if (isHostPlayer) MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).background(
                                        if (isHostPlayer) Color(0xFFFFD700) else Color(0xFF4CAF50),
                                        CircleShape
                                    )
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(player.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        if (isHostPlayer) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiary.copy(0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "HOST",
                                                    Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }
                                    Text("IP: ${player.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.45f))
                                }
                                if (player.id == mpVm.uiState.playerId) {
                                    Surface(color = MaterialTheme.colorScheme.primary.copy(0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("YOU", Modifier.padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isHost) {
                    Button(
                        onClick = { mpVm.startMultiplayerGame() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = players.size >= 2
                    ) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("START GAME", fontWeight = FontWeight.Bold)
                    }
                    if (players.size < 2) {
                        Text("Need at least 2 players", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(0.6f), modifier = Modifier.padding(top = 4.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { mpVm.backToBrowser() },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("BACK", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
fun MultiplayerVictoryScreen(winnerId: String, winnerName: String, myId: String, onBackToLobby: () -> Unit, onBackToMenu: () -> Unit) {
    val isWinner = winnerId == myId
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isWinner) Icons.Default.EmojiEvents else Icons.Default.MilitaryTech,
                null, Modifier.size(80.dp),
                tint = if (isWinner) Color(0xFFFFD700) else Color.White.copy(0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isWinner) "YOU WIN!" else "$winnerName WINS!",
                fontWeight = FontWeight.Black, fontSize = 40.sp, color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (isWinner) "Congratulations!" else "Better luck next time.",
                color = Color.White.copy(0.6f), fontSize = 16.sp
            )
            Spacer(Modifier.height(40.dp))
            Button(onClick = onBackToLobby, modifier = Modifier.width(200.dp)) {
                Icon(Icons.Default.Replay, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("PLAY AGAIN", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBackToMenu) {
                Text("BACK TO MENU", color = Color.White.copy(0.5f))
            }
        }

        val particles = remember { List(60) { ConfettiParticle() } }
        val infiniteTransition = rememberInfiniteTransition("victory_confetti")
        val progress by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), "")
        Canvas(Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val y = (p.startY + (progress * 1800f * p.speed)) % size.height
                val x = p.startX + (progress * 300f * p.drift)
                drawRect(color = p.color, topLeft = Offset(x, y), size = Size(12f, 24f), alpha = 1f - (y / size.height).coerceIn(0f, 1f))
            }
        }
    }
}

@Composable
fun CountdownOverlay(count: Int) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).pointerInput(Unit) {}, contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (scaleIn(tween(500, easing = EaseOutBack)) + fadeIn()).togetherWith(scaleOut(tween(500)) + fadeOut())
            }, label = "countdown_anim"
        ) { targetCount ->
            Text(
                text = "$targetCount", fontSize = 140.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GameContent(state: SnakeState, settings: GameSettings, vm: GameViewModel, focusRequester: FocusRequester, onSwitchToMultiplayer: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                .then(if (settings.isTVMode) {
                    Modifier.focusRequester(focusRequester).focusable().onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> { vm.setDirection(Direction.UP); true }
                                KeyEvent.KEYCODE_DPAD_DOWN -> { vm.setDirection(Direction.DOWN); true }
                                KeyEvent.KEYCODE_DPAD_LEFT -> { vm.setDirection(Direction.LEFT); true }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.setDirection(Direction.RIGHT); true }
                                KeyEvent.KEYCODE_BACK -> { vm.togglePause(); true }
                                else -> false
                            }
                        } else false
                    }
                } else Modifier)
        ) {
            GameCanvasArea(state, settings, vm, onSwitchToMultiplayer)
        }

        if (!settings.isTVMode && settings.controlMode == ControlMode.BUTTONS) {
            ControlButtonsRow(onDirChange = { vm.setDirection(it) })
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun ControlButtonsRow(onDirChange: (Direction) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val size = 56.dp
        val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.7f)

        @Composable
        fun DirIconButton(icon: ImageVector, dir: Direction) {
            IconButton(onClick = { onDirChange(dir) }, modifier = Modifier.size(size).background(containerColor, CircleShape)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        @Suppress("DEPRECATION")
        DirIconButton(Icons.Default.ArrowBack, Direction.LEFT)
        DirIconButton(Icons.Default.ArrowUpward, Direction.UP)
        DirIconButton(Icons.Default.ArrowDownward, Direction.DOWN)
        @Suppress("DEPRECATION")
        DirIconButton(Icons.Default.ArrowForward, Direction.RIGHT)
    }
}

@Composable
fun GameCanvasArea(state: SnakeState, settings: GameSettings, vm: GameViewModel, onSwitchToMultiplayer: () -> Unit = {}) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition("game_anim")
    val decayAlpha by infiniteTransition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(200), RepeatMode.Reverse), "flash")
    val decayScale by infiniteTransition.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "breath")

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalContext.current.resources.displayMetrics.density
        val cellSizePx = settings.targetCellSize * density
        val gridW = if (settings.dynamicGrid) (constraints.maxWidth / cellSizePx).toInt().coerceAtLeast(10) else 20
        val gridH = if (settings.dynamicGrid) (constraints.maxHeight / cellSizePx).toInt().coerceAtLeast(10) else 20
        LaunchedEffect(gridW, gridH) { vm.updateGridSize(gridW, gridH) }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.shieldCount > 0) {
                Surface(Modifier.align(Alignment.TopEnd).offset(y = (-38.dp)), color = Color(ItemType.SHIELD.colorHex), shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, Modifier.size(14.dp), tint = Color.White)
                        Text("${state.shieldCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            Box(
                Modifier.size(
                    (cellSizePx * gridW / density).dp,
                    (cellSizePx * gridH / density).dp
                ).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant.copy(0.3f))
                    .border(1.dp, colorScheme.outlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            ) {
                Canvas(
                    Modifier.fillMaxSize().then(
                        if (!settings.isTVMode && settings.controlMode == ControlMode.SWIPE) {
                            Modifier.pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); vm.handleSwipe(drag.x, drag.y) } }
                        } else Modifier
                    )
                ) {
                    drawSinglePlayerGame(state, settings, cellSizePx, colorScheme, decayAlpha, decayScale)
                }

                if (!state.isStarted) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { vm.startGame() },
                                modifier = Modifier.height(52.dp).width(180.dp).then(if (settings.isTVMode) Modifier.focusable() else Modifier),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("START", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onSwitchToMultiplayer,
                                modifier = Modifier.height(52.dp).width(180.dp).then(if (settings.isTVMode) Modifier.focusable() else Modifier),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(Icons.Default.People, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("MULTIPLAYER", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        if (state.isGameOver && state.score >= state.highScore && state.score > 0) ConfettiEffect()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSinglePlayerGame(
    state: SnakeState,
    settings: GameSettings,
    cellSizePx: Float,
    colorScheme: androidx.compose.material3.ColorScheme,
    decayAlpha: Float,
    decayScale: Float
) {
    if (settings.showGrid) {
        for (i in 0..state.gridWidth) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, size.height))
        for (i in 0..state.gridHeight) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(size.width, i * cellSizePx))
    }
    if (state.invincibleTimeRemaining > 0) {
        val progress = state.invincibleTimeRemaining.toFloat() / GameConfig.INVINCIBLE_DURATION_MS
        drawRect(Color(0xFF00E5FF), Offset(0f, 0f), Size(size.width * progress, 4.dp.toPx()))
    } else if (state.ghostTimeRemaining > 0 && !settings.isGhostPermanent) {
        val progress = state.ghostTimeRemaining.toFloat() / GameConfig.GHOST_DURATION_MS
        drawRect(Color(ItemType.GHOST.colorHex), Offset(0f, 0f), Size(size.width * progress, 4.dp.toPx()))
    }
    state.objects.forEach { obj ->
        val alpha = if (settings.enableItemDecay && obj.timeLeft < 5000L) decayAlpha else 1f
        drawCircle(Color(obj.type.colorHex).copy(alpha), (cellSizePx * 0.35f) * (if (alpha < 1f) decayScale else 1f), Offset(obj.pos.first * cellSizePx + cellSizePx / 2f, obj.pos.second * cellSizePx + cellSizePx / 2f))
    }
    state.snake.forEachIndexed { i, p ->
        val color = when {
            i == 0 && state.invincibleTimeRemaining > 0 -> Color(0xFF00E5FF)
            i == 0 && (state.ghostTimeRemaining > 0 || settings.isGhostPermanent) -> Color(ItemType.GHOST.colorHex)
            i == 0 && state.shieldCount > 0 -> Color(ItemType.SHIELD.colorHex)
            else -> colorScheme.primary
        }
        drawRoundRect(color.copy((1f - (i.toFloat() / state.snake.size)).coerceAtLeast(0.2f)), Offset(p.first * cellSizePx + 1.5f, p.second * cellSizePx + 1.5f), Size(cellSizePx - 3f, cellSizePx - 3f), CornerRadius(4.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMultiplayerGame(
    state: SerializedGameState,
    cellSizePx: Float,
    viewOffX: Float,
    viewOffY: Float,
    viewW: Float,
    viewH: Float
) {
    val gridColor = Color(0xFF1E3A5F).copy(0.3f)
    val startX = (-viewOffX % cellSizePx).let { if (it < 0) it + cellSizePx else it }
    val startY = (-viewOffY % cellSizePx).let { if (it < 0) it + cellSizePx else it }

    var x = startX
    while (x < viewW) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, viewH))
        x += cellSizePx
    }
    var y = startY
    while (y < viewH) {
        drawLine(gridColor, Offset(0f, y), Offset(viewW, y))
        y += cellSizePx
    }

    val visLeft = (-viewOffX / cellSizePx).toInt() - 1
    val visTop = (-viewOffY / cellSizePx).toInt() - 1
    val visRight = visLeft + (viewW / cellSizePx).toInt() + 2
    val visBottom = visTop + (viewH / cellSizePx).toInt() + 2

    state.objects.forEach { obj ->
        val sx = obj.x * cellSizePx + viewOffX + cellSizePx / 2f
        val sy = obj.y * cellSizePx + viewOffY + cellSizePx / 2f
        if (sx in -cellSizePx..viewW + cellSizePx && sy in -cellSizePx..viewH + cellSizePx) {
            val color = try { Color(ItemType.valueOf(obj.typeName).colorHex) } catch (_: Exception) { Color.Gray }
            drawCircle(color.copy(0.8f), cellSizePx * 0.3f, Offset(sx, sy))
        }
    }

    state.players.sortedBy { it.score }.forEach { player ->
        if (!player.isAlive) return@forEach
        val color = playerColor(player.colorIndex)
        val alpha = if (player.snake.isEmpty()) 0f else 1f

        player.snake.forEachIndexed { i, seg ->
            val sx = seg.x * cellSizePx + viewOffX
            val sy = seg.y * cellSizePx + viewOffY
            if (sx in -cellSizePx..viewW + cellSizePx && sy in -cellSizePx..viewH + cellSizePx) {
                val segmentAlpha = alpha * (1f - (i.toFloat() / player.snake.size.coerceAtLeast(1)) * 0.7f).coerceAtLeast(0.3f)
                val isHead = i == 0
                if (isHead) {
                    drawRoundRect(color.copy(segmentAlpha), Offset(sx + 1f, sy + 1f), Size(cellSizePx - 2f, cellSizePx - 2f), CornerRadius(cellSizePx * 0.3f))
                } else {
                    drawRoundRect(color.copy(segmentAlpha), Offset(sx + 1.5f, sy + 1.5f), Size(cellSizePx - 3f, cellSizePx - 3f), CornerRadius(4.dp.toPx()))
                }
            }
        }

        if (player.snake.isNotEmpty()) {
            val head = player.snake.first()
            val hx = head.x * cellSizePx + viewOffX
            val hy = head.y * cellSizePx + viewOffY - cellSizePx * 0.4f
            if (hx in -cellSizePx * 2..viewW + cellSizePx * 2 && hy in -cellSizePx * 2..viewH + cellSizePx * 2) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        player.name, hx, hy,
                        Paint().apply {
                            setColor(android.graphics.Color.WHITE)
                            textSize = cellSizePx * 0.45f
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                            setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
                        }
                    )
                }
            }
        }
    }
}

private fun playerColor(index: Int): Color {
    val colors = listOf(
        0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63,
        0xFF9C27B0, 0xFF00BCD4, 0xFFFF5722, 0xFF8BC34A,
        0xFF607D8B, 0xFF795548, 0xFFCDDC39, 0xFF03A9F4
    )
    return Color(colors[index % colors.size])
}

@Composable
fun MultiplayerLeaderboardOverlay(state: SerializedGameState) {
    val sorted = state.players.sortedByDescending { it.score }
    if (sorted.isEmpty()) return

    Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.TopEnd) {
        Surface(
            color = Color.Black.copy(0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(140.dp)
        ) {
            Column(Modifier.padding(8.dp)) {
                Text("LEADERBOARD", fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.White.copy(0.7f))
                Spacer(Modifier.height(6.dp))
                sorted.forEachIndexed { i, player ->
                    val rankColor = when (i) {
                        0 -> Color(0xFFFFD700)
                        1 -> Color(0xFFC0C0C0)
                        2 -> Color(0xFFCD7F32)
                        else -> Color.White.copy(0.6f)
                    }
                    val medal = when (i) {
                        0 -> "1st"
                        1 -> "2nd"
                        2 -> "3rd"
                        else -> "${i + 1}th"
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(medal, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = rankColor, modifier = Modifier.width(24.dp))
                        Text(
                            player.name,
                            fontSize = if (i < 3) 11.sp else 10.sp,
                            fontWeight = if (i < 3) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text("${player.score}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = rankColor)
                    }
                }
            }
        }
    }
}

@Composable
fun MultiplayerGameView(mpVm: MultiplayerViewModel) {
    val state = mpVm.uiState.gameState ?: return
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23))) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalContext.current.resources.displayMetrics.density
                val cellSizePx = 14f * density
                val gridW = state.gridWidth
                val gridH = state.gridHeight

                val myPlayer = mpVm.uiState.playerId.let { myId ->
                    state.players.find { it.id == myId } ?: state.players.firstOrNull()
                }
                val viewOffX = if (myPlayer != null && myPlayer.snake.isNotEmpty()) {
                    -(myPlayer.snake.first().x * cellSizePx - constraints.maxWidth / 2f)
                } else 0f
                val viewOffY = if (myPlayer != null && myPlayer.snake.isNotEmpty()) {
                    -(myPlayer.snake.first().y * cellSizePx - constraints.maxHeight / 2f)
                } else 0f

                Canvas(
                    Modifier.fillMaxSize()
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> { mpVm.sendDirection("UP"); true }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> { mpVm.sendDirection("DOWN"); true }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> { mpVm.sendDirection("LEFT"); true }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> { mpVm.sendDirection("RIGHT"); true }
                                    else -> false
                                }
                            } else false
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val dir = if (kotlin.math.abs(drag.x) > kotlin.math.abs(drag.y)) {
                                    if (drag.x > 0) "RIGHT" else "LEFT"
                                } else {
                                    if (drag.y > 0) "DOWN" else "UP"
                                }
                                mpVm.sendDirection(dir)
                            }
                        }
                ) {
                    drawMultiplayerGame(state, cellSizePx, viewOffX, viewOffY, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
                }

                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.Black.copy(0.3f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val dirs = listOf(
                    Triple(Icons.Default.ArrowUpward, "UP", Modifier.align(Alignment.CenterVertically)),
                    Triple(Icons.Default.ArrowDownward, "DOWN", Modifier.align(Alignment.CenterVertically)),
                    @Suppress("DEPRECATION")
                    Triple(Icons.Default.ArrowBack, "LEFT", Modifier.align(Alignment.CenterVertically)),
                    @Suppress("DEPRECATION")
                    Triple(Icons.Default.ArrowForward, "RIGHT", Modifier.align(Alignment.CenterVertically))
                )
                dirs.forEach { (icon, dir, _) ->
                    IconButton(
                        onClick = { mpVm.sendDirection(dir) },
                        modifier = Modifier.size(48.dp).background(Color.White.copy(0.1f), CircleShape)
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    val stateData = mpVm.uiState.gameState
    if (stateData != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            MultiplayerLeaderboardOverlay(stateData)
        }
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, playerName: String, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit, onRename: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("DONE") } },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingRow("TV Mode Adapter") {
                    Switch(checked = settings.isTVMode, onCheckedChange = { onUpdate(settings.copy(isTVMode = it)) })
                }

                Surface(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Player Name", fontSize = 13.sp)
                            Text(
                                if (playerName.isNotBlank()) playerName else "(not set)",
                                fontSize = 11.sp,
                                color = if (playerName.isNotBlank()) MaterialTheme.colorScheme.onSurface.copy(0.6f) else MaterialTheme.colorScheme.error.copy(0.6f)
                            )
                        }
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }

                if (!settings.isTVMode) {
                    Row(Modifier.fillMaxWidth().height(56.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Control Mode", fontSize = 14.sp)
                        Surface(
                            onClick = { onUpdate(settings.copy(controlMode = if (settings.controlMode == ControlMode.SWIPE) ControlMode.BUTTONS else ControlMode.SWIPE)) },
                            color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = if (settings.controlMode == ControlMode.SWIPE) "Swipe Focus" else "Button Control",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                SettingRow("Loop Mode") { Switch(checked = settings.isLoopMode, onCheckedChange = { onUpdate(settings.copy(isLoopMode = it)) }) }
                SettingRow("Vibration") { Switch(checked = settings.enableVibration, onCheckedChange = { onUpdate(settings.copy(enableVibration = it)) }) }
                SettingRow("Item Decay") { Switch(checked = settings.enableItemDecay, onCheckedChange = { onUpdate(settings.copy(enableItemDecay = it)) }) }

                if (!settings.isTVMode) {
                    Spacer(Modifier.height(8.dp))
                    Text("Max Items: ${settings.maxObjects}", style = MaterialTheme.typography.labelMedium)
                    Slider(value = settings.maxObjects.toFloat(), onValueChange = { onUpdate(settings.copy(maxObjects = it.toInt())) }, valueRange = 1f..15f, steps = 13)
                    Text("Cell Size: ${settings.targetCellSize.toInt()}dp", style = MaterialTheme.typography.labelMedium)
                    Slider(value = settings.targetCellSize, onValueChange = { onUpdate(settings.copy(targetCellSize = it)) }, valueRange = 16f..40f)
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                ItemType.entries.forEach { type ->
                    Row(Modifier.fillMaxWidth().height(40.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(18.dp), tint = Color(type.colorHex))
                            Text(type.label, Modifier.padding(start = 12.dp), fontSize = 14.sp)
                        }
                        Checkbox(checked = settings.enabledItems[type] == true, onCheckedChange = {
                            val m = settings.enabledItems.toMutableMap(); m[type] = it; onUpdate(settings.copy(enabledItems = m))
                        })
                    }
                }
            }
        }
    )
}

@Composable
fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp); content()
    }
}

@Composable
fun ResultDialog(state: SnakeState, settings: GameSettings, onRestart: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(onDismissRequest = {},
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("SETTINGS")
                }
                Spacer(Modifier.width(12.dp)); Button(onClick = onRestart) { Icon(Icons.Default.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("REPLAY") }
            }
        },
        title = { Text("Game Over", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Black) },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${state.score}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp)); StatsList(state.itemsCollected)
        }}
    )
}

@Composable
fun PauseStatsDialog(state: SnakeState, onResume: () -> Unit) {
    AlertDialog(onDismissRequest = onResume, confirmButton = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Button(onClick = onResume) { Text("RESUME") } } },
        title = { Text("Paused", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Current Score: ${state.score}", fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); StatsList(state.itemsCollected)
        }}
    )
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    val collected = items.filter { it.value > 0 }.toList()
    if (collected.isEmpty()) return
    if (collected.size >= 2) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            collected.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    rowItems.forEach { (type, count) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(type.icon, null, Modifier.size(16.dp), tint = Color(type.colorHex))
                            Text("${type.label}: $count", Modifier.padding(start = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            collected.forEach { (type, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(type.icon, null, Modifier.size(18.dp), tint = Color(type.colorHex))
                    Text("${type.label}: $count", Modifier.padding(start = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConfettiEffect() {
    val particles = remember { List(60) { ConfettiParticle() } }
    val infiniteTransition = rememberInfiniteTransition("confetti")
    val progress by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), "")
    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val y = (p.startY + (progress * 1800f * p.speed)) % size.height
            val x = p.startX + (progress * 300f * p.drift)
            drawRect(color = p.color, topLeft = Offset(x, y), size = Size(12f, 24f), alpha = 1f - (y / size.height).coerceIn(0f, 1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameTopBar(state: SnakeState, settings: GameSettings, onTogglePause: () -> Unit, onOpenSettings: () -> Unit, onResetHS: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().height(70.dp)) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
            ScoreChip(Icons.Default.EmojiEvents, "HI", state.highScore, MaterialTheme.colorScheme.outline, onLongClick = onResetHS)
            ScoreChip(Icons.Default.MilitaryTech, "SC", state.score, MaterialTheme.colorScheme.primary)
        }
        Text("SNAKE EVO", fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))

        if (!settings.isTVMode) {
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                if (state.isStarted && !state.isGameOver) {
                    IconButton(onClick = onTogglePause) { Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null) }
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, null) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScoreChip(icon: ImageVector, label: String, value: Int, color: Color, onLongClick: (() -> Unit)? = null) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 1.dp).combinedClickable(onClick = {}, onLongClick = onLongClick)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(10.dp), tint = color); Spacer(Modifier.width(4.dp))
            Text("$label: $value", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

class ConfettiParticle {
    val startX = Random.nextFloat() * 2000f; val startY = -Random.nextFloat() * 1000f
    val speed = Random.nextFloat() * 0.7f + 0.3f; val drift = Random.nextFloat() * 2f - 1f
    val color = Color(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), 1f)
}
