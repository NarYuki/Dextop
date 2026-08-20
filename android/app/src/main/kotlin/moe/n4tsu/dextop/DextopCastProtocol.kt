package moe.n4tsu.dextop

object DextopCastProtocol {
    const val NAMESPACE = "urn:x-cast:moe.n4tsu.dextop"
    // CAF requires an app id while constructing CastContext. This value does
    // not discover receivers and is replaced by the registered id at build time.
    const val UNCONFIGURED_APP_ID = "00000000"
}
