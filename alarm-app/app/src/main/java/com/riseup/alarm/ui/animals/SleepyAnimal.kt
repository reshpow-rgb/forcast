package com.riseup.alarm.ui.animals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.riseup.alarm.data.Animal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A cute animal that wakes up as [wake] goes from 0 (fast asleep) to 1 (wide awake).
 * Changes in [wake] are eased over [wakeMillis] so it wakes *slowly*.
 */
@Composable
fun SleepyAnimal(
    animal: Animal,
    wake: Float,
    modifier: Modifier = Modifier,
    zColor: Color = Color.Gray,
    wakeMillis: Int = 2500,
) {
    if (animal == Animal.NONE) return
    val w by animateFloatAsState(wake.coerceIn(0f, 1f), tween(wakeMillis), label = "wake")
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier) {
        val unit = size.minDimension / 100f
        withTransform({
            translate((size.width - 100 * unit) / 2, (size.height - 100 * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            drawAnimal(animal, Pose.of(w, time), zColor)
        }
    }
}

/** Everything that changes as the animal wakes, derived from wakefulness and time. */
internal class Pose(
    val t: Float,
    /** 0 = eyes shut, 1 = open. */
    val eyes: Float,
    /** 0 = ears/tuft drooping, 1 = perked up. */
    val perk: Float,
    /** 0..1 mouth opening for a yawn. */
    val yawn: Float,
    /** 0..1 how happy/awake: smile, blush, bounce. */
    val joy: Float,
    /** Opacity of the floating Zs. */
    val zzz: Float,
    val breathe: Float,
    val tilt: Float,
) {
    companion object {
        fun of(w: Float, t: Float): Pose {
            val joy = smoothstep(0.88f, 1f, w)
            val mid = w in 0.3f..0.9f
            // Half awake: eyes flutter. Fully awake: an occasional blink.
            var eyes = smoothstep(0.3f, 0.92f, w)
            if (mid) eyes *= 0.55f + 0.45f * abs(sin(t * 1.3f))
            if (joy > 0.5f && (t % 3.7f) < 0.14f) eyes = 0f
            // Big yawn every few seconds while half awake.
            val yawnPhase = (t % 5.5f) / 1.6f
            val yawn = if (mid && yawnPhase < 1f) sin(PI.toFloat() * yawnPhase) else 0f
            return Pose(
                t = t,
                eyes = eyes,
                perk = smoothstep(0.2f, 1f, w),
                yawn = yawn,
                joy = joy,
                zzz = 1f - smoothstep(0.25f, 0.55f, w),
                breathe = sin(t * 1.6f) * (1f - joy),
                tilt = -9f * (1f - smoothstep(0.2f, 0.9f, w)),
            )
        }
    }
}

private val Dark = Color(0xFF2B2220)
private val Blush = Color(0xFFFF8FA3)
private val InnerEar = Color(0xFFF4A7B0)
private val Tongue = Color(0xFFB2454F)

private fun DrawScope.drawAnimal(animal: Animal, p: Pose, zColor: Color) {
    // Soft shadow
    oval(zColor.copy(alpha = 0.15f), 50f, 96f, 52f - 6f * p.breathe, 6f)

    val bounce = -abs(sin(p.t * 3.2f)) * 3f * p.joy
    withTransform({
        translate(0f, bounce)
        rotate(p.tilt, Offset(50f, 92f))
        scale(1f, 1f + 0.025f * p.breathe, Offset(50f, 95f))
    }) {
        when (animal) {
            Animal.CORGI -> corgi(p)
            Animal.KITTEN -> kitten(p)
            Animal.GIRAFFE -> giraffe(p)
            Animal.ELEPHANT -> elephant(p)
            Animal.MARMOT -> marmot(p)
            Animal.OTTERS -> otters(p)
            Animal.BIRD -> bird(p)
            Animal.NONE -> Unit
        }
    }
    drawZzz(p, zColor)
}

// ---------------------------------------------------------------- animals (100 x 100 units)

private fun DrawScope.corgi(p: Pose) {
    val fur = Color(0xFFE8964A)
    val white = Color(0xFFFFF6EC)
    ears(fur, 22f, 46f, 42f, 32f, lerp(Offset(13f, 36f), Offset(22f, 10f), p.perk))
    oval(fur, 50f, 58f, 66f, 56f)
    // White blaze down the forehead and over the muzzle
    drawRoundRect(white, Offset(46f, 33f), Size(8f, 30f), CornerRadius(4f))
    oval(white, 50f, 70f, 40f, 28f)
    eye(38f, 54f, 4.4f, p.eyes, fur)
    eye(62f, 54f, 4.4f, p.eyes, fur)
    cheeks(31f, 64f, 69f, 64f, p)
    oval(Dark, 50f, 62f, 10f, 7f)
    mouth(50f, 67.5f, 10f, p)
}

private fun DrawScope.kitten(p: Pose) {
    val fur = Color(0xFFC9C2BB)
    val stripe = Color(0xFF9C948C)
    val muzzle = Color(0xFFF4F0EC)
    ears(fur, 20f, 46f, 40f, 34f, lerp(Offset(10f, 30f), Offset(18f, 12f), p.perk))
    oval(fur, 50f, 58f, 68f, 56f)
    listOf(44f to 39f, 50f to 36f, 56f to 39f).forEach { (x, y) ->
        drawRoundRect(stripe, Offset(x - 1.6f, y - 5f), Size(3.2f, 10f), CornerRadius(1.6f))
    }
    eye(37f, 54f, 4.8f, p.eyes, fur)
    eye(63f, 54f, 4.8f, p.eyes, fur)
    oval(muzzle, 44f, 67f, 14f, 10f)
    oval(muzzle, 56f, 67f, 14f, 10f)
    cheeks(30f, 64f, 70f, 64f, p)
    whiskers(36f, 66f, 64f, 66f)
    // Little pink triangle nose
    drawPath(Path().apply { moveTo(46.5f, 61f); lineTo(53.5f, 61f); lineTo(50f, 65f); close() }, InnerEar)
    mouth(50f, 67f, 9f, p)
}

private fun DrawScope.giraffe(p: Pose) {
    val coat = Color(0xFFF3C66B)
    val spot = Color(0xFFC9853A)
    val knob = Color(0xFF8A5A2B)
    val muzzle = Color(0xFFF8DFA9)
    // Ossicones
    val l = lerp(Offset(34f, 16f), Offset(39f, 11f), p.perk)
    val r = Offset(100f - l.x, l.y)
    drawLine(coat, Offset(43f, 32f), l, 5f, StrokeCap.Round)
    drawLine(coat, Offset(57f, 32f), r, 5f, StrokeCap.Round)
    drawCircle(knob, 4.5f, l)
    drawCircle(knob, 4.5f, r)
    // Ears stick out sideways and droop while asleep
    val earAngle = lerp(40f, -8f, p.perk)
    withTransform({ rotate(earAngle, Offset(32f, 40f)) }) {
        oval(coat, 22f, 40f, 22f, 10f); oval(InnerEar, 21f, 40f, 12f, 4.5f)
    }
    withTransform({ rotate(-earAngle, Offset(68f, 40f)) }) {
        oval(coat, 78f, 40f, 22f, 10f); oval(InnerEar, 79f, 40f, 12f, 4.5f)
    }
    oval(coat, 50f, 54f, 46f, 56f)
    drawCircle(spot, 3.2f, Offset(41f, 40f))
    drawCircle(spot, 2.4f, Offset(58f, 37f))
    drawCircle(spot, 2f, Offset(62f, 46f))
    oval(muzzle, 50f, 74f, 40f, 26f)
    eye(40f, 53f, 4.2f, p.eyes, coat)
    eye(60f, 53f, 4.2f, p.eyes, coat)
    cheeks(34f, 63f, 66f, 63f, p)
    oval(Dark, 44f, 72f, 3.4f, 2.4f)
    oval(Dark, 56f, 72f, 3.4f, 2.4f)
    mouth(50f, 79f, 10f, p)
}

private fun DrawScope.elephant(p: Pose) {
    val skin = Color(0xFFA9B6C4)
    val shade = Color(0xFF94A2B2)
    val flap = 1f + 0.1f * sin(p.t * 5f) * p.joy
    oval(skin, 20f, 54f, 34f * flap, 46f)
    oval(InnerEar, 22f, 55f, 21f * flap, 31f)
    oval(skin, 80f, 54f, 34f * flap, 46f)
    oval(InnerEar, 78f, 55f, 21f * flap, 31f)
    drawCircle(skin, 28f, Offset(50f, 50f))
    // Tuft of baby hair
    listOf(-4f, 0f, 4f).forEach { dx ->
        drawLine(shade, Offset(50f + dx, 24f), Offset(50f + dx * 1.5f, 17f + abs(dx) * 0.4f), 1.6f, StrokeCap.Round)
    }
    eye(39f, 47f, 4.2f, p.eyes, skin)
    eye(61f, 47f, 4.2f, p.eyes, skin)
    cheeks(30f, 57f, 70f, 57f, p)
    // Trunk hangs while asleep, curls up happily when awake
    val end = lerp(Offset(47f, 90f), Offset(71f, 64f), p.joy)
    val c2 = lerp(Offset(45f, 84f), Offset(66f, 88f), p.joy)
    val trunk = Path().apply {
        moveTo(50f, 56f)
        cubicTo(50f, 72f, c2.x, c2.y, end.x, end.y)
    }
    drawPath(trunk, skin, style = Stroke(11f, cap = StrokeCap.Round))
    drawCircle(shade, 3.2f, end)
    if (p.yawn > 0.05f) oval(Tongue, 42f, 72f, 6f * p.yawn + 1f, 7f * p.yawn + 1f)
}

private fun DrawScope.marmot(p: Pose) {
    val fur = Color(0xFFA47551)
    val dark = Color(0xFF7A5236)
    val muzzle = Color(0xFFDDBB95)
    drawCircle(fur, 7f, Offset(25f, 37f)); drawCircle(dark, 3.8f, Offset(25f, 37f))
    drawCircle(fur, 7f, Offset(75f, 37f)); drawCircle(dark, 3.8f, Offset(75f, 37f))
    oval(fur, 50f, 59f, 72f, 60f)
    oval(dark.copy(alpha = 0.55f), 50f, 37f, 40f, 14f)
    eye(36f, 53f, 4.3f, p.eyes, fur)
    eye(64f, 53f, 4.3f, p.eyes, fur)
    oval(muzzle, 50f, 70f, 36f, 25f)
    cheeks(28f, 64f, 72f, 64f, p)
    oval(Dark, 50f, 64f, 9f, 6f)
    whiskers(40f, 68f, 60f, 68f)
    mouth(50f, 70f, 9f, p)
    if (p.joy > 0.4f && p.yawn < 0.05f) {
        // Buck teeth
        listOf(47.4f, 50.2f).forEach {
            drawRect(Color.White, Offset(it, 72f), Size(2.4f, 4f))
            drawRect(Dark, Offset(it, 72f), Size(2.4f, 4f), style = Stroke(0.5f))
        }
    }
}

private fun DrawScope.otters(p: Pose) {
    val fur = Color(0xFF8C6A4F)
    // Floating side by side, holding paws
    oval(fur, 30f, 86f, 34f, 18f)
    oval(fur, 70f, 86f, 34f, 18f)
    oval(Color(0xFFE9D6BD), 30f, 87f, 22f, 10f)
    oval(Color(0xFFE9D6BD), 70f, 87f, 22f, 10f)
    withTransform({ translate(30f - 50f * 0.56f, 56f - 52f * 0.56f); scale(0.56f, 0.56f, Offset.Zero); rotate(8f, Offset(50f, 52f)) }) {
        otterHead(p, fur)
    }
    withTransform({ translate(70f - 50f * 0.56f, 58f - 52f * 0.56f); scale(0.56f, 0.56f, Offset.Zero); rotate(-8f, Offset(50f, 52f)) }) {
        otterHead(p, fur)
    }
    // Paws meeting in the middle
    oval(fur, 46f, 80f, 10f, 7f)
    oval(fur, 54f, 80f, 10f, 7f)
}

private fun DrawScope.otterHead(p: Pose, fur: Color) {
    drawCircle(fur, 6f, Offset(24f, 34f))
    drawCircle(fur, 6f, Offset(76f, 34f))
    oval(fur, 50f, 54f, 68f, 58f)
    oval(Color(0xFFE9D6BD), 50f, 66f, 48f, 34f)
    eye(35f, 48f, 5f, p.eyes, fur)
    eye(65f, 48f, 5f, p.eyes, fur)
    cheeks(26f, 62f, 74f, 62f, p)
    oval(Dark, 50f, 59f, 13f, 9f)
    listOf(38f to 66f, 42f to 69f, 58f to 69f, 62f to 66f).forEach { (x, y) -> drawCircle(Dark, 0.9f, Offset(x, y)) }
    mouth(50f, 69f, 11f, p)
}

private fun DrawScope.bird(p: Pose) {
    val blue = Color(0xFF5B8FD9)
    val wing = Color(0xFF3F6FB8)
    val belly = Color(0xFFF59A5B)
    val beak = Color(0xFFF2B33D)
    // Feet
    drawLine(beak, Offset(44f, 86f), Offset(44f, 94f), 2.4f, StrokeCap.Round)
    drawLine(beak, Offset(56f, 86f), Offset(56f, 94f), 2.4f, StrokeCap.Round)
    // Head tuft rises as it wakes
    val tuftY = lerp(8f, 0f, p.perk)
    withTransform({ translate(0f, tuftY) }) {
        listOf(-14f to 46f, 0f to 50f, 14f to 54f).forEach { (angle, x) ->
            withTransform({ rotate(angle, Offset(x, 30f)) }) { oval(wing, x, 26f, 6f, 14f) }
        }
    }
    drawCircle(blue, 31f, Offset(50f, 58f))
    oval(belly, 50f, 72f, 38f, 30f)
    // Wings flap when awake
    val flap = 18f * sin(p.t * 12f) * p.joy
    withTransform({ rotate(20f - flap, Offset(28f, 52f)) }) { oval(wing, 24f, 64f, 16f, 30f) }
    withTransform({ rotate(-20f + flap, Offset(72f, 52f)) }) { oval(wing, 76f, 64f, 16f, 30f) }
    eye(40f, 49f, 4.3f, p.eyes, blue)
    eye(60f, 49f, 4.3f, p.eyes, blue)
    cheeks(33f, 59f, 67f, 59f, p)
    // Beak opens for a yawn or a happy chirp
    val open = maxOf(p.yawn, if (p.joy > 0.5f) 0.35f * abs(sin(p.t * 4f)) else 0f) * 5f
    if (open > 0.3f) oval(Tongue, 50f, 59f + open / 2, 6f, open + 1f)
    drawPath(Path().apply { moveTo(44.5f, 55f); lineTo(55.5f, 55f); lineTo(50f, 60.5f); close() }, beak)
    drawPath(Path().apply { moveTo(46f, 59f + open); lineTo(54f, 59f + open); lineTo(50f, 63f + open); close() }, beak)
}

// ---------------------------------------------------------------- shared features

/** Pointed ears (corgi, kitten). Left ear given; right is mirrored. */
private fun DrawScope.ears(color: Color, bx1: Float, by1: Float, bx2: Float, by2: Float, tip: Offset) {
    fun ear(mirror: Boolean) {
        fun x(v: Float) = if (mirror) 100f - v else v
        drawPath(Path().apply { moveTo(x(bx1), by1); lineTo(x(tip.x), tip.y); lineTo(x(bx2), by2); close() }, color)
        // Inner ear: same triangle shrunk towards its centre
        val cx = (bx1 + bx2 + tip.x) / 3
        val cy = (by1 + by2 + tip.y) / 3
        fun s(v: Float, c: Float) = c + (v - c) * 0.55f
        drawPath(
            Path().apply {
                moveTo(x(s(bx1, cx)), s(by1, cy)); lineTo(x(s(tip.x, cx)), s(tip.y, cy)); lineTo(x(s(bx2, cx)), s(by2, cy)); close()
            },
            InnerEar,
        )
    }
    ear(false)
    ear(true)
}

/** An eye that opens like a lid lifting. Shut, it's a sleepy curve. */
private fun DrawScope.eye(cx: Float, cy: Float, r: Float, open: Float, lid: Color) {
    if (open < 0.08f) {
        val path = Path().apply {
            moveTo(cx - r, cy)
            quadraticBezierTo(cx, cy + r * 0.95f, cx + r, cy)
        }
        drawPath(path, Dark, style = Stroke(r * 0.42f, cap = StrokeCap.Round))
        return
    }
    drawCircle(Dark, r, Offset(cx, cy))
    if (open > 0.5f) drawCircle(Color.White, r * 0.34f, Offset(cx - r * 0.32f, cy - r * 0.36f))
    if (open >= 0.97f) return
    // Heavy upper lid whose edge sags in the middle - reads as sleepy rather than grumpy.
    val lidBottom = cy - r + (1f - open) * 2f * r
    val sag = r * 0.55f * (1f - open)
    val top = cy - r - 0.8f
    drawPath(
        Path().apply {
            moveTo(cx - r - 0.8f, top)
            lineTo(cx + r + 0.8f, top)
            lineTo(cx + r + 0.8f, lidBottom)
            quadraticBezierTo(cx, lidBottom + 2 * sag, cx - r - 0.8f, lidBottom)
            close()
        },
        lid,
    )
    val dy = lidBottom - cy
    val half = sqrt((r * r - dy * dy).coerceAtLeast(0f))
    drawPath(
        Path().apply {
            moveTo(cx - half, lidBottom)
            quadraticBezierTo(cx, lidBottom + 2 * sag, cx + half, lidBottom)
        },
        Dark,
        style = Stroke(r * 0.22f, cap = StrokeCap.Round),
    )
}

/** A little "w" mouth that becomes a smile when happy, or a round yawn. */
private fun DrawScope.mouth(cx: Float, cy: Float, width: Float, p: Pose) {
    if (p.yawn > 0.05f) {
        oval(Tongue, cx, cy + p.yawn * 2.5f, width * 0.7f * p.yawn + 1.5f, width * 0.9f * p.yawn + 1.5f)
        return
    }
    val depth = width * (0.18f + 0.3f * p.joy)
    val path = Path().apply {
        moveTo(cx - width / 2, cy)
        quadraticBezierTo(cx - width / 4, cy + depth, cx, cy)
        quadraticBezierTo(cx + width / 4, cy + depth, cx + width / 2, cy)
    }
    drawPath(path, Dark, style = Stroke(1.3f, cap = StrokeCap.Round))
}

private fun DrawScope.cheeks(lx: Float, ly: Float, rx: Float, ry: Float, p: Pose) {
    val c = Blush.copy(alpha = 0.25f + 0.35f * p.joy)
    oval(c, lx, ly, 9f, 5.5f)
    oval(c, rx, ry, 9f, 5.5f)
}

private fun DrawScope.whiskers(lx: Float, ly: Float, rx: Float, ry: Float) {
    val c = Dark.copy(alpha = 0.55f)
    listOf(-3f, 2f).forEach { dy ->
        drawLine(c, Offset(lx, ly), Offset(lx - 16f, ly + dy * 1.5f - 1f), 0.7f, StrokeCap.Round)
        drawLine(c, Offset(rx, ry), Offset(rx + 16f, ry + dy * 1.5f - 1f), 0.7f, StrokeCap.Round)
    }
}

private fun DrawScope.drawZzz(p: Pose, color: Color) {
    if (p.zzz <= 0.01f) return
    repeat(3) { i ->
        val phase = ((p.t * 0.35f) + i / 3f) % 1f
        val s = 4f + phase * 5f
        val x = 74f + phase * 12f
        val y = 30f - phase * 26f
        val alpha = p.zzz * sin(PI.toFloat() * phase)
        val z = Path().apply { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
        drawPath(z, color.copy(alpha = alpha), style = Stroke(1.4f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, w: Float, h: Float) =
    drawOval(color, Offset(cx - w / 2, cy - h / 2), Size(w, h))

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun lerp(a: Offset, b: Offset, t: Float) = Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

internal fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}
