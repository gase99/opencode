package com.confighub.app.vpn

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of available proxy cores. A real core implementation registers itself here
 * (via DI) and [get] picks it by id; today only the honest mock is available.
 */
@Singleton
class CoreRegistry @Inject constructor(
    private val mock: MockProxyCore,
) {
    private val cores: Map<String, ProxyCore> = mapOf(mock.id to mock)

    /** The core used for sessions — defaults to the (only) registered core. */
    val defaultCoreId: String get() = mock.id

    fun get(id: String? = null): ProxyCore = id?.let { cores[it] } ?: mock

    fun available(): List<ProxyCore> = cores.values.toList()
}
