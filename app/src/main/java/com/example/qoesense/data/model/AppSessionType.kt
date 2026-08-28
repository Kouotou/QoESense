package com.example.qoesense.data.model

import kotlinx.serialization.Serializable

/** What kind of OTT session, if any, the foregrounded app represents for QoE labelling. */
@Serializable
enum class AppSessionType {
    NONE,
    SOCIAL_MEDIA,
    VIDEO_CONFERENCING,
}

/**
 * Known package names for the app classes this study cares about. WhatsApp appears in both
 * sets: by default it's treated as social media (messaging/feed), but [AppUsageMonitor]
 * reclassifies it as VIDEO_CONFERENCING when a call is detected to be active, since a plain
 * foreground-package check can't otherwise distinguish a WhatsApp call from WhatsApp chat -
 * they're the same package.
 */
object MonitoredApps {
    val SOCIAL_MEDIA_PACKAGES = setOf(
        "com.whatsapp",
        "com.facebook.katana",
        "com.instagram.android",
    )

    val VIDEO_CONFERENCING_PACKAGES = setOf(
        "us.zoom.videomeetings",
        "com.google.android.apps.meetings",
        "com.google.android.apps.classroom",
    )

    const val WHATSAPP_PACKAGE = "com.whatsapp"
}
