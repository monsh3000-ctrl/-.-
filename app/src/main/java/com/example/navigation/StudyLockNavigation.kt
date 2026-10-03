package com.example.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.core.datastore.StudyLockPreferences
import com.example.core.model.QuizLength
import com.example.core.model.StudyMode
import com.example.feature.decks.DecksScreen
import com.example.feature.history.HistoryScreen
import com.example.feature.home.HomeScreen
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.scanner.OcrReviewScreen
import com.example.feature.scanner.ScannerScreen
import com.example.feature.scanner.ScannerViewModel
import com.example.feature.screentime.BlockedAppsScreen
import com.example.feature.screentime.ScreenTimeBankScreen
import com.example.feature.screentime.ScreenTimeViewModel
import com.example.feature.settings.SettingsScreen
import com.example.feature.study.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String = "", val icon: ImageVector? = null) {
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object StudyModeSelect : Screen("study_mode_select", "Study", Icons.Default.School)
    data object Scanner : Screen("scanner")
    data object OcrReview : Screen("ocr_review")
    data object Quiz : Screen("quiz")
    data object Flashcards : Screen("flashcards")
    data object Results : Screen("results")
    data object Decks : Screen("decks", "Decks", Icons.Default.MenuBook)
    data object FocusTimer : Screen("focus_timer", "Focus", Icons.Default.Timer)
    data object Bank : Screen("bank", "Bank", Icons.Default.AccountBalanceWallet)
    data object BlockedApps : Screen("blocked_apps")
    data object History : Screen("history")
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object SchoolMaterials : Screen("school_materials", "Materials")
    data object PostStudySummary : Screen("post_study_summary/{durationSeconds}/{subject}/{earnedSeconds}") {
        fun createRoute(durationSeconds: Long, subject: String, earnedSeconds: Long): String {
            val encodedSubject = android.net.Uri.encode(subject)
            return "post_study_summary/$durationSeconds/$encodedSubject/$earnedSeconds"
        }
    }
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.FocusTimer,
    Screen.StudyModeSelect,
    Screen.Decks,
    Screen.Bank,
    Screen.Settings
)

