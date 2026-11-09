package app.rork.p7buzvrzcw3frddi8y50ou

import android.graphics.Color
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.ui.navigation.AppNavigation
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.AppTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                AppNavigation(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setPaused(false)
    }

    override fun onStop() {
        vm.setPaused(true)
        super.onStop()
    }

    // Optional gamepad support: left stick walks, face buttons act.
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val engine = (vm.phase.value as? Phase.Playing)?.engine
        if (engine != null && event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val x = event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_Y)
            engine.input.moveX = if (abs(x) > 0.15f) x else 0f
            engine.input.moveY = if (abs(y) > 0.15f) -y else 0f
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val command = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> Command.Jump
            KeyEvent.KEYCODE_BUTTON_X -> Command.Attack
            KeyEvent.KEYCODE_BUTTON_Y -> Command.Interact
            else -> null
        }
        if (command != null && vm.phase.value is Phase.Playing) {
            if (event.repeatCount == 0) vm.send(command)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
