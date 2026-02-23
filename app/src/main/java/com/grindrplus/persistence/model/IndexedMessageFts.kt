package com.grindrplus.persistence.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(contentEntity = IndexedMessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity
data class IndexedMessageFts(
    val body: String
)
