package com.confighub.app.data.sources

/**
 * Sample Source Registry entries — PLACEHOLDERS only.
 *
 * These point to non-functional example URLs and are imported DISABLED. The user must
 * replace them with sources they are legally allowed to use (docs/SOURCES_POLICY.md).
 * We intentionally do NOT ship live third-party subscription URLs.
 */
object SampleSources {

    data class Sample(
        val name: String,
        val url: String,
        val type: com.confighub.app.data.db.SourceType,
        val licenseNote: String,
    )

    val all: List<Sample> = listOf(
        Sample(
            name = "SAMPLE — raw text list (replace me)",
            url = "https://example.com/replace-with-your-lawful-source.txt",
            type = com.confighub.app.data.db.SourceType.SUBSCRIPTION_URL,
            licenseNote = "Placeholder. Replace with a source you are legally allowed to use. See docs/SOURCES_POLICY.md.",
        ),
        Sample(
            name = "SAMPLE — your own GitHub raw JSON",
            url = "https://raw.githubusercontent.com/your-account/your-repo/main/configs.json",
            type = com.confighub.app.data.db.SourceType.GITHUB_RAW,
            licenseNote = "Placeholder pointing to YOUR own public repository. Check the repo license.",
        ),
        Sample(
            name = "SAMPLE — JSON API",
            url = "https://example.com/api/v1/configs",
            type = com.confighub.app.data.db.SourceType.JSON_API,
            licenseNote = "Placeholder. Confirm the API terms allow automated access and respect rate limits.",
        ),
    )
}
