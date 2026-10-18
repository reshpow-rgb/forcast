package com.forcast.asxvalue.core

import com.forcast.asxvalue.core.model.Fundamentals
import com.forcast.asxvalue.core.model.PriceHistory
import com.forcast.asxvalue.core.model.PricePoint
import com.forcast.asxvalue.core.model.NewsItem
import com.forcast.asxvalue.core.research.ResearchSummary
import com.forcast.asxvalue.core.valuation.ModelKind
import com.forcast.asxvalue.core.valuation.PriceStats
import com.forcast.asxvalue.core.valuation.ValuationEngine
import com.forcast.asxvalue.core.valuation.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ValuationEngineTest {
    private val cheap = Fundamentals(
        symbol = "EXM.AX", name = "Example Ltd", sector = "Industrials",
        price = 20.0, sharesOutstanding = 1e8, trailingEps = 2.5, bookValuePerShare = 18.0,
        dividendRate = 1.2, payoutRatio = 0.5, returnOnEquity = 0.14, freeCashflow = 2.6e8,
        beta = 1.0, growthEstimate = 0.06, targetMeanPrice = 27.0, analystCount = 8,
        fiftyTwoWeekHigh = 30.0, fiftyTwoWeekLow = 19.0, priceToBook = 1.1, isDetailed = true,
    )

    @Test
    fun grahamNumberMatchesFormula() {
        val v = ValuationEngine.value(cheap, sectorPe = 18.0)
        val g = v.models.first { it.kind == ModelKind.GRAHAM }
        assertEquals(sqrt(22.5 * 2.5 * 18.0), g.value, 1e-9)
    }

    @Test
    fun cheapStockIsUndervaluedWithHighScore() {
        val v = ValuationEngine.value(cheap, sectorPe = 18.0)
        assertEquals(5, v.models.size)
        assertEquals(Verdict.UNDERVALUED, v.verdict)
        assertTrue("score ${v.score}", v.score >= 65)
        assertTrue(v.upside!! > 0.2)
        assertTrue(v.flags.any { it.positive && "Graham" in it.text })
    }

    @Test
    fun expensiveStockIsOvervalued() {
        val pricey = cheap.copy(price = 60.0, fiftyTwoWeekHigh = 61.0, targetMeanPrice = 50.0)
        val v = ValuationEngine.value(pricey, sectorPe = 18.0)
        assertEquals(Verdict.OVERVALUED, v.verdict)
        assertTrue("score ${v.score}", v.score < 35)
    }

    @Test
    fun banksSkipDcf() {
        val bank = cheap.copy(sector = "Financial Services")
        val v = ValuationEngine.value(bank, sectorPe = 15.0)
        assertFalse(v.models.any { it.kind == ModelKind.DCF })
    }

    @Test
    fun insufficientDataWhenOnlyPrice() {
        val v = ValuationEngine.value(Fundamentals(symbol = "NEW.AX", price = 1.0), sectorPe = null)
        assertEquals(Verdict.INSUFFICIENT_DATA, v.verdict)
        assertNull(v.fairValue)
    }

    @Test
    fun outliersAreExcluded() {
        val weird = cheap.copy(freeCashflow = 5e10) // FCF/share of $500 on a $20 stock
        val v = ValuationEngine.value(weird, sectorPe = 18.0)
        assertTrue(v.models.first { it.kind == ModelKind.DCF }.outlier)
        assertNotNull(v.fairValue)
        assertTrue(v.fairValue!! < 80)
    }

    @Test
    fun dcfOfZeroGrowthIsPerpetuity() {
        // With 0% growth everywhere a DCF collapses to FCF / r.
        assertEquals(1.0 / 0.1, ValuationEngine.dcf(1.0, 0.0, 0.1, 0.0), 1e-9)
    }

    @Test
    fun sectorMedianIgnoresLossesAndExtremes() {
        val peers = listOf(10.0, 12.0, 14.0, -5.0, 300.0).map { pe ->
            "Energy" to Fundamentals(symbol = "X.AX", trailingPe = pe)
        }
        assertEquals(12.0, ValuationEngine.sectorMedianPe(peers)["Energy"]!!, 1e-9)
    }

    @Test
    fun priceStatsAndSummary() {
        val day = 86_400L
        val pts = (0 until 400).map { i -> PricePoint(i * day, 10.0 + i * 0.01) }
        val stats = PriceStats.from(PriceHistory("EXM.AX", "AUD", pts))!!
        assertEquals(0.0, stats.maxDrawdown, 1e-12)
        assertNotNull(stats.sma200)
        assertTrue(stats.cagr!! > 0)

        val v = ValuationEngine.value(cheap, 18.0, priceStats = stats)
        val md = ResearchSummary.build(cheap, v, stats, listOf(NewsItem("Headline", "AFR", "https://x.test", 1_700_000_000)))
        assertTrue(md.startsWith("# Example Ltd (ASX: EXM)"))
        assertTrue("## Valuation" in md)
        assertTrue("[Headline](https://x.test)" in md)
    }
}
