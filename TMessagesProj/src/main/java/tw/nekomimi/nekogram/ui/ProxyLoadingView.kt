package tw.nekomimi.nekogram.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.ui.ActionBar.Theme
import kotlin.math.*

/**
 * A fully custom, hardware-accelerated animated loading view.
 * Features:
 *   - Pulsing concentric rings with ripple fade-out
 *   - Orbiting particles on 3 independent orbital planes
 *   - Rotating radar/scanner arc
 *   - Animated shimmer on the status label
 *   - Subtle breathing glow on the core node
 */
class ProxyLoadingView(context: Context) : View(context) {

    // ─── Paints ────────────────────────────────────────────────────────────────

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f).toFloat()
    }
    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(14f).toFloat()
    }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(14f).toFloat()
    }
    private val subLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = dp(12f).toFloat()
        alpha = 160
    }

    // ─── Animation State ───────────────────────────────────────────────────────

    // Master clock: 0f → 1f repeating (period = ~4s)
    private var tick = 0f

    // Radar rotation: 0 → 360 continuously
    private var radarAngle = 0f

    // Ripple rings: 3 staggered rings, each with their own phase
    private data class RippleRing(var phase: Float)
    private val ripples = listOf(RippleRing(0f), RippleRing(0.33f), RippleRing(0.66f))

    // Orbital particles: 3 orbital planes, 2 particles each
    private data class Particle(
        val orbitRadius: Float,   // fraction of coreRadius
        val orbitSpeed: Float,    // rotations per second
        val orbitTilt: Float,     // tilt angle of the orbital plane in degrees
        val size: Float,          // dot size in dp
        val phaseOffset: Float,   // starting angle offset 0-360
        val color: Int
    )
    private lateinit var particles: List<Particle>

    // Shimmer sweep: -1 → 2 linear
    private var shimmerX = -1f

    // Core breathe: scale 0.9 → 1.0 → 0.9
    private var breatheScale = 1f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            tick = anim.animatedFraction

            // Radar: full 360 every 2.5s → 360/2500ms * 16ms ≈ 2.304° per frame
            radarAngle = (tick * 360f * 1.6f) % 360f

            // Ripples advance their phase by tick speed
            ripples.forEach { r ->
                r.phase = ((r.phase + 1f / 75f) % 1f)
            }

            // Shimmer: sweeps across label in ~1.5s out of every 4s cycle
            shimmerX = (tick * 3f) - 1f

            // Breathe: sin wave, 0.92 → 1.0
            breatheScale = 0.92f + 0.08f * ((sin(tick * 2 * Math.PI.toFloat()) + 1f) / 2f)

            invalidate()
        }
    }

    private var coreColor = 0xFF4FC3F7.toInt()  // Light blue — updated with theme
    private var isDark = false

    // ─── Colors (updated from theme) ──────────────────────────────────────────
    private fun refreshColors() {
        isDark = Theme.isCurrentThemeDark()

        // Core accent: cyan-blue in dark, deep blue in light
        coreColor = if (isDark) 0xFF29B6F6.toInt() else 0xFF1565C0.toInt()

        val bg = if (isDark) Color.parseColor("#0D1117") else Color.parseColor("#EEF2F7")
        setBackgroundColor(bg)

        labelPaint.color = if (isDark) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        subLabelPaint.color = if (isDark) 0xFFAAAAAA.toInt() else 0xFF777777.toInt()
    }

    // ─── Layout ───────────────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val coreR = minOf(w, h) * 0.10f

        // Build particles now that we know coreR
        particles = listOf(
            // Inner orbit — fast, cyan
            Particle(2.4f, 0.9f, 0f, 4f, 0f, 0xFF29B6F6.toInt()),
            Particle(2.4f, 0.9f, 0f, 3f, 180f, 0xFF29B6F6.toInt()),
            // Mid orbit — medium, green
            Particle(3.8f, 0.55f, 30f, 4.5f, 60f, 0xFF66BB6A.toInt()),
            Particle(3.8f, 0.55f, 30f, 3f, 240f, 0xFF66BB6A.toInt()),
            // Outer orbit — slow, orange
            Particle(5.5f, 0.35f, 60f, 5f, 120f, 0xFFFFB74D.toInt()),
            Particle(5.5f, 0.35f, 60f, 3f, 300f, 0xFFFFB74D.toInt()),
        )
    }

    // ─── Draw ─────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = (height * 0.42f)   // slightly above center to leave room for label
        val coreR = minOf(width, height) * 0.10f

        refreshColors()

        // 1. Draw ripple rings ─────────────────────────────────────────────────
        ripples.forEach { ring ->
            val progress = ring.phase  // 0 → 1
            val ringRadius = coreR * (1.2f + progress * 5.5f)
            val alpha = ((1f - progress) * (1f - progress) * 200).toInt().coerceIn(0, 255)
            ringPaint.color = coreColor and 0x00FFFFFF.toInt() or (alpha shl 24)
            canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        }

        // 2. Draw orbital paths (faint) ────────────────────────────────────────
        val orbitAlpha = if (isDark) 0x1A else 0x12
        listOf(2.4f, 3.8f, 5.5f).forEach { orbitFrac ->
            ringPaint.color = (coreColor and 0x00FFFFFF.toInt()) or (orbitAlpha shl 24)
            canvas.drawCircle(cx, cy, coreR * orbitFrac, ringPaint)
        }

        // 3. Draw orbiting particles ───────────────────────────────────────────
        particles.forEach { p ->
            val orbitR = coreR * p.orbitRadius
            val angleDeg = (tick * 360f * p.orbitSpeed + p.phaseOffset) % 360f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val tiltRad = Math.toRadians(p.orbitTilt.toDouble())

            // Project onto the screen with tilt (simple y-foreshortening)
            val x = cx + orbitR * cos(angleRad).toFloat()
            val y = cy + orbitR * sin(angleRad).toFloat() * cos(tiltRad).toFloat()

            // Alpha fades when particle is "behind" (y-coord up in orbital perspective)
            val depthAlpha = ((cos(angleRad).toFloat() * 0.3f + 0.7f) * 255).toInt().coerceIn(60, 255)
            particlePaint.color = (p.color and 0x00FFFFFF.toInt()) or (depthAlpha shl 24)

            canvas.drawCircle(x, y, dp(p.size).toFloat() / 2f, particlePaint)

            // Trail dot half-step behind
            val trailAngleDeg = (angleDeg - 22f + 360f) % 360f
            val trailRad = Math.toRadians(trailAngleDeg.toDouble())
            val tx = cx + orbitR * cos(trailRad).toFloat()
            val ty = cy + orbitR * sin(trailRad).toFloat() * cos(tiltRad).toFloat()
            val trailAlpha = (depthAlpha * 0.35f).toInt()
            particlePaint.color = (p.color and 0x00FFFFFF.toInt()) or (trailAlpha shl 24)
            canvas.drawCircle(tx, ty, dp(p.size).toFloat() / 3f, particlePaint)
        }

        // 4. Draw radar sweep ──────────────────────────────────────────────────
        val radarSweepR = coreR * 4.8f
        val radarRect = RectF(cx - radarSweepR, cy - radarSweepR, cx + radarSweepR, cy + radarSweepR)
        val sweepShader = SweepGradient(
            cx, cy,
            intArrayOf(0x00FFFFFF.toInt() and coreColor, (coreColor and 0x00FFFFFF.toInt()) or 0x99000000.toInt(), coreColor),
            floatArrayOf(0f, 0.25f, 1f)
        )
        val matrix = Matrix()
        matrix.preRotate(radarAngle, cx, cy)
        sweepShader.setLocalMatrix(matrix)
        radarPaint.shader = sweepShader
        canvas.drawArc(radarRect, radarAngle - 90f, 270f, false, radarPaint)
        radarPaint.shader = null

        // 5. Draw core glow (outer glow -> inner core) ─────────────────────────
        val glowR = coreR * breatheScale * 1.35f
        val glowShader = RadialGradient(
            cx, cy, glowR,
            intArrayOf(coreColor, (coreColor and 0x00FFFFFF.toInt()) or 0x55000000.toInt(), 0x00FFFFFF.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.shader = glowShader
        canvas.drawCircle(cx, cy, glowR, corePaint)

        // Core solid disc
        val coreShader = RadialGradient(
            cx, cy, coreR * breatheScale,
            intArrayOf(0xFFFFFFFF.toInt(), coreColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        corePaint.shader = coreShader
        canvas.drawCircle(cx, cy, coreR * breatheScale, corePaint)
        corePaint.shader = null

        // 6. Draw label with shimmer ───────────────────────────────────────────
        val labelY = cy + coreR * 7.2f
        val subLabelY = labelY + dp(20f)
        val labelText = "Fetching Proxies"
        val subText = "Scanning secure nodes..."

        // Base label
        labelPaint.typeface = AndroidUtilities.bold()
        canvas.drawText(labelText, cx, labelY, labelPaint)

        // Shimmer overlay: sweeps left → right
        if (shimmerX in -1f..2f) {
            val shimmerWidth = width * 0.35f
            val shimmerCX = cx - width / 2f + (shimmerX + 1f) / 2f * (width + shimmerWidth)
            val shimmerShader = LinearGradient(
                shimmerCX - shimmerWidth, labelY,
                shimmerCX + shimmerWidth, labelY,
                intArrayOf(0x00FFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            shimmerPaint.shader = shimmerShader
            shimmerPaint.typeface = AndroidUtilities.bold()
            shimmerPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            canvas.drawText(labelText, cx, labelY, shimmerPaint)
            shimmerPaint.xfermode = null
            shimmerPaint.shader = null
        }

        // Sub label
        canvas.drawText(subText, cx, subLabelY, subLabelPaint)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshColors()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
