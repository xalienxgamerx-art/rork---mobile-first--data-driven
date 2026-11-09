package app.rork.p7buzvrzcw3frddi8y50ou.render

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine

/**
 * Hosts the GL world. Resolution is decoupled from the display: the surface is rendered at a
 * fraction of its size and scaled by the compositor, and that fraction adapts to frame rate.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context, engine: GameEngine, level: Int, private var adaptive: Boolean) :
    GLSurfaceView(context) {

    val renderer = WorldRenderer(engine, level)
    private var scale = GraphicsPreset.of(level).scale
    private var maxScale = scale
    private var slowSeconds = 0
    private var fastSeconds = 0

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        renderer.onFps = { fps -> onFps(fps) }
    }

    fun configure(engine: GameEngine, level: Int, adaptive: Boolean) {
        renderer.engine = engine
        this.adaptive = adaptive
        if (renderer.level != level) {
            renderer.level = level
            maxScale = GraphicsPreset.of(level).scale
            scale = maxScale
            applyScale()
        }
    }

    private fun onFps(fps: Int) {
        if (!adaptive) return
        if (fps < 38) { slowSeconds++; fastSeconds = 0 } else if (fps > 56) { fastSeconds++; slowSeconds = 0 } else {
            slowSeconds = 0; fastSeconds = 0
        }
        if (slowSeconds >= 2 && scale > 0.45f) {
            slowSeconds = 0
            scale = (scale - 0.1f).coerceAtLeast(0.45f)
            post { applyScale() }
        } else if (fastSeconds >= 6 && scale < maxScale) {
            fastSeconds = 0
            scale = (scale + 0.1f).coerceAtMost(maxScale)
            post { applyScale() }
        }
    }

    private fun applyScale() {
        if (width <= 0 || height <= 0) return
        holder.setFixedSize((width * scale).toInt().coerceAtLeast(160), (height * scale).toInt().coerceAtLeast(240))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { applyScale() }
    }
}
