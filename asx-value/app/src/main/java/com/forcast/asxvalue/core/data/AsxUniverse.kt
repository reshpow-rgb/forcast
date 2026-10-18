package com.forcast.asxvalue.core.data

/** A stock the screener scans. Sector names follow Yahoo Finance's naming. */
data class UniverseStock(val ticker: String, val name: String, val sector: String, val top50: Boolean = false) {
    val symbol: String get() = "$ticker.AX"
}

enum class Universe(val label: String) { ASX50("ASX top 50"), ASX100("ASX ~100 large & mid caps"), WATCHLIST("My watchlist") }

/**
 * A hand-maintained list of large and mid-cap ASX companies. Index membership changes over time,
 * so treat this as a starting point. Any ASX ticker can be searched and added to the watchlist.
 */
object AsxUniverse {
    private const val BM = "Basic Materials"
    private const val FS = "Financial Services"
    private const val HC = "Healthcare"
    private const val EN = "Energy"
    private const val RE = "Real Estate"
    private const val CC = "Consumer Cyclical"
    private const val CD = "Consumer Defensive"
    private const val IN = "Industrials"
    private const val TE = "Technology"
    private const val CS = "Communication Services"
    private const val UT = "Utilities"

    val stocks: List<UniverseStock> = listOf(
        UniverseStock("BHP", "BHP Group", BM, true),
        UniverseStock("CBA", "Commonwealth Bank", FS, true),
        UniverseStock("CSL", "CSL", HC, true),
        UniverseStock("NAB", "National Australia Bank", FS, true),
        UniverseStock("WBC", "Westpac Banking", FS, true),
        UniverseStock("ANZ", "ANZ Group", FS, true),
        UniverseStock("WES", "Wesfarmers", CC, true),
        UniverseStock("MQG", "Macquarie Group", FS, true),
        UniverseStock("GMG", "Goodman Group", RE, true),
        UniverseStock("FMG", "Fortescue", BM, true),
        UniverseStock("RIO", "Rio Tinto", BM, true),
        UniverseStock("WDS", "Woodside Energy", EN, true),
        UniverseStock("TLS", "Telstra Group", CS, true),
        UniverseStock("WOW", "Woolworths Group", CD, true),
        UniverseStock("TCL", "Transurban Group", IN, true),
        UniverseStock("ALL", "Aristocrat Leisure", CC, true),
        UniverseStock("REA", "REA Group", CS, true),
        UniverseStock("QBE", "QBE Insurance", FS, true),
        UniverseStock("WTC", "WiseTech Global", TE, true),
        UniverseStock("COL", "Coles Group", CD, true),
        UniverseStock("JHX", "James Hardie", BM, true),
        UniverseStock("RMD", "ResMed", HC, true),
        UniverseStock("XRO", "Xero", TE, true),
        UniverseStock("NST", "Northern Star Resources", BM, true),
        UniverseStock("SUN", "Suncorp Group", FS, true),
        UniverseStock("COH", "Cochlear", HC, true),
        UniverseStock("BXB", "Brambles", IN, true),
        UniverseStock("PME", "Pro Medicus", HC, true),
        UniverseStock("STO", "Santos", EN, true),
        UniverseStock("CPU", "Computershare", IN, true),
        UniverseStock("IAG", "Insurance Australia Group", FS, true),
        UniverseStock("ORG", "Origin Energy", UT, true),
        UniverseStock("SCG", "Scentre Group", RE, true),
        UniverseStock("EVN", "Evolution Mining", BM, true),
        UniverseStock("S32", "South32", BM, true),
        UniverseStock("ASX", "ASX Ltd", FS, true),
        UniverseStock("SOL", "Washington H. Soul Pattinson", FS, true),
        UniverseStock("TLC", "The Lottery Corporation", CC, true),
        UniverseStock("APA", "APA Group", UT, true),
        UniverseStock("SIG", "Sigma Healthcare", HC, true),
        UniverseStock("MIN", "Mineral Resources", BM, true),
        UniverseStock("JBH", "JB Hi-Fi", CC, true),
        UniverseStock("CAR", "CAR Group", CS, true),
        UniverseStock("SHL", "Sonic Healthcare", HC, true),
        UniverseStock("QAN", "Qantas Airways", IN, true),
        UniverseStock("TNE", "TechnologyOne", TE, true),
        UniverseStock("NXT", "NEXTDC", TE, true),
        UniverseStock("SGP", "Stockland", RE, true),
        UniverseStock("LYC", "Lynas Rare Earths", BM, true),
        UniverseStock("PLS", "Pilbara Minerals", BM, true),

        UniverseStock("AIA", "Auckland International Airport", IN),
        UniverseStock("MPL", "Medibank Private", FS),
        UniverseStock("A2M", "The a2 Milk Company", CD),
        UniverseStock("SEK", "SEEK", CS),
        UniverseStock("ALQ", "ALS", IN),
        UniverseStock("ORI", "Orica", BM),
        UniverseStock("BSL", "BlueScope Steel", BM),
        UniverseStock("WOR", "Worley", IN),
        UniverseStock("TWE", "Treasury Wine Estates", CD),
        UniverseStock("AGL", "AGL Energy", UT),
        UniverseStock("MGR", "Mirvac Group", RE),
        UniverseStock("DXS", "Dexus", RE),
        UniverseStock("VCX", "Vicinity Centres", RE),
        UniverseStock("GPT", "GPT Group", RE),
        UniverseStock("CHC", "Charter Hall Group", RE),
        UniverseStock("SDF", "Steadfast Group", FS),
        UniverseStock("NHF", "nib Holdings", FS),
        UniverseStock("BEN", "Bendigo and Adelaide Bank", FS),
        UniverseStock("BOQ", "Bank of Queensland", FS),
        UniverseStock("AZJ", "Aurizon Holdings", IN),
        UniverseStock("QUB", "Qube Holdings", IN),
        UniverseStock("CWY", "Cleanaway Waste Management", IN),
        UniverseStock("SVW", "Seven Group Holdings", IN),
        UniverseStock("REH", "Reece", IN),
        UniverseStock("HVN", "Harvey Norman", CC),
        UniverseStock("SUL", "Super Retail Group", CC),
        UniverseStock("LOV", "Lovisa Holdings", CC),
        UniverseStock("PMV", "Premier Investments", CC),
        UniverseStock("BRG", "Breville Group", CC),
        UniverseStock("FLT", "Flight Centre Travel", CC),
        UniverseStock("WEB", "Web Travel Group", CC),
        UniverseStock("DMP", "Domino's Pizza Enterprises", CC),
        UniverseStock("EDV", "Endeavour Group", CD),
        UniverseStock("MTS", "Metcash", CD),
        UniverseStock("ELD", "Elders", CD),
        UniverseStock("GNC", "GrainCorp", CD),
        UniverseStock("IEL", "IDP Education", CD),
        UniverseStock("HUB", "HUB24", FS),
        UniverseStock("NWL", "Netwealth Group", FS),
        UniverseStock("AMP", "AMP", FS),
        UniverseStock("CGF", "Challenger", FS),
        UniverseStock("MFG", "Magellan Financial", FS),
        UniverseStock("PNI", "Pinnacle Investment Management", FS),
        UniverseStock("ZIP", "Zip Co", TE),
        UniverseStock("MP1", "Megaport", TE),
        UniverseStock("IRE", "IRESS", TE),
        UniverseStock("TPG", "TPG Telecom", CS),
        UniverseStock("NEC", "Nine Entertainment", CS),
        UniverseStock("CNU", "Chorus", CS),
        UniverseStock("RHC", "Ramsay Health Care", HC),
        UniverseStock("ANN", "Ansell", HC),
        UniverseStock("EBO", "EBOS Group", HC),
        UniverseStock("TLX", "Telix Pharmaceuticals", HC),
        UniverseStock("FPH", "Fisher & Paykel Healthcare", HC),
        UniverseStock("WHC", "Whitehaven Coal", EN),
        UniverseStock("YAL", "Yancoal Australia", EN),
        UniverseStock("PDN", "Paladin Energy", EN),
        UniverseStock("BOE", "Boss Energy", EN),
        UniverseStock("VEA", "Viva Energy", EN),
        UniverseStock("ALD", "Ampol", EN),
        UniverseStock("IGO", "IGO", BM),
        UniverseStock("RMS", "Ramelius Resources", BM),
        UniverseStock("PRU", "Perseus Mining", BM),
        UniverseStock("GMD", "Genesis Minerals", BM),
        UniverseStock("CIA", "Champion Iron", BM),
        UniverseStock("ILU", "Iluka Resources", BM),
        UniverseStock("IPL", "Incitec Pivot", BM),
        UniverseStock("NUF", "Nufarm", BM),
        UniverseStock("BKW", "Brickworks", BM),
    )

    private val byTicker = stocks.associateBy { it.ticker }

    fun find(tickerOrSymbol: String): UniverseStock? = byTicker[tickerOrSymbol.removeSuffix(".AX").uppercase()]

    fun forUniverse(u: Universe, watchlist: Collection<String>): List<UniverseStock> = when (u) {
        Universe.ASX50 -> stocks.filter { it.top50 }
        Universe.ASX100 -> stocks
        Universe.WATCHLIST -> watchlist.map { t -> find(t) ?: UniverseStock(t.removeSuffix(".AX"), t.removeSuffix(".AX"), "Other") }
    }

    /**
     * Approximate long-run ASX sector P/E ratios, used only when we can't compute a live median
     * from the screener (e.g. a single stock looked up on its own).
     */
    val fallbackSectorPe: Map<String, Double> = mapOf(
        BM to 13.0, FS to 16.0, HC to 30.0, EN to 12.0, RE to 17.0, CC to 22.0,
        CD to 22.0, IN to 22.0, TE to 40.0, CS to 22.0, UT to 18.0,
    )
    const val MARKET_PE = 17.0
}
