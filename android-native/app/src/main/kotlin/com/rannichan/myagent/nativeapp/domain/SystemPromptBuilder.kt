package com.rannichan.myagent.nativeapp.domain

import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.data.model.NpcProfile

object SystemPromptBuilder {
    fun build(mode: Mode, npc: NpcProfile?, agent: AgentProfile?, userText: String, thinkingEnabled: Boolean): String {
        return when (mode) {
            Mode.npc -> npc?.system_prompt.orEmpty()
            Mode.agent -> {
                val parts = listOf(
                    "# agent\n${agent?.agent.orEmpty()}".trim(),
                    "# identity\n${agent?.identity.orEmpty()}".trim(),
                    "# user\n$userText".trim(),
                    "# soul\n${agent?.soul.orEmpty()}".trim(),
                    "# memory\n${agent?.memory.orEmpty()}".trim()
                ).filter { it.isNotBlank() }
                if (thinkingEnabled) parts.joinToString("\n\n") else parts.joinToString("\n\n")
            }
        }
    }
}
