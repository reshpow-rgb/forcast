package com.riseup.alarm.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.riseup.alarm.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob + Google's consent SDK (UMP). Consent is required before showing ads to
 * users in the EEA/UK - configure the consent message in AdMob > Privacy & messaging.
 */
object Ads {

    private val initialized = AtomicBoolean(false)

    private fun consentInfo(context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** Shows the consent form if required, then initialises the Ads SDK if allowed. */
    fun gatherConsent(activity: Activity, onDone: () -> Unit = {}) {
        val info = consentInfo(activity)
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    initIfAllowed(activity)
                    onDone()
                }
            },
            {
                initIfAllowed(activity)
                onDone()
            },
        )
        // Consent from a previous session can be used straight away.
        initIfAllowed(activity)
    }

    fun canRequestAds(context: Context) = consentInfo(context).canRequestAds()

    fun privacyOptionsRequired(context: Context) =
        consentInfo(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }

    fun initIfAllowed(context: Context) {
        if (!canRequestAds(context)) return
        if (initialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext)
        }
    }
}

/** Adaptive banner. Renders nothing if consent doesn't allow ads. */
@Composable
fun BannerAd(modifier: Modifier = Modifier, adSize: AdSize? = null) {
    val context = LocalContext.current
    if (!Ads.canRequestAds(context)) return
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = maxWidth.value.toInt()
        val adView = remember(width, adSize) {
            AdView(context).apply {
                setAdSize(adSize ?: AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
                adUnitId = BuildConfig.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
        DisposableEffect(adView) { onDispose { adView.destroy() } }
        AndroidView(factory = { adView })
    }
}
