package app.rork.p7buzvrzcw3frddi8y50ou.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.RenderFrame
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Terrain
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Draws the vale. The renderer only ever reads a [RenderFrame] snapshot: the simulation is
 * stepped once per frame from here, then the lock is released before any GL work.
 */
class WorldRenderer(
    @Volatile var engine: GameEngine,
    @Volatile var level: Int,
) : GLSurfaceView.Renderer {

    /** Called (on the GL thread) roughly once a second with the measured frame rate. */
    var onFps: ((Int) -> Unit)? = null

    private val frame = RenderFrame()
    private var program = 0
    private var skyProgram = 0
    private var worldVbo = 0
    private var worldCount = 0
    private var cubeVbo = 0
    private var skyVbo = 0
    private var waterVbo = 0
    private var builtFor: Any? = null
    private var builtLevel = -1

    private var aPos = 0
    private var aNormal = 0
    private var aColor = 0
    private var uMvp = 0
    private var uModel = 0
    private var uTint = 0
    private var uSunDir = 0
    private var uSunCol = 0
    private var uAmbient = 0
    private var uCam = 0
    private var uFogCol = 0
    private var uFogDensity = 0
    private var uGlow = 0
    private var uAlpha = 0

    private var sPos = 0
    private var sForward = 0
    private var sRight = 0
    private var sUp = 0
    private var sZenith = 0
    private var sHorizon = 0
    private var sSunDir = 0
    private var sSunCol = 0
    private var sGround = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val base = FloatArray(16)
    private val mvp = FloatArray(16)
    private val camWorld = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private var aspect = 0.5f
    private var lastNanos = 0L
    private var fpsFrames = 0
    private var fpsTime = 0f
    private val fov = 72f

    private val zenith = FloatArray(3)
    private val horizon = FloatArray(3)
    private val sunCol = FloatArray(3)
    private val ambient = FloatArray(3)
    private val sunDir = FloatArray(3)
    private val fogCol = FloatArray(3)
    private var night = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = link(VERT, FRAG)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        aColor = GLES20.glGetAttribLocation(program, "aColor")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uTint = GLES20.glGetUniformLocation(program, "uTint")
        uSunDir = GLES20.glGetUniformLocation(program, "uSunDir")
        uSunCol = GLES20.glGetUniformLocation(program, "uSunCol")
        uAmbient = GLES20.glGetUniformLocation(program, "uAmbient")
        uCam = GLES20.glGetUniformLocation(program, "uCam")
        uFogCol = GLES20.glGetUniformLocation(program, "uFogCol")
        uFogDensity = GLES20.glGetUniformLocation(program, "uFogDensity")
        uGlow = GLES20.glGetUniformLocation(program, "uGlow")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")

        skyProgram = link(SKY_VERT, SKY_FRAG)
        sPos = GLES20.glGetAttribLocation(skyProgram, "aPos")
        sForward = GLES20.glGetUniformLocation(skyProgram, "uForward")
        sRight = GLES20.glGetUniformLocation(skyProgram, "uRight")
        sUp = GLES20.glGetUniformLocation(skyProgram, "uUp")
        sZenith = GLES20.glGetUniformLocation(skyProgram, "uZenith")
        sHorizon = GLES20.glGetUniformLocation(skyProgram, "uHorizon")
        sSunDir = GLES20.glGetUniformLocation(skyProgram, "uSunDir")
        sSunCol = GLES20.glGetUniformLocation(skyProgram, "uSunCol")
        sGround = GLES20.glGetUniformLocation(skyProgram, "uGround")

        val ids = IntArray(4)
        GLES20.glGenBuffers(4, ids, 0)
        worldVbo = ids[0]; cubeVbo = ids[1]; skyVbo = ids[2]; waterVbo = ids[3]
        val cube = WorldMesh.cube()
        upload(cubeVbo, cube.data, cube.size)
        upload(skyVbo, floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f), 12)
        val w = MeshBuilder(256)
        w.place(0f, 0f, 0f, 0f)
        w.upward()
        val e = Terrain.HALF
        w.quad(-e, Terrain.WATER, -e, e, Terrain.WATER, -e, e, Terrain.WATER, e, -e, Terrain.WATER, e, 0.22f, 0.40f, 0.48f)
        upload(waterVbo, w.data, w.size)
        builtFor = null
        builtLevel = -1
        lastNanos = 0L
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / max(1, height)
        Matrix.perspectiveM(proj, 0, fov, aspect, 0.12f, 700f)
    }

    private fun upload(vbo: Int, data: FloatArray, size: Int) {
        val buf = ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data, 0, size).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, size * 4, buf, GLES20.GL_STATIC_DRAW)
    }

    private fun ensureWorld(e: GameEngine) {
        val lvl = level
        if (builtFor === e.content && builtLevel == lvl) return
        val mesh = WorldMesh.get(e.content, lvl)
        upload(worldVbo, mesh.data, mesh.size)
        worldCount = mesh.vertexCount
        builtFor = e.content
        builtLevel = lvl
    }

    override fun onDrawFrame(gl: GL10?) {
        val e = engine
        ensureWorld(e)
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNanos = now
        fpsFrames++
        fpsTime += dt
        if (fpsTime >= 1f) {
            val fps = (fpsFrames / fpsTime).toInt()
            e.fps = fps
            onFps?.invoke(fps)
            fpsFrames = 0
            fpsTime = 0f
        }
        e.update(dt)
        e.fillFrame(frame)
        val f = frame

        atmosphere(f.hour, f.mist)
        val cp = cos(f.pitch)
        val fx = sin(f.yaw) * cp
        val fy = sin(f.pitch)
        val fz = -cos(f.yaw) * cp
        Matrix.setLookAtM(view, 0, f.camX, f.camY, f.camZ, f.camX + fx, f.camY + fy, f.camZ + fz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES20.glClearColor(fogCol[0], fogCol[1], fogCol[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Sky.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(skyProgram)
        val th = tan(Math.toRadians(fov / 2.0)).toFloat()
        val rx = cos(f.yaw); val rz = sin(f.yaw)
        // up = right x forward
        val ux = -rz * fy
        val uy = rz * fx - rx * fz
        val uz = rx * fy
        GLES20.glUniform3f(sForward, fx, fy, fz)
        GLES20.glUniform3f(sRight, rx * th * aspect, 0f, rz * th * aspect)
        GLES20.glUniform3f(sUp, ux * th, uy * th, uz * th)
        GLES20.glUniform3fv(sZenith, 1, zenith, 0)
        GLES20.glUniform3fv(sHorizon, 1, horizon, 0)
        GLES20.glUniform3fv(sSunDir, 1, sunDir, 0)
        GLES20.glUniform3f(sSunCol, sunCol[0], sunCol[1], sunCol[2])
        GLES20.glUniform3fv(sGround, 1, fogCol, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo)
        GLES20.glEnableVertexAttribArray(sPos)
        GLES20.glVertexAttribPointer(sPos, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(sPos)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Land.
        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(uSunDir, 1, sunDir, 0)
        GLES20.glUniform3fv(uSunCol, 1, sunCol, 0)
        GLES20.glUniform3fv(uAmbient, 1, ambient, 0)
        GLES20.glUniform3f(uCam, f.camX, f.camY, f.camZ)
        GLES20.glUniform3fv(uFogCol, 1, fogCol, 0)
        val fogDistance = GraphicsPreset.of(level).fog / (1f + f.mist * 1.3f)
        GLES20.glUniform1f(uFogDensity, 1.5f / fogDistance)
        GLES20.glUniform1f(uGlow, 0.55f + night * 0.9f)
        GLES20.glUniform1f(uAlpha, 1f)
        GLES20.glUniform3f(uTint, 1f, 1f, 1f)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, identity, 0)
        bind(worldVbo)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, worldCount)

        // Everything that moves is assembled from one cube.
        bind(cubeVbo)
        for (i in 0 until f.nodeCount) node(f, i)
        for (i in 0 until f.actorCount) {
            val dx = f.ax[i] - f.camX
            val dz = f.az[i] - f.camZ
            val along = dx * fx + dz * fz
            val d2 = dx * dx + dz * dz
            if (along < -3f && d2 > 16f) continue
            if (f.akind[i] == 0) humanoid(f, i, d2 > 80f * 80f) else quadruped(f, i)
        }

        // The Silverrun.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glUniform1f(uAlpha, 0.72f)
        GLES20.glUniform3f(uTint, 0.7f + horizon[0] * 0.5f, 0.8f + horizon[1] * 0.3f, 0.9f + horizon[2] * 0.2f)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, identity, 0)
        bind(waterVbo)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUniform1f(uAlpha, 1f)

        if (f.firstPersonWeapon) weapon(f)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
        GLES20.glDisableVertexAttribArray(aColor)
    }

    private fun bind(vbo: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 36, 0)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 36, 12)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 36, 24)
    }

    /** Draws one cube: pivot at (px,py,pz), rotated about x, then offset along y and scaled. */
    private fun part(
        px: Float, py: Float, pz: Float, angle: Float, oy: Float,
        sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
    ) {
        System.arraycopy(base, 0, model, 0, 16)
        Matrix.translateM(model, 0, px, py, pz)
        if (angle != 0f) Matrix.rotateM(model, 0, angle, 1f, 0f, 0f)
        if (oy != 0f) Matrix.translateM(model, 0, 0f, oy, 0f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform3f(uTint, r, g, b)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }

    private fun place(f: RenderFrame, i: Int) {
        Matrix.setIdentityM(base, 0)
        Matrix.translateM(base, 0, f.ax[i], f.ay[i], f.az[i])
        Matrix.rotateM(base, 0, -Math.toDegrees(f.ayaw[i].toDouble()).toFloat(), 0f, 1f, 0f)
        val s = f.ascale[i]
        Matrix.scaleM(base, 0, s, s, s)
    }

    private fun humanoid(f: RenderFrame, i: Int, far: Boolean) {
        place(f, i)
        val o = i * 9
        val c = f.acol
        val sr = c[o]; val sg = c[o + 1]; val sb = c[o + 2]
        val cr = c[o + 3]; val cg = c[o + 4]; val cb = c[o + 5]
        val hr = c[o + 6]; val hg = c[o + 7]; val hb = c[o + 8]
        val swing = sin(f.aphase[i]) * 34f
        if (far) {
            part(0f, 0.4f, 0f, 0f, 0f, 0.34f, 0.8f, 0.2f, cr * 0.6f, cg * 0.6f, cb * 0.6f)
            part(0f, 1.1f, 0f, 0f, 0f, 0.44f, 0.62f, 0.26f, cr, cg, cb)
            part(0f, 1.56f, 0f, 0f, 0f, 0.26f, 0.28f, 0.26f, sr, sg, sb)
            return
        }
        val flags = f.aflags[i]
        part(-0.11f, 0.8f, 0f, swing, -0.4f, 0.17f, 0.8f, 0.19f, cr * 0.58f, cg * 0.58f, cb * 0.58f)
        part(0.11f, 0.8f, 0f, -swing, -0.4f, 0.17f, 0.8f, 0.19f, cr * 0.58f, cg * 0.58f, cb * 0.58f)
        part(0f, 1.1f, 0f, 0f, 0f, 0.44f, 0.62f, 0.26f, cr, cg, cb)
        part(0f, 0.86f, 0f, 0f, 0f, 0.46f, 0.07f, 0.28f, 0.25f, 0.17f, 0.11f)
        part(-0.29f, 1.38f, 0f, -swing * 0.8f, -0.3f, 0.13f, 0.6f, 0.15f, cr * 0.88f, cg * 0.88f, cb * 0.88f)
        part(0.29f, 1.38f, 0f, swing * 0.8f, -0.3f, 0.13f, 0.6f, 0.15f, cr * 0.88f, cg * 0.88f, cb * 0.88f)
        part(0f, 1.56f, 0f, 0f, 0f, 0.26f, 0.28f, 0.26f, sr, sg, sb)
        part(0f, 1.71f, 0.02f, 0f, 0f, 0.29f, 0.09f, 0.3f, hr, hg, hb)
        if (flags and 4 != 0) part(0f, 1.5f, 0.15f, 0f, 0f, 0.28f, 0.36f, 0.07f, hr, hg, hb)
        if (flags and 2 != 0) part(0f, 1.45f, -0.14f, 0f, 0f, 0.22f, 0.13f, 0.06f, hr, hg, hb)
        if (flags and 1 != 0) {
            part(0f, 1.76f, 0f, 0f, 0f, 0.5f, 0.04f, 0.5f, cr * 0.5f, cg * 0.5f, cb * 0.5f)
            part(0f, 1.83f, 0f, 0f, 0f, 0.27f, 0.12f, 0.27f, cr * 0.5f, cg * 0.5f, cb * 0.5f)
        }
        if (flags and 8 != 0) part(0.29f, 1.38f, 0f, swing * 0.8f - 70f, -0.95f, 0.04f, 0.8f, 0.02f, 0.75f, 0.76f, 0.8f)
    }

    private fun quadruped(f: RenderFrame, i: Int) {
        place(f, i)
        val o = i * 9
        val r = f.acol[o]; val g = f.acol[o + 1]; val b = f.acol[o + 2]
        val swing = sin(f.aphase[i]) * 30f
        part(0f, 0.62f, 0f, 0f, 0f, 0.4f, 0.4f, 0.95f, r, g, b)
        part(0f, 0.78f, -0.6f, 0f, 0f, 0.3f, 0.3f, 0.34f, r * 1.08f, g * 1.08f, b * 1.08f)
        part(0f, 0.72f, -0.82f, 0f, 0f, 0.16f, 0.14f, 0.16f, r * 0.7f, g * 0.7f, b * 0.7f)
        part(-0.1f, 0.98f, -0.55f, 0f, 0f, 0.07f, 0.14f, 0.07f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(0.1f, 0.98f, -0.55f, 0f, 0f, 0.07f, 0.14f, 0.07f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(-0.14f, 0.45f, -0.35f, swing, -0.22f, 0.1f, 0.45f, 0.1f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(0.14f, 0.45f, -0.35f, -swing, -0.22f, 0.1f, 0.45f, 0.1f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(-0.14f, 0.45f, 0.35f, -swing, -0.22f, 0.1f, 0.45f, 0.1f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(0.14f, 0.45f, 0.35f, swing, -0.22f, 0.1f, 0.45f, 0.1f, r * 0.8f, g * 0.8f, b * 0.8f)
        part(0f, 0.72f, 0.5f, -40f, 0.18f, 0.08f, 0.36f, 0.08f, r * 0.9f, g * 0.9f, b * 0.9f)
    }

    private fun node(f: RenderFrame, i: Int) {
        Matrix.setIdentityM(base, 0)
        Matrix.translateM(base, 0, f.nx[i], f.ny[i], f.nz[i])
        Matrix.rotateM(base, 0, i * 47f, 0f, 1f, 0f)
        if (f.nkind[i] == 1) {
            part(0f, 0.35f, 0f, 0f, 0f, 1.1f, 0.8f, 0.9f, 0.42f, 0.40f, 0.40f)
            part(0.45f, 0.3f, 0.3f, 0f, 0f, 0.6f, 0.6f, 0.6f, 0.36f, 0.34f, 0.35f)
            part(-0.2f, 0.62f, 0.2f, 0f, 0f, 0.3f, 0.3f, 0.3f, 0.72f, 0.48f, 0.32f)
            part(0.3f, 0.5f, -0.3f, 0f, 0f, 0.22f, 0.22f, 0.22f, 0.76f, 0.52f, 0.34f)
        } else {
            part(0f, 0.22f, 0f, 0f, 0f, 0.5f, 0.44f, 0.5f, 0.30f, 0.46f, 0.22f)
            part(0.1f, 0.52f, 0.05f, 0f, 0f, 0.16f, 0.18f, 0.16f, 0.82f, 0.74f, 0.9f)
            part(-0.14f, 0.48f, -0.1f, 0f, 0f, 0.14f, 0.16f, 0.14f, 0.9f, 0.84f, 0.5f)
        }
    }

    /** First-person blade, drawn over the world in camera space. */
    private fun weapon(f: RenderFrame) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.invertM(camWorld, 0, view, 0)
        System.arraycopy(camWorld, 0, base, 0, 16)
        val t = 1f - f.swing
        val arc = if (f.swing > 0f) sin(t * 3.1416f) else 0f
        if (f.blocking) {
            Matrix.translateM(base, 0, 0.02f, -0.3f, -0.55f)
            Matrix.rotateM(base, 0, 72f, 0f, 0f, 1f)
            Matrix.rotateM(base, 0, -12f, 1f, 0f, 0f)
        } else {
            Matrix.translateM(base, 0, 0.3f - arc * 0.34f, -0.36f + arc * 0.08f, -0.6f - arc * 0.12f)
            Matrix.rotateM(base, 0, 9f + arc * 62f, 0f, 0f, 1f)
            Matrix.rotateM(base, 0, -18f - arc * 58f, 1f, 0f, 0f)
        }
        val len = f.bladeLength.coerceIn(0.25f, 1.25f) * 0.72f
        val c = f.bladeColor
        part(0f, 0.1f + len / 2f, 0f, 0f, 0f, 0.04f, len, 0.012f, c[0], c[1], c[2])
        part(0f, 0.09f, 0f, 0f, 0f, 0.16f, 0.025f, 0.035f, 0.5f, 0.42f, 0.25f)
        part(0f, 0f, 0f, 0f, 0f, 0.032f, 0.17f, 0.032f, 0.3f, 0.2f, 0.13f)
        part(0f, -0.1f, 0f, 0f, 0f, 0.05f, 0.04f, 0.05f, 0.5f, 0.42f, 0.25f)
    }

    private fun atmosphere(hour: Float, mist: Float) {
        var i = 0
        while (i < KEYS.size - 1 && hour >= KEYS[i + 1][0]) i++
        val a = KEYS[i]
        val b = KEYS[min(i + 1, KEYS.size - 1)]
        val t = if (b[0] > a[0]) ((hour - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f) else 0f
        for (k in 0 until 3) {
            zenith[k] = a[1 + k] + (b[1 + k] - a[1 + k]) * t
            horizon[k] = a[4 + k] + (b[4 + k] - a[4 + k]) * t
            sunCol[k] = a[7 + k] + (b[7 + k] - a[7 + k]) * t
            ambient[k] = a[10 + k] + (b[10 + k] - a[10 + k]) * t
        }
        val angle = (hour - 6f) / 12f * 3.1415927f
        var sx = cos(angle) * 0.85f
        var sy = sin(angle)
        val sz = 0.4f
        night = (1f - Terrain.smooth(-0.1f, 0.35f, sy))
        if (sy < 0f) { sx = -sx; sy = -sy }
        sy = max(sy, 0.12f)
        val len = sqrt(sx * sx + sy * sy + sz * sz)
        sunDir[0] = sx / len; sunDir[1] = sy / len; sunDir[2] = sz / len
        val grey = (horizon[0] + horizon[1] + horizon[2]) / 3f
        val m = mist * 0.75f
        for (k in 0 until 3) {
            horizon[k] += (grey * 0.95f - horizon[k]) * m
            zenith[k] += (grey * 0.8f - zenith[k]) * m
            sunCol[k] *= 1f - mist * 0.45f
            fogCol[k] = horizon[k] * 0.86f + zenith[k] * 0.14f
        }
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    private companion object {
        // hour, zenith rgb, horizon rgb, light rgb, ambient rgb
        val KEYS = arrayOf(
            floatArrayOf(0f, 0.03f, 0.04f, 0.10f, 0.08f, 0.10f, 0.17f, 0.16f, 0.19f, 0.30f, 0.13f, 0.15f, 0.22f),
            floatArrayOf(5f, 0.03f, 0.04f, 0.10f, 0.08f, 0.10f, 0.17f, 0.16f, 0.19f, 0.30f, 0.13f, 0.15f, 0.22f),
            floatArrayOf(6.6f, 0.30f, 0.36f, 0.56f, 0.97f, 0.66f, 0.46f, 0.95f, 0.60f, 0.38f, 0.36f, 0.34f, 0.40f),
            floatArrayOf(9f, 0.27f, 0.50f, 0.84f, 0.74f, 0.84f, 0.93f, 1.00f, 0.95f, 0.86f, 0.46f, 0.49f, 0.54f),
            floatArrayOf(14.5f, 0.27f, 0.50f, 0.84f, 0.74f, 0.84f, 0.93f, 1.00f, 0.95f, 0.86f, 0.46f, 0.49f, 0.54f),
            floatArrayOf(16.5f, 0.38f, 0.44f, 0.70f, 0.98f, 0.74f, 0.50f, 1.00f, 0.72f, 0.44f, 0.46f, 0.40f, 0.40f),
            floatArrayOf(18.6f, 0.30f, 0.28f, 0.50f, 0.96f, 0.56f, 0.36f, 0.90f, 0.50f, 0.30f, 0.38f, 0.32f, 0.36f),
            floatArrayOf(20f, 0.10f, 0.11f, 0.24f, 0.36f, 0.24f, 0.28f, 0.30f, 0.24f, 0.28f, 0.20f, 0.19f, 0.26f),
            floatArrayOf(21.2f, 0.03f, 0.04f, 0.10f, 0.08f, 0.10f, 0.17f, 0.16f, 0.19f, 0.30f, 0.13f, 0.15f, 0.22f),
            floatArrayOf(24f, 0.03f, 0.04f, 0.10f, 0.08f, 0.10f, 0.17f, 0.16f, 0.19f, 0.30f, 0.13f, 0.15f, 0.22f),
        )

        const val VERT = """
attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec3 aColor;
uniform mat4 uMVP;
uniform mat4 uModel;
uniform vec3 uTint;
uniform vec3 uSunDir;
uniform vec3 uSunCol;
uniform vec3 uAmbient;
uniform vec3 uCam;
uniform float uFogDensity;
uniform float uGlow;
varying vec3 vColor;
varying float vFog;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    float nl = length(aNormal);
    vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);
    float d = max(dot(n, uSunDir), 0.0);
    float sky = 0.5 + 0.5 * n.y;
    vec3 lit = uAmbient * (0.7 + 0.5 * sky) + uSunCol * d * 0.85;
    vec3 c = aColor * uTint;
    float e = step(1.5, nl);
    vColor = mix(c * lit, c * uGlow, e);
    float dist = length(wp.xyz - uCam) * uFogDensity;
    vFog = clamp(1.0 - exp(-dist * dist), 0.0, 1.0) * (1.0 - e * 0.45);
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""
        const val FRAG = """
precision mediump float;
uniform vec3 uFogCol;
uniform float uAlpha;
varying vec3 vColor;
varying float vFog;
void main() {
    gl_FragColor = vec4(mix(vColor, uFogCol, vFog), uAlpha);
}
"""
        const val SKY_VERT = """
attribute vec2 aPos;
uniform vec3 uForward;
uniform vec3 uRight;
uniform vec3 uUp;
varying vec3 vDir;
void main() {
    vDir = uForward + aPos.x * uRight + aPos.y * uUp;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""
        const val SKY_FRAG = """
precision mediump float;
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uSunDir;
uniform vec3 uSunCol;
uniform vec3 uGround;
varying vec3 vDir;
void main() {
    vec3 d = normalize(vDir);
    float t = clamp(d.y, 0.0, 1.0);
    vec3 col = mix(uHorizon, uZenith, pow(t, 0.55));
    col = mix(uGround, col, smoothstep(-0.02, 0.06, d.y));
    float s = max(dot(d, uSunDir), 0.0);
    col += uSunCol * (pow(s, 180.0) * 0.9 + pow(s, 8.0) * 0.22);
    gl_FragColor = vec4(col, 1.0);
}
"""
    }
}
