package com.rannichan.myagent.nativeapp.data.local

import android.content.Context
import androidx.room.Room
import com.rannichan.myagent.nativeapp.data.model.AgentProfile
import com.rannichan.myagent.nativeapp.data.model.AppearanceSettings
import com.rannichan.myagent.nativeapp.data.model.ChatMessage
import com.rannichan.myagent.nativeapp.data.model.Conversation
import com.rannichan.myagent.nativeapp.data.model.LlmConfig
import com.rannichan.myagent.nativeapp.data.model.Mode
import com.rannichan.myagent.nativeapp.data.model.NpcProfile
import com.rannichan.myagent.nativeapp.data.model.UserConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class LocalStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "myagent_native.db"
    ).build()

    private val root = File(context.filesDir, "myagent").apply { mkdirs() }
    private val npcDir = File(root, "npc").apply { mkdirs() }
    private val agentDir = File(root, "agent/profiles").apply { mkdirs() }
    private val userFile = File(root, "agent/user.md")
    private val llmFile = File(root, "config/llm.json").apply { parentFile?.mkdirs() }
    private val appearanceFile = File(root, "config/appearance.json").apply { parentFile?.mkdirs() }

    suspend fun listConversations(): List<Conversation> = withContext(Dispatchers.IO) {
        db.conversationDao().list().map { it.toModel(json) }
    }

    suspend fun saveConversation(conversation: Conversation): Conversation = withContext(Dispatchers.IO) {
        db.conversationDao().upsert(conversation.toEntity(json))
        conversation
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        db.conversationDao().delete(id)
    }

    suspend fun listNpcs(): List<NpcProfile> = withContext(Dispatchers.IO) {
        npcDir.listFiles()?.filter { it.isDirectory }?.map { dir ->
            NpcProfile(
                id = dir.name,
                name = dir.name,
                system_prompt = File(dir, "system.md").takeIf { it.exists() }?.readText() ?: "",
                opening = File(dir, "opening.md").takeIf { it.exists() }?.readText()?.ifBlank { null }
            )
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    suspend fun saveNpc(profile: NpcProfile) = withContext(Dispatchers.IO) {
        val dir = File(npcDir, profile.id).apply { mkdirs() }
        File(dir, "system.md").writeText(profile.system_prompt)
        val opening = File(dir, "opening.md")
        if ((profile.opening ?: "").isBlank()) opening.delete()
        else opening.writeText(profile.opening!!)
    }

    suspend fun deleteNpc(id: String) = withContext(Dispatchers.IO) {
        File(npcDir, id).deleteRecursively()
    }

    suspend fun listAgents(): List<AgentProfile> = withContext(Dispatchers.IO) {
        agentDir.listFiles()?.filter { it.isDirectory }?.map { dir ->
            AgentProfile(
                id = dir.name,
                name = dir.name,
                agent_md = File(dir, "agent.md").takeIf { it.exists() }?.readText() ?: "",
                identity_md = File(dir, "identity.md").takeIf { it.exists() }?.readText() ?: "",
                soul_md = File(dir, "soul.md").takeIf { it.exists() }?.readText() ?: "",
                memory_md = File(dir, "memory.md").takeIf { it.exists() }?.readText() ?: ""
            )
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    suspend fun saveAgent(profile: AgentProfile) = withContext(Dispatchers.IO) {
        val dir = File(agentDir, profile.id).apply { mkdirs() }
        File(dir, "agent.md").writeText(profile.agent_md)
        File(dir, "identity.md").writeText(profile.identity_md)
        File(dir, "soul.md").writeText(profile.soul_md)
        File(dir, "memory.md").writeText(profile.memory_md)
    }

    suspend fun deleteAgent(id: String) = withContext(Dispatchers.IO) {
        File(agentDir, id).deleteRecursively()
    }

    suspend fun loadUser(): UserConfig = withContext(Dispatchers.IO) {
        UserConfig(if (userFile.exists()) userFile.readText() else "")
    }

    suspend fun saveUser(content: String) = withContext(Dispatchers.IO) {
        userFile.parentFile?.mkdirs()
        userFile.writeText(content)
    }

    suspend fun loadLlmConfig(): LlmConfig = withContext(Dispatchers.IO) {
        if (!llmFile.exists()) return@withContext LlmConfig()
        runCatching { json.decodeFromString<LlmConfig>(llmFile.readText()) }.getOrElse { LlmConfig() }
    }

    suspend fun saveLlmConfig(config: LlmConfig) = withContext(Dispatchers.IO) {
        llmFile.writeText(json.encodeToString(config))
    }

    suspend fun loadAppearance(): AppearanceSettings = withContext(Dispatchers.IO) {
        if (!appearanceFile.exists()) return@withContext AppearanceSettings()
        runCatching { json.decodeFromString<AppearanceSettings>(appearanceFile.readText()) }
            .getOrElse { AppearanceSettings() }
    }

    suspend fun saveAppearance(settings: AppearanceSettings) = withContext(Dispatchers.IO) {
        appearanceFile.writeText(json.encodeToString(settings))
    }

    private fun Conversation.toEntity(json: Json): ConversationEntity = ConversationEntity(
        id = id,
        title = title,
        mode = mode.name,
        npcId = npc_id,
        agentId = agent_id,
        messagesJson = json.encodeToString(messages),
        createdAt = created_at,
        updatedAt = updated_at
    )

    private fun ConversationEntity.toModel(json: Json): Conversation = Conversation(
        id = id,
        title = title,
        mode = if (mode == Mode.npc.name) Mode.npc else Mode.agent,
        npc_id = npcId,
        agent_id = agentId,
        messages = runCatching { json.decodeFromString<List<ChatMessage>>(messagesJson) }.getOrElse { emptyList() },
        created_at = createdAt,
        updated_at = updatedAt
    )
}
