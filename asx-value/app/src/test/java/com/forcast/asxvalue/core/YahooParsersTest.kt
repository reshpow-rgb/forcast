package com.forcast.asxvalue.core

import com.forcast.asxvalue.core.data.YahooParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YahooParsersTest {
    @Test
    fun parsesChartSkippingNullCloses() {
        val body = """
            {"chart":{"result":[{"meta":{"currency":"AUD","symbol":"BHP.AX"},
              "timestamp":[1700000000,1700086400,1700172800],
              "events":{"dividends":{"1700086400":{"amount":0.72,"date":1700086400}}},
              "indicators":{"quote":[{"close":[45.1,null,46.3]}],"adjclose":[{"adjclose":[44.0,null,45.5]}]}}],
              "error":null}}
        """.trimIndent()
        val h = YahooParsers.parseChart(body, "BHP.AX")
        assertEquals("AUD", h.currency)
        assertEquals(2, h.points.size)
        assertEquals(46.3, h.points[1].close, 1e-9)
        assertEquals(45.5, h.points[1].adjClose, 1e-9)
        assertEquals(0.72, h.dividends.single().amount, 1e-9)
    }

    @Test(expected = YahooParsers.YahooException::class)
    fun chartErrorThrows() {
        YahooParsers.parseChart("""{"chart":{"result":null,"error":{"code":"Not Found","description":"No data found, symbol may be delisted"}}}""", "XXX.AX")
    }

    @Test
    fun parsesQuoteSummaryRawValues() {
        val body = """
            {"quoteSummary":{"result":[{
              "price":{"regularMarketPrice":{"raw":40.0,"fmt":"40.00"},"regularMarketPreviousClose":{"raw":39.0},"longName":"Example Ltd","currency":"AUD","marketCap":{"raw":2.0E10}},
              "summaryDetail":{"trailingPE":{"raw":12.5},"dividendRate":{"raw":2.0},"payoutRatio":{"raw":0.6},"beta":{"raw":0.9},"fiftyTwoWeekHigh":{"raw":50},"fiftyTwoWeekLow":{"raw":35}},
              "defaultKeyStatistics":{"sharesOutstanding":{"raw":5.0E8},"bookValue":{"raw":20.0},"trailingEps":{"raw":3.2},"forwardEps":{"raw":3.5},"priceToBook":{"raw":2.0},"pegRatio":{}},
              "financialData":{"freeCashflow":{"raw":1.5E9},"totalDebt":{"raw":3.0E9},"totalCash":{"raw":1.0E9},"targetMeanPrice":{"raw":46.0},"numberOfAnalystOpinions":{"raw":12},"recommendationKey":"buy","returnOnEquity":{"raw":0.16},"debtToEquity":{"raw":30.0}},
              "assetProfile":{"sector":"Industrials","industry":"Conglomerates","longBusinessSummary":"Does things."},
              "earningsTrend":{"trend":[{"period":"0y","growth":{"raw":0.05}},{"period":"+1y","growth":{"raw":0.08}}]}
            }],"error":null}}
        """.trimIndent()
        val f = YahooParsers.parseQuoteSummary(body, "EXM.AX")
        assertEquals("Example Ltd", f.name)
        assertEquals(40.0, f.price!!, 1e-9)
        assertEquals(3.0, f.freeCashflowPerShare!!, 1e-9)
        assertEquals(0.05, f.dividendYield!!, 1e-9)
        assertEquals(0.08, f.growthEstimate!!, 1e-9)
        assertEquals(12, f.analystCount)
        assertEquals("buy", f.recommendationKey)
        assertNull(f.pegRatio)
        assertTrue(f.isDetailed)
    }

    @Test
    fun parsesBatchQuotes() {
        val body = """{"quoteResponse":{"result":[
            {"symbol":"CBA.AX","longName":"Commonwealth Bank","regularMarketPrice":150.2,"trailingPE":26.1,"epsTrailingTwelveMonths":5.75,"trailingAnnualDividendRate":4.85,"averageAnalystRating":"4.1 - Underperform"},
            {"symbol":"BHP.AX","shortName":"BHP GROUP","regularMarketPrice":42.0}
          ],"error":null}}"""
        val qs = YahooParsers.parseQuotes(body)
        assertEquals(2, qs.size)
        assertEquals(4.1, qs[0].recommendationMean!!, 1e-9)
        assertEquals("BHP GROUP", qs[1].name)
        assertNull(qs[1].trailingPe)
    }

    @Test
    fun parsesNews() {
        val body = """{"news":[{"title":"BHP lifts output","publisher":"Reuters","link":"https://x.test/a","providerPublishTime":1700000000},{"title":"no link"}]}"""
        val n = YahooParsers.parseNews(body)
        assertEquals(1, n.size)
        assertEquals("Reuters", n[0].publisher)
    }
}
