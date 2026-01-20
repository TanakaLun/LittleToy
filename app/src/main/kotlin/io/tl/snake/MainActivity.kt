package io.tl.snake

import android.content.Context
import android.os.*
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.tl.snake.logic.*
import io.tl.snake.ui.*
import io.tl.snake.ui.theme.MyTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    // 状态外置，方便按键拦截器访问
    private var gameState by mutableStateOf(SnakeState())
    private var gameSettings by mutableStateOf(GameSettings())
    private var showSettings by mutableStateOf(false)
    private var nextDir by mutableStateOf(Direction.UP)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("snake_prefs", Context.MODE_PRIVATE)
        
        enableEdgeToEdge()
        setContent {
            MyTheme {
                // 核心循环
                LaunchedEffect(gameState.isStarted, gameState.isPaused, gameState.isGameOver) {
                    while (gameState.isStarted && !gameState.isPaused && !gameState.isGameOver) {
                        val speed = (GameConfig.BASE_SPEED - (gameState.score / 100 * 5) + gameState.speedModifier).coerceAtLeast(GameConfig.MIN_SPEED)
                        delay(speed)
                        gameState = gameTick(gameState, gameSettings, nextDir)
                    }
                }

                Scaffold { p ->
                    Box(Modifier.padding(p)) {
                        MainGameScreen(
                            state = gameState,
                            settings = gameSettings,
                            onDirectionChange = { dx, dy -> 
                                nextDir = InputHandler.handleSwipe(gameState.direction, dx, dy) 
                            },
                            onStart = { gameState = gameState.copy(isStarted = true) }
                        )

                        if (gameState.isGameOver) {
                            ResultDialog(
                                state = gameState,
                                onRestart = { 
                                    gameState = SnakeState(highScore = gameState.highScore, isStarted = true)
                                    nextDir = Direction.UP
                                },
                                onOpenSettings = { showSettings = true }
                            )
                        }

                        if (showSettings) {
                            SettingsDialog(gameSettings, { showSettings = false }, { gameSettings = it })
                        }
                    }
                }
            }
        }
    }

    /**
     * TV 遥控器按键拦截
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 1. 处理方向键
        val dir = InputHandler.handleKeyEvent(gameState.direction, keyCode)
        if (dir != null) {
            nextDir = dir
            return true
        }

        // 2. 处理返回键：如果是游戏中，则暂停并呼出菜单；如果是暂停中，则可能退出
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (gameState.isStarted && !gameState.isGameOver) {
                if (!gameState.isPaused) {
                    gameState = gameState.copy(isPaused = true)
                    showSettings = true // TV 规范：返回键通常用于呼出菜单或暂停
                    return true 
                }
            }
        }

        // 3. 处理菜单键 (部分遥控器有此键)
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (gameState.isStarted) {
                gameState = gameState.copy(isPaused = true)
                showSettings = true
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }
}
