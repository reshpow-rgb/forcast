package com.forcast.asxvalue.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Display formatting shared by the screens. Figures are Australian dollars. */
object Fmt {
    private val au = Locale("en", "AU")
    private val zone = ZoneId.of("Australia/Sydney")
    private val day = DateTimeFormatter.ofPattern("d MMM yyyy", au).withZone(zone)

    fun money(v: Double?): String = when {
        v == null -> "–"
        abs(v) >= 1000 -> "$" + NumberFormat.getNumberInstance(au).apply { maximumFractionDigits = 0 }.format(v)
        abs(v) >= 1 -> "$%.2f".format(au, v)
        else -> "$%.3f".format(au, v)
    }

    fun pct(v: Double?, signed: Boolean = false): String =
        if (v == null) "–" else (if (signed && v >= 0) "+" else "") + "%.1f%%".format(au, v * 100)

    fun num(v: Double?, digits: Int = 1): String = if (v == null) "–" else "%.${digits}f".format(au, v)

    fun big(v: Double?): String = when {
        v == null -> "–"
        abs(v) >= 1e12 -> "$%.2ftn".format(au, v / 1e12)
        abs(v) >= 1e9 -> "$%.2fbn".format(au, v / 1e9)
        abs(v) >= 1e6 -> "$%.1fm".format(au, v / 1e6)
        else -> money(v)
    }

    fun date(epochSec: Long): String = day.format(Instant.ofEpochSecond(epochSec))
}
