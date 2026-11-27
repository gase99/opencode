package com.confighub.app.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlRedactorTest {

    @Test
    fun `keeps normal path segments`() {
        val out = UrlRedactor.redact("https://example.com/raw/subfile.txt")
        assertThat(out).isEqualTo("https://example.com/raw/subfile.txt")
    }

    @Test
    fun `strips query and fragment`() {
        val out = UrlRedactor.redact("https://example.com/api/configs?token=abcdef&x=1#frag")
        assertThat(out).isEqualTo("https://example.com/api/configs")
    }

    @Test
    fun `replaces token-like path segments`() {
        val out = UrlRedactor.redact("https://sub.example.com/api/AbcDef123456789012345/configs")
        assertThat(out).doesNotContain("AbcDef123456789012345")
        assertThat(out).contains("<token>")
    }

    @Test
    fun `drops userinfo`() {
        val out = UrlRedactor.redact("https://alice:hunter2@example.com/sub")
        assertThat(out).doesNotContain("alice")
        assertThat(out).doesNotContain("hunter2")
        assertThat(out).isEqualTo("https://example.com/sub")
    }

    @Test
    fun `keeps explicit port`() {
        val out = UrlRedactor.redact("https://example.com:8443/x")
        assertThat(out).isEqualTo("https://example.com:8443/x")
    }

    @Test
    fun `unparseable urls are fully hidden`() {
        assertThat(UrlRedactor.redact("not a url")).isEqualTo("<unparseable-url>")
        assertThat(UrlRedactor.redact("::::")).isEqualTo("<unparseable-url>")
    }
}
