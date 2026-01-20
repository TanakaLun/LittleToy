package io.tl.snake.logic

import android.view.KeyEvent
import kotlin.math.abs

object InputHandler {
    /**
     * 手势/滑动控制
     */
    fun handleSwipe(currentDir: Direction, dx: Float, dy: Float): Direction {
        return when {
            abs(dx) > abs(dy) -> {
                if (dx > 0 && currentDir != Direction.LEFT) Direction.RIGHT 
                else if (dx < 0 && currentDir != Direction.RIGHT) Direction.LEFT 
                else currentDir
            }
            else -> {
                if (dy > 0 && currentDir != Direction.UP) Direction.DOWN 
                else if (dy < 0 && currentDir != Direction.DOWN) Direction.UP 
                else currentDir
            }
        }
    }

    /**
     * 遥控器/键盘控制
     */
    fun handleKeyEvent(currentDir: Direction, keyCode: Int): Direction? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> if (currentDir != Direction.DOWN) Direction.UP else null
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> if (currentDir != Direction.UP) Direction.DOWN else null
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> if (currentDir != Direction.RIGHT) Direction.LEFT else null
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> if (currentDir != Direction.LEFT) Direction.RIGHT else null
            else -> null
        }
    }
}
