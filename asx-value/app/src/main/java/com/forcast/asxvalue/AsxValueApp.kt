package com.forcast.asxvalue

import android.app.Application
import com.forcast.asxvalue.core.data.YahooFinance
import com.forcast.asxvalue.data.Prefs
import com.forcast.asxvalue.data.StockRepository

class AsxValueApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repository: StockRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = StockRepository(YahooFinance())
    }
}
