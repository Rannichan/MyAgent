package com.rannichan.myagent.nativeapp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rannichan.myagent.nativeapp.ui.screens.AgentDetailScreen
import com.rannichan.myagent.nativeapp.ui.screens.AgentListScreen
import com.rannichan.myagent.nativeapp.ui.screens.ConversationScreen
import com.rannichan.myagent.nativeapp.ui.screens.ConversationsScreen
import com.rannichan.myagent.nativeapp.ui.screens.LlmSettingsScreen
import com.rannichan.myagent.nativeapp.ui.screens.NpcDetailScreen
import com.rannichan.myagent.nativeapp.ui.screens.NpcListScreen
import com.rannichan.myagent.nativeapp.ui.screens.SettingsMainScreen
import com.rannichan.myagent.nativeapp.ui.screens.UserSettingsScreen

object Routes {
    const val CONVERSATIONS = "conversations"
    const val CONVERSATION = "conversation/{conversationId}"
    const val SETTINGS = "settings"
    const val SETTINGS_NPC = "settings/npc"
    const val SETTINGS_NPC_DETAIL = "settings/npc/{npcId}"
    const val SETTINGS_AGENT = "settings/agent"
    const val SETTINGS_AGENT_DETAIL = "settings/agent/{agentId}"
    const val SETTINGS_USER = "settings/user"
    const val SETTINGS_LLM = "settings/llm"

    fun conversation(id: String) = "conversation/$id"
    fun npcDetail(id: String) = "settings/npc/$id"
    fun agentDetail(id: String) = "settings/agent/$id"
}

private val TOP_LEVEL_ROUTES = listOf(Routes.CONVERSATIONS, Routes.SETTINGS)

@Composable
fun AppScaffold(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute?.startsWith("conversation/") != true
    val dark = isSystemInDarkTheme()

    AppTheme(mode = state.mode, darkTheme = dark) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == Routes.CONVERSATIONS
                            } == true,
                            onClick = {
                                navController.navigate(Routes.CONVERSATIONS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
                            label = { Text("对话") }
                        )
                        NavigationBarItem(
                            selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route?.startsWith("settings") == true
                            } == true,
                            onClick = {
                                navController.navigate(Routes.SETTINGS) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            label = { Text("设置") }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.CONVERSATIONS,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Routes.CONVERSATIONS) {
                    ConversationsScreen(
                        state = state,
                        vm = vm,
                        onOpenConversation = { id ->
                            vm.selectConversation(id)
                            navController.navigate(Routes.conversation(id))
                        }
                    )
                }
                composable(Routes.CONVERSATION) { backStack ->
                    val conversationId = backStack.arguments?.getString("conversationId") ?: return@composable
                    ConversationScreen(
                        conversationId = conversationId,
                        state = state,
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsMainScreen(
                        onNpc = { navController.navigate(Routes.SETTINGS_NPC) },
                        onAgent = { navController.navigate(Routes.SETTINGS_AGENT) },
                        onUser = { navController.navigate(Routes.SETTINGS_USER) },
                        onLlm = { navController.navigate(Routes.SETTINGS_LLM) }
                    )
                }
                composable(Routes.SETTINGS_NPC) {
                    NpcListScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { id -> navController.navigate(Routes.npcDetail(id)) },
                        onNew = { navController.navigate(Routes.npcDetail("__new__")) },
                        onDelete = vm::deleteNpc
                    )
                }
                composable(Routes.SETTINGS_NPC_DETAIL) { backStack ->
                    val npcId = backStack.arguments?.getString("npcId") ?: return@composable
                    NpcDetailScreen(
                        npcId = npcId,
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSave = { profile ->
                            vm.saveNpc(profile)
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.SETTINGS_AGENT) {
                    AgentListScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { id -> navController.navigate(Routes.agentDetail(id)) },
                        onNew = { navController.navigate(Routes.agentDetail("__new__")) },
                        onDelete = vm::deleteAgent
                    )
                }
                composable(Routes.SETTINGS_AGENT_DETAIL) { backStack ->
                    val agentId = backStack.arguments?.getString("agentId") ?: return@composable
                    AgentDetailScreen(
                        agentId = agentId,
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSave = { profile ->
                            vm.saveAgent(profile)
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.SETTINGS_USER) {
                    UserSettingsScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSave = vm::saveUser
                    )
                }
                composable(Routes.SETTINGS_LLM) {
                    LlmSettingsScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSave = vm::saveLlmConfig,
                        onSetModel = vm::setModel
                    )
                }
            }
        }
    }
}

