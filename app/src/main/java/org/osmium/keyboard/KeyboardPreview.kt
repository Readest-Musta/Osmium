package org.osmium.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * A small, live drawing of the keyboard driven by the exact same palette and
 * sizing preferences as the real IME. Settings no longer feel inert: changing a
 * colour or slider in this Activity updates this preview immediately.
 */
class KeyboardPreview(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun dp(value: Float): Float = value * density

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, dp(150f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pal = Theme.palette(context)
        val w = width.toFloat()
        val h = height.toFloat()

        paint.color = pal.keyboardBg
        canvas.drawRect(0f, 0f, w, h, paint)

        // Suggestion strip with one highlighted candidate.
        val barH = dp(26f)
        paint.color = pal.barBg
        canvas.drawRect(0f, 0f, w, barH, paint)
        val candidates = listOf("আমি", "ami", "বাংলা")
        val chipW = w / (candidates.size + 2f)
        candidates.forEachIndexed { index, text ->
            val left = chipW * (index + 1)
            if (index == 0) {
                paint.color = pal.accent
                canvas.drawRoundRect(
                    RectF(left + dp(2f), dp(3f), left + chipW - dp(2f), barH - dp(3f)),
                    dp(6f), dp(6f), paint,
                )
                paint.color = ColorMath.onColor(pal.accent)
            } else {
                paint.color = pal.keyText
            }
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(11f)
            canvas.drawText(text, left + chipW / 2f, barH - dp(7f), paint)
        }

        // Four rows of stylised keys, using the real radius / height / font prefs.
        val rows = listOf(
            listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"),
            listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"),
            listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"),
            listOf("⇧", "প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "⌫"),
        )
        val gap = dp(3f)
        val keyH = (dp(26f) * Prefs.keyHeightScale(context) / 100f)
            .coerceAtLeast(dp(14f))
            .coerceAtMost(dp(34f))
        val keyW = (w - gap * (rows[0].size + 1)) / rows[0].size
        val radius = dp(Prefs.keyRadiusDp(context).toFloat()).coerceAtMost(keyH / 2f)
        val textSize = dp(11f * Prefs.keyFontScale(context) / 100f)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize

        rows.forEachIndexed { rowIndex, row ->
            val top = barH + dp(6f) + rowIndex * (keyH + gap)
            row.forEachIndexed { colIndex, label ->
                val left = gap + colIndex * (keyW + gap)
                val rect = RectF(left, top, left + keyW, top + keyH)
                val isAction = colIndex == 0 && rowIndex == rows.lastIndex ||
                    colIndex == row.lastIndex && rowIndex == rows.lastIndex
                paint.color = if (isAction) pal.keyAction else pal.keyNormal
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.color = if (isAction) pal.keyTextHint else ColorMath.onColor(pal.keyNormal)
                canvas.drawText(label, rect.centerX(), rect.centerY() + textSize / 3f, paint)
            }
        }
    }

    /** Redraw with the latest preferences. */
    fun refresh() {
        invalidate()
    }
}
