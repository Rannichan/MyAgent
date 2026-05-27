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
                    "# agent\n${agent?.agent_md.orEmpty()}".trim(),
                    "# identity\n${agent?.identity_md.orEmpty()}".trim(),
                    "# user\n$userText".trim(),
                    "# soul\n${agent?.soul_md.orEmpty()}".trim(),
                    "# memory\n${agent?.memory_md.orEmpty()}".trim()
                ).filter { it.isNotBlank() }
                parts.joinToString("\n\n")
            }
        }
    }
}
