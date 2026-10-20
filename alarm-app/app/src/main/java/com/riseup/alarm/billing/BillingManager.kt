package com.riseup.alarm.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One-time "Premium" in-app purchase via Google Play Billing.
 *
 * Create a one-time product with id [PREMIUM_PRODUCT_ID] in Play Console
 * (Monetize > Products > In-app products). For production, also verify purchase
 * tokens on your own server - this client-side check is fine to start with.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences("billing", Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _price = MutableStateFlow<String?>(null)
    /** Localised price string from Play, e.g. "$3.99". Null until loaded (or if billing is unavailable). */
    val price: StateFlow<String?> = _price

    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProduct()
                    refreshPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnects lazily on the next start()/buy() call.
            }
        })
    }

    /** Returns false if the store isn't reachable yet (no Play Store, offline, product not set up). */
    fun buy(activity: Activity): Boolean {
        val details = productDetails ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    fun refreshPurchases() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                setPremium(purchases.any { it.isPremium() })
                purchases.forEach(::acknowledgeIfNeeded)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase ->
                if (purchase.isPremium()) {
                    setPremium(true)
                    acknowledgeIfNeeded(purchase)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> Unit // cancelled or failed - nothing to do
        }
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = queryResult.productDetailsList.firstOrNull()
                _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        // Unacknowledged purchases are refunded automatically by Play after 3 days.
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { }
    }

    private fun Purchase.isPremium() =
        PREMIUM_PRODUCT_ID in products && purchaseState == Purchase.PurchaseState.PURCHASED

    private fun setPremium(value: Boolean) {
        _isPremium.value = value
        prefs.edit().putBoolean(KEY_PREMIUM, value).apply()
    }

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_unlock"
        private const val KEY_PREMIUM = "is_premium"
    }
}
