package com.confighub.app.domain.operator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OperatorCatalogTest {

    @Test
    fun `plmn codes map to the right operators`() {
        assertThat(OperatorCatalog.fromPlmn("43211")).isEqualTo(OperatorId.MCI)
        assertThat(OperatorCatalog.fromPlmn("43219")).isEqualTo(OperatorId.MCI)
        assertThat(OperatorCatalog.fromPlmn("43235")).isEqualTo(OperatorId.IRANCELL)
        assertThat(OperatorCatalog.fromPlmn("43220")).isEqualTo(OperatorId.RIGHTEL)
    }

    @Test
    fun `unknown or blank plmn returns null`() {
        assertThat(OperatorCatalog.fromPlmn("99999")).isNull()
        assertThat(OperatorCatalog.fromPlmn("")).isNull()
        assertThat(OperatorCatalog.fromPlmn(null)).isNull()
    }

    @Test
    fun `every operator has a default profile with thresholds`() {
        for (id in OperatorId.entries) {
            val profile = OperatorCatalog.profile(id)
            assertThat(profile.thresholds.goodBelowMs).isAtLeast(50)
            assertThat(profile.thresholds.acceptableBelowMs).isGreaterThan(profile.thresholds.goodBelowMs)
        }
    }

    @Test
    fun `operator display names are non-blank`() {
        for (id in OperatorId.entries) {
            assertThat(id.displayName).isNotEmpty()
        }
    }
}