@Composable
fun StudyLockApp(
    initialDestination: String? = null,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val preferences = remember { StudyLockPreferences(context) }
    val coroutineScope = rememberCoroutineScope()

    val isOnboardingDone by preferences.isOnboardingCompleted.collectAsState(initial = null)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val scannerViewModel: ScannerViewModel = viewModel()
    val studyViewModel: StudyViewModel = viewModel()
    val screenTimeViewModel: ScreenTimeViewModel = viewModel()

    if (isOnboardingDone == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        )
        return
    }

    val startDestination = when {
        initialDestination == "FOCUS_TIMER" -> Screen.FocusTimer.route
        initialDestination == "STUDY_STUDIO" -> Screen.StudyModeSelect.route
        !isOnboardingDone!! -> Screen.Onboarding.route
        else -> Screen.Home.route
    }

    val showBottomBar = currentRoute in bottomNavScreens.map { it.route }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SlateSurface,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_bottom_nav")
                ) {
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon ?: Icons.Default.Circle,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = ElectricIndigoLight,
                                indicatorColor = ElectricIndigo,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinished = {
                        coroutineScope.launch {
                            preferences.setOnboardingCompleted(true)
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onStartStudy = {
                        navController.navigate(Screen.StudyModeSelect.route)
                    },
                    onNavigateToDecks = {
                        navController.navigate(Screen.Decks.route)
                    },
                    onNavigateToBank = {
                        navController.navigate(Screen.Bank.route)
                    },
                    onNavigateToBlockedApps = {
                        navController.navigate(Screen.BlockedApps.route)
                    },
                    onNavigateToFocusTimer = {
                        navController.navigate(Screen.FocusTimer.route)
                    },
                    onResumeSession = { sessionId, deckId, mode ->
                        studyViewModel.resumeExistingSession(
                            sessionId = sessionId,
                            deckId = deckId,
                            mode = if (mode == StudyMode.FLASHCARDS.name) StudyMode.FLASHCARDS else StudyMode.QUIZ
                        ) {
                            if (mode == StudyMode.FLASHCARDS.name) {
                                navController.navigate(Screen.Flashcards.route)
                            } else {
                                navController.navigate(Screen.Quiz.route)
                            }
                        }
                    }
                )
            }

            composable(Screen.FocusTimer.route) {
                com.example.feature.focustimer.FocusTimerScreen(
                    onNavigateBack = { navController.navigate(Screen.Home.route) },
                    onNavigateToBank = { navController.navigate(Screen.Bank.route) },
                    onNavigateToSummary = { duration, subject, earned ->
                        navController.navigate(Screen.PostStudySummary.createRoute(duration, subject, earned))
                    }
                )
            }

            composable(
                route = Screen.PostStudySummary.route,
                arguments = listOf(
                    navArgument("durationSeconds") { type = NavType.LongType },
                    navArgument("subject") { type = NavType.StringType },
                    navArgument("earnedSeconds") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val duration = backStackEntry.arguments?.getLong("durationSeconds") ?: 0L
                val subject = backStackEntry.arguments?.getString("subject") ?: "General Study"
                val earned = backStackEntry.arguments?.getLong("earnedSeconds") ?: 0L
                com.example.feature.focustimer.PostStudySummaryScreen(
                    durationSeconds = duration,
                    subject = subject,
                    earnedSeconds = earned,
                    onNavigateHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onNavigateToBank = {
                        navController.navigate(Screen.Bank.route)
                    },
                    onNavigateToDecks = {
                        navController.navigate(Screen.Decks.route)
                    }
                )
            }

            composable(Screen.StudyModeSelect.route) {
                StudyModeSelectScreen(
                    initialDeckId = null,
                    onNavigateBack = {
                        navController.navigateUp()
                    },
                    onNavigateToFocusTimer = {
                        navController.navigate(Screen.FocusTimer.route)
                    },
                    onStartSession = { deckId, mode, length ->
                        studyViewModel.startNewSession(deckId, mode, length) {
                            if (mode == StudyMode.FLASHCARDS) {
                                navController.navigate(Screen.Flashcards.route)
                            } else {
                                navController.navigate(Screen.Quiz.route)
                            }
                        }
                    }
                )
            }

            composable(Screen.Scanner.route) {
                ScannerScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onProceedToReview = { navController.navigate(Screen.OcrReview.route) },
                    viewModel = scannerViewModel
                )
            }

            composable(Screen.OcrReview.route) {
                val extractedText by scannerViewModel.extractedText.collectAsState()
                OcrReviewScreen(
                    extractedText = extractedText,
                    onNavigateBack = { navController.navigateUp() },
                    onProceedToStudy = { deckId ->
                        studyViewModel.startNewSession(deckId, StudyMode.QUIZ, QuizLength.STANDARD) {
                            navController.navigate(Screen.Quiz.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                )
            }

            composable(Screen.Quiz.route) {
                QuizScreen(
                    onNavigateBack = { navController.navigate(Screen.Home.route) },
                    onCompleted = { navController.navigate(Screen.Results.route) },
                    viewModel = studyViewModel
                )
            }

            composable(Screen.Flashcards.route) {
                FlashcardsScreen(
                    onNavigateBack = { navController.navigate(Screen.Home.route) },
                    onCompleted = { navController.navigate(Screen.Results.route) },
                    viewModel = studyViewModel
                )
            }

            composable(Screen.Results.route) {
                ResultsScreen(
                    onReturnHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    viewModel = studyViewModel
                )
            }

            composable(Screen.Decks.route) {
                DecksScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onStudyDeck = { deckId ->
                        studyViewModel.startNewSession(deckId, StudyMode.QUIZ, QuizLength.STANDARD) {
                            navController.navigate(Screen.Quiz.route)
                        }
                    },
                    onScanNewDeck = {
                        navController.navigate(Screen.Scanner.route)
                    },
                    onViewSchoolMaterials = {
                        navController.navigate(Screen.SchoolMaterials.route)
                    }
                )
            }

            composable(Screen.SchoolMaterials.route) {
                com.example.feature.school.SchoolMaterialsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onStudyDeckCreated = { deckId ->
                        studyViewModel.startNewSession(deckId, StudyMode.QUIZ, QuizLength.STANDARD) {
                            navController.navigate(Screen.Quiz.route)
                        }
                    }
                )
            }

            composable(Screen.Bank.route) {
                ScreenTimeBankScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToBlockedApps = { navController.navigate(Screen.BlockedApps.route) },
                    viewModel = screenTimeViewModel
                )
            }

            composable(Screen.BlockedApps.route) {
                BlockedAppsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    viewModel = screenTimeViewModel
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.navigateUp() },
                    onManageSchoolMaterials = {
                        navController.navigate(Screen.SchoolMaterials.route)
                    }
                )
            }
        }
    }
}
