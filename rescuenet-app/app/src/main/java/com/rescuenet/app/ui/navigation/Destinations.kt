package com.rescuenet.app.ui.navigation

/** Central route registry — matches the Phase 1 Screen Map (Part 5) 1:1 so nothing drifts. */
sealed class Destinations(val route: String) {
    object Splash : Destinations("splash")
    object Onboarding : Destinations("onboarding")
    object Home : Destinations("home")

    object NeedHelpType : Destinations("need_help/type")
    object NeedHelpDetails : Destinations("need_help/details")
    object NeedHelpMedia : Destinations("need_help/media")
    object AiSummary : Destinations("need_help/ai_summary")
    object ReportStatus : Destinations("need_help/status")

    object VoiceMode : Destinations("voice_mode")

    object Family : Destinations("family")
    object NearbyHelp : Destinations("nearby_help")
    object SafeRoute : Destinations("safe_route")
    object OfflineMaps : Destinations("offline_maps")
    object NetworkStatus : Destinations("network_status")
    object Alerts : Destinations("alerts")
    object Settings : Destinations("settings")
    object DemoMode : Destinations("demo_mode")
}
