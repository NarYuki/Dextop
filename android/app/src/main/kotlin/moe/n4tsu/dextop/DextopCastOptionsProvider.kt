package moe.n4tsu.dextop

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Google Cast configuration; Miracast remains handled by the platform/DeX. */
class DextopCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val mode = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            .getString("flutter.cast_mode", "simple") ?: "simple"
        val compatibilityMode = mode != "receiver"
        val appId = if (compatibilityMode) {
            com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
        } else {
            BuildConfig.CAST_RECEIVER_APP_ID.ifBlank { DextopCastProtocol.UNCONFIGURED_APP_ID }
        }
        val builder = CastOptions.Builder().setReceiverApplicationId(appId)
        if (!compatibilityMode) {
            builder.setSupportedNamespaces(listOf(DextopCastProtocol.NAMESPACE))
        }
        OperationLog.i(
            context,
            "Cast",
            "CastOptions initialized mode=$mode receiverAppId=$appId"
        )
        return builder.build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
