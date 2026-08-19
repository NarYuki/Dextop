package moe.n4tsu.cardex

/** Distinguishes Android's configuration recreation from a lost car session. */
internal object CarCompanionLifecyclePolicy {
    fun isGracefulActivityDestruction(isChangingConfigurations: Boolean): Boolean =
        isChangingConfigurations
}
