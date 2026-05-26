package com.rannichan.myagent.nativeapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String,
    val npcId: String?,
    val agentId: String?,
    val messagesJson: String,
    val createdAt: Long,
    val updatedAt: Long
)
