package io.tl.snake.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tl.snake.logic.*

@Composable
fun MainGameScreen(
    state: SnakeState,
    settings: GameSettings,
    onDirectionChange: (Float, Float) -> Unit,
    onStart: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // 自适应布局：根据屏幕宽高比决定排版
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        
        if (isLandscape) {
            // 横屏布局 (平板/TV)
            Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                // 左侧显示即时信息
                SideStatsPanel(state, Modifier.weight(0.3f))
                
                // 右侧或中间显示游戏区
                Box(Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
                    GameCanvasArea(state, settings, onDirectionChange, onStart)
                }
            }
        } else {
            // 竖屏布局 (手机)
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                GameCanvasArea(state, settings, onDirectionChange, onStart)
            }
        }
    }
}

@Composable
fun SideStatsPanel(state: SnakeState, modifier: Modifier) {
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
        Text("SCORE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Text("${state.score}", fontSize = 48.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        StatsList(state.itemsCollected)
    }
}

@Composable
fun GameCanvasArea(state: SnakeState, settings: GameSettings, onDirectionChange: (Float, Float) -> Unit, onStart: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalContext.current.resources.displayMetrics.density
        val cellSizePx = settings.targetCellSize * density
        
        val gridW = (constraints.maxWidth / cellSizePx).toInt().coerceAtLeast(10)
        val gridH = (constraints.maxHeight / cellSizePx).toInt().coerceAtLeast(10)
        
        Box(Modifier.size((gridW * settings.targetCellSize).dp, (gridH * settings.targetCellSize).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceVariant.copy(0.3f))
            .border(2.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures { change, drag -> change.consume(); onDirectionChange(drag.x, drag.y) }
            }) {
                // 绘制网格
                if (settings.showGrid) {
                    for (i in 0..gridW) drawLine(colorScheme.onSurface.copy(0.05f), Offset(i * cellSizePx, 0f), Offset(i * cellSizePx, size.height))
                    for (i in 0..gridH) drawLine(colorScheme.onSurface.copy(0.05f), Offset(0f, i * cellSizePx), Offset(size.width, i * cellSizePx))
                }
                // 绘制物体
                state.objects.forEach { obj ->
                    drawCircle(obj.type.color, cellSizePx * 0.35f, Offset(obj.pos.first * cellSizePx + cellSizePx/2, obj.pos.second * cellSizePx + cellSizePx/2))
                }
                // 绘制蛇
                state.snake.forEachIndexed { i, p ->
                    val color = if (i == 0) colorScheme.primary else colorScheme.primary.copy(0.6f)
                    drawRoundRect(color, Offset(p.first * cellSizePx + 2, p.second * cellSizePx + 2), Size(cellSizePx - 4, cellSizePx - 4), CornerRadius(4.dp.toPx()))
                }
            }
            if (!state.isStarted) {
                Button(onClick = onStart, Modifier.align(Alignment.Center)) { Text("START GAME") }
            }
        }
    }
}

@Composable
fun ResultDialog(state: SnakeState, onRestart: () -> Unit, onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Button(onClick = onOpenSettings, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("设置")
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = onRestart) {
                    Icon(Icons.Default.Replay, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("重赛")
                }
            }
        },
        title = { Text("Game Over", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                StatsList(state.itemsCollected)
            }
        }
    )
}

@Composable
fun StatsList(items: Map<ItemType, Int>) {
    val collected = items.filter { it.value > 0 }.toList()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        collected.chunked(2).forEach { row ->
            Row(Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (type, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(type.icon, null, Modifier.size(14.dp), tint = type.color)
                        Text("${type.label}: $count", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}
