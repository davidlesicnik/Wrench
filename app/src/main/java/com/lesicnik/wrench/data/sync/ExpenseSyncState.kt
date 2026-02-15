package com.lesicnik.wrench.data.sync

enum class ExpenseSyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    SYNC_ERROR,
    CONFLICT
}

enum class SyncOperationType {
    CREATE,
    UPDATE,
    DELETE
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val pendingOperations: Int = 0,
    val lastError: String? = null,
    val lastSyncAt: Long? = null
)
