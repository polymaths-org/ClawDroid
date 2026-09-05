package com.clawdroid.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.clawdroid.app.core.config.AppConfigManager
import com.clawdroid.app.ui.chat.ChatScreen
import com.clawdroid.app.ui.settings.AgentConfigScreen
import com.clawdroid.app.ui.settings.AudioConfigScreen
import com.clawdroid.app.ui.settings.AutomationsConfigScreen
import com.clawdroid.app.ui.settings.ChannelsConfigScreen
import com.clawdroid.app.ui.settings.ConfigEditorScreen
import com.clawdroid.app.ui.settings.ConfigFileType
import com.clawdroid.app.ui.settings.McpConfigScreen
import com.clawdroid.app.ui.settings.SettingsScreen
import com.clawdroid.app.ui.settings.SkillsConfigScreen
import com.clawdroid.app.ui.setup.SetupScreen
import com.clawdroid.app.ui.setup.PostSetupScreen
import com.clawdroid.app.ui.splash.SplashScreen
import com.clawdroid.app.ui.splash.HatchingScreen
import com.clawdroid.app.ui.terminal.TerminalScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route,
        enterTransition = {
            slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(250))
        },
        exitTransition = {
            slideOutHorizontally(tween(250)) { -it / 4 } + fadeOut(tween(200))
        },
        popEnterTransition = {
            slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(250))
        },
        popExitTransition = {
            slideOutHorizontally(tween(250)) { it / 4 } + fadeOut(tween(200))
        },
    ) {
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    if (AppConfigManager.isOnboardingComplete) {
                        if (AppConfigManager.hasSeenHatching) {
                            navController.navigate(NavRoutes.Chat.route) {
                                popUpTo(NavRoutes.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(NavRoutes.Hatching.route) {
                                popUpTo(NavRoutes.Splash.route) { inclusive = true }
                            }
                        }
                    } else {
                        navController.navigate(NavRoutes.Setup.route) {
                            popUpTo(NavRoutes.Splash.route) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(NavRoutes.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(NavRoutes.Hatching.route) {
                        popUpTo(NavRoutes.Setup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.Hatching.route) {
            HatchingScreen(
                onComplete = {
                    navController.navigate(NavRoutes.PostSetup.route) {
                        popUpTo(NavRoutes.Hatching.route) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.PostSetup.route) {
            PostSetupScreen(
                onComplete = {
                    navController.navigate(NavRoutes.Chat.route) {
                        popUpTo(NavRoutes.PostSetup.route) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) },
                onNavigateToAudio = { navController.navigate(NavRoutes.SettingsVoice.route) },
                onNavigateToAutomations = { navController.navigate(NavRoutes.SettingsAutomations.route) },
                onNavigateToChannels = { navController.navigate(NavRoutes.SettingsChannels.route) },
                onNavigateToSkills = { navController.navigate(NavRoutes.SettingsSkills.route) },
                onNavigateToMcp = { navController.navigate(NavRoutes.SettingsMCP.route) },
                onNavigateToAgentConfig = { navController.navigate(NavRoutes.SettingsAgent.route) },
                onNavigateToTerminal = { navController.navigate(NavRoutes.Terminal.route) },
            )
        }

        composable(NavRoutes.Terminal.route) {
            TerminalScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToConfigEditor = { fileType ->
                    navController.navigate(NavRoutes.ConfigEditor.create(fileType.name))
                },
            )
        }

        composable(NavRoutes.SettingsVoice.route) {
            AudioConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SettingsAgent.route) {
            AgentConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SettingsAutomations.route) {
            AutomationsConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SettingsChannels.route) {
            ChannelsConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SettingsSkills.route) {
            SkillsConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.SettingsMCP.route) {
            McpConfigScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.ConfigEditor.route) { backStackEntry ->
            val fileTypeName = backStackEntry.arguments?.getString("fileType") ?: "AGENTS"
            val fileType = try {
                ConfigFileType.valueOf(fileTypeName)
            } catch (_: Exception) {
                ConfigFileType.AGENTS
            }
            ConfigEditorScreen(
                fileType = fileType,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
