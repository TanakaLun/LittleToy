package io.tl.snake

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tl.snake.logic.*
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
                val state = vm.state
                val settings = vm.settings
                var showSettings by remember { mutableStateOf(false) }
                val vibrator = LocalContext.current.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(state.lastEvent) {
                    if (settings.enableVibration && state.lastEvent != null) {
                        val ms = when (state.lastEvent) {
                            GameEvent.EAT_GOOD -> 30L; GameEvent.EAT_BAD -> 80L
                            GameEvent.SHIELD_BREAK -> 150L; GameEvent.HIT_WALL -> 250L; else -> 0L
                        }
                        if (ms > 0) vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }

                // TV 模式下，每次进入或恢复时请求焦点
                LaunchedEffect(settings.isTVMode, state.isPaused, state.isStarted, showSettings) {
                    if (settings.isTVMode && !state.isPaused && state.isStarted && !showSettings) {
                        focusRequester.requestFocus()
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = { GameTopBar(state, settings, { vm.togglePause() }, { vm.setPaused(true); showSettings = true }, { vm.resetHighScore() }) }
                    ) { p ->
                        Box(Modifier.padding(p).fillMaxSize()) {
                            // 游戏核心区域：TV 模式下通过 onKeyEvent 拦截遥控器信号
                            GameContent(state, settings, vm, focusRequester)
                            
                            if (showSettings) {
                                SettingsDialog(settings, onDismiss = { showSettings = false; vm.startResumeCountdown() }, onUpdate = { vm.updateSettings(it) })
                            }
                            if (state.isGameOver) {
                                ResultDialog(state, settings, onRestart = { vm.restartGame() }, onOpenSettings = { showSettings = true })
                            } else if (state.isPaused && vm.countdown == 0 && !showSettings) {
                                PauseStatsDialog(state) { vm.togglePause() }
                            }
                        }
                    }

                    if (vm.countdown > 0) {
                        CountdownOverlay(vm.countdown)
                    }
                }
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
fun GameContent(state: SnakeState, settings: GameSettings, vm: GameViewModel, focusRequester: FocusRequester) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                // TV 模式按键监听
                .then(if (settings.isTVMode) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
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
            GameCanvasArea(state, settings, vm) 
        }
        
        // 只有非 TV 模式且选择了按钮控制才显示按钮
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
        DirIconButton(Icons.Default.ArrowBack, Direction.LEFT)
        DirIconButton(Icons.Default.ArrowUpward, Direction.UP)
        DirIconButton(Icons.Default.ArrowDownward, Direction.DOWN)
        DirIconButton(Icons.Default.ArrowForward, Direction.RIGHT)
    }
}

@Composable
fun GameCanvasArea(state: SnakeState, settings: GameSettings, vm: GameViewModel) {
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
            Box(Modifier.size((cellSizePx * gridW / density).dp, (cellSizePx * gridH / density).dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant.copy(0.3f)).border(1.dp, colorScheme.outlineVariant.copy(0.3f), RoundedCornerShape(12.dp))) {
                Canvas(Modifier.fillMaxSize().then(
                    if (!settings.isTVMode && settings.controlMode == ControlMode.SWIPE) {
                        Modifier.pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); vm.handleSwipe(drag.x, drag.y) } }
                    } else Modifier
                )) {
                    if (settings.showGrid) {
                        for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, size.height))
                        for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(size.width, i * cellSizePx))
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
                if (!state.isStarted) {
                    Button(onClick = { vm.startGame() }, Modifier.align(Alignment.Center).then(if (settings.isTVMode) Modifier.focusable() else Modifier)) { 
                        Text("START") 
                    }
                }
            }
        }
        if (state.isGameOver && state.score >= state.highScore && state.score > 0) ConfettiEffect()
    }
}

@Composable
fun SettingsDialog(settings: GameSettings, onDismiss: () -> Unit, onUpdate: (GameSettings) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = onDismiss) { Text("DONE") } },
        title = { Text("Configuration", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // TV 模式切换
                SettingRow("TV Mode Adapter") { 
                    Switch(checked = settings.isTVMode, onCheckedChange = { onUpdate(settings.copy(isTVMode = it)) }) 
                }
                
                // 只有非 TV 模式显示控制方式切换
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
                
                // TV 模式下移除 Slider，代之以简单的步进器或固定配置以减少遥控操作难度
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
        
        // TV 模式下隐藏顶栏按钮，仅保留信息展示
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
