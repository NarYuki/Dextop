package moe.n4tsu.dextop

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Google Cast configuration; Miracast remains handled by the platform/DeX. */
class DextopCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val appId = BuildConfig.CAST_RECEIVER_APP_ID
        return CastOptions.Builder()
            .setReceiverApplicationId(appId.ifBlank { DextopCastProtocol.UNCONFIGURED_APP_ID })
            .setSupportedNamespaces(listOf(DextopCastProtocol.NAMESPACE))
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
