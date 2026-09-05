package com.rescuenet.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rescuenet.app.data.viewmodel.EmergencyReportViewModel
import com.rescuenet.app.data.viewmodel.HomeViewModel
import com.rescuenet.app.ui.screens.*

@Composable
fun RescueNetNavGraph(
    darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit,
    largeText: Boolean, onLargeTextChange: (Boolean) -> Unit,
    highContrast: Boolean, onHighContrastChange: (Boolean) -> Unit,
    backendBaseUrl: String, onBackendBaseUrlChange: (String) -> Unit,
) {
    val navController: NavHostController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val reportViewModel: EmergencyReportViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Destinations.Splash.route) {

        composable(Destinations.Splash.route) {
            SplashScreen(onFinished = {
                navController.navigate(Destinations.Onboarding.route) {
                    popUpTo(Destinations.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Destinations.Onboarding.route) {
            OnboardingScreen(onDone = {
                navController.navigate(Destinations.Home.route) {
                    popUpTo(Destinations.Onboarding.route) { inclusive = true }
                }
            })
        }

        composable(Destinations.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNeedHelp = {
                    reportViewModel.reset()
                    navController.navigate(Destinations.NeedHelpType.route)
                },
                onImSafe = { homeViewModel.markSafe() },
                onMyLocation = { navController.navigate(Destinations.NetworkStatus.route) },
                onNearbyHelp = { navController.navigate(Destinations.NearbyHelp.route) },
                onSafeRoute = { navController.navigate(Destinations.SafeRoute.route) },
                onNetworkStatus = { navController.navigate(Destinations.NetworkStatus.route) },
                onFamily = { navController.navigate(Destinations.Family.route) },
                onSettings = { navController.navigate(Destinations.Settings.route) },
                onDemoMode = { navController.navigate(Destinations.DemoMode.route) },
            )
        }

        composable(Destinations.NeedHelpType.route) {
            NeedHelpTypeScreen(
                viewModel = reportViewModel,
                onVoiceMode = { navController.navigate(Destinations.VoiceMode.route) },
                onNext = { navController.navigate(Destinations.NeedHelpDetails.route) },
            )
        }

        composable(Destinations.VoiceMode.route) {
            VoiceModeScreen(
                onTranscribed = { transcript ->
                    reportViewModel.setDescriptionFromVoice(transcript)
                    navController.navigate(Destinations.NeedHelpDetails.route) {
                        popUpTo(Destinations.NeedHelpType.route)
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(Destinations.NeedHelpDetails.route) {
            NeedHelpDetailsScreen(
                viewModel = reportViewModel,
                onNext = { navController.navigate(Destinations.NeedHelpMedia.route) },
            )
        }

        composable(Destinations.NeedHelpMedia.route) {
            val isOffline = !homeViewModel.networkStatus.value.internetConnected
            NeedHelpMediaScreen(
                viewModel = reportViewModel,
                isOffline = isOffline,
                onSend = { navController.navigate(Destinations.AiSummary.route) },
            )
        }

        composable(Destinations.AiSummary.route) {
            AiSummaryScreen(
                viewModel = reportViewModel,
                onContinue = { navController.navigate(Destinations.ReportStatus.route) },
            )
        }

        composable(Destinations.ReportStatus.route) {
            ReportStatusScreen(
                viewModel = reportViewModel,
                onDone = {
                    navController.navigate(Destinations.Home.route) {
                        popUpTo(Destinations.Home.route) { inclusive = true }
                    }
                },
            )
        }

        composable(Destinations.Family.route) {
            FamilyScreen(viewModel = homeViewModel, onAddContact = { /* Phase 3 */ })
        }

        composable(Destinations.NearbyHelp.route) {
            NearbyHelpScreen(viewModel = homeViewModel)
        }

        composable(Destinations.SafeRoute.route) {
            SafeRouteScreen()
        }

        composable(Destinations.NetworkStatus.route) {
            NetworkStatusScreen(viewModel = homeViewModel)
        }

        composable(Destinations.Settings.route) {
            SettingsScreen(
                darkMode = darkMode, onDarkModeChange = onDarkModeChange,
                largeText = largeText, onLargeTextChange = onLargeTextChange,
                highContrast = highContrast, onHighContrastChange = onHighContrastChange,
                backendBaseUrl = backendBaseUrl, onBackendBaseUrlChange = onBackendBaseUrlChange,
            )
        }

        composable(Destinations.DemoMode.route) {
            DemoModeScreen(homeViewModel = homeViewModel)
        }
    }
}
