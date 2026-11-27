package com.confighub.app.core.parse

import com.confighub.app.core.model.ConfigProfile

/** A single-URI (or single-JSON-outbound) parser producing a [ConfigProfile]. */
interface LinkParser {
    fun parse(candidate: String): ConfigProfile?
}
