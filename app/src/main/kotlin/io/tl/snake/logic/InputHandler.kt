package io.tl.snake.logic

import android.view.KeyEvent
import kotlin.math.abs

object InputHandler {
    // 处理手势
    fun handleSwipe(current: Direction, dx: Float, dy: Float): Direction {
        return when {
            abs(dx) > abs(dy) -> if (dx > 0 && current != Direction.LEFT) Direction.RIGHT else if (dx < 0 && current != Direction.RIGHT) Direction.LEFT else current
            else -> if (dy > 0 && current != Direction.UP) Direction.DOWN else if (dy < 0 && current != Direction.DOWN) Direction.UP else current
        }
    }

    // 处理 TV 按键
    fun handleKeyEvent(current: Direction, keyCode: Int): Direction? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (current != Direction.DOWN) Direction.UP else null
            KeyEvent.KEYCODE_DPAD_DOWN -> if (current != Direction.UP) Direction.DOWN else null
            KeyEvent.KEYCODE_DPAD_LEFT -> if (current != Direction.RIGHT) Direction.LEFT else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (current != Direction.LEFT) Direction.RIGHT else null
            else -> null
        }
    }
}
