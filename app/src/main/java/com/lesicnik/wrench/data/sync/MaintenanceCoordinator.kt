package com.lesicnik.wrench.data.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.lesicnik.wrench.data.local.WrenchDatabase
import com.lesicnik.wrench.data.local.entity.*
import com.lesicnik.wrench.data.remote.records.Expense
import com.lesicnik.wrench.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All entry points share the same write lock, including edits/cancellation of queued intents. */
class MaintenanceCoordinator(
    private val database: WrenchDatabase,
    private val remote: MaintenanceRemote = PublicMaintenanceRemote()
) {
    private val dao = database.maintenanceDao()
    private val gson = Gson()
    private fun encode(values: ReminderValues) = gson.toJson(values)
    fun values(json: String): ReminderValues = gson.fromJson(json, ReminderValues::class.java)

    fun observe(c: Credentials, vehicleId: Int) = combine(
        dao.observeReminders(ReminderRepository.accountKey(c), vehicleId),
        dao.observeUploads(ReminderRepository.accountKey(c), vehicleId)
    ) { reminders, uploads ->
        (reminders.map { it.reminderId } + uploads.map { it.reminderId }).distinct().associateWith { id ->
            MaintenanceState(reminders.firstOrNull { it.reminderId == id }, uploads.lastOrNull { it.reminderId == id })
        }
    }

    suspend fun recordService(c: Credentials, original: Reminder, expense: ExpenseEntity, target: ReminderValues?, remove: Boolean): Int = writeMutex.withLock {
        require(expense.vehicleRemoteId == original.vehicleId)
        require(target == null || !remove)
        require(expense.type == "SERVICE") { "Record this work as a service." }
        require(expense.cost.isFinite() && expense.cost > 0)
        target?.draft()?.validationError()?.let { error(it) }
        database.withTransaction {
            val key = ReminderRepository.accountKey(c)
            check(dao.uploads(key).none { it.vehicleId == original.vehicleId && it.reminderId == original.id && it.status !in MaintenanceState.terminal }) {
                "This reminder already has a service waiting for upload. Review it first."
            }
            checkAvailable(key, original)
            val id = database.expenseDao().insertExpense(expense.copy(serverUrl = c.serverUrl, accountKey = key)).toInt()
            dao.save(ServiceUploadEntity(id, key, c.serverUrl, original.vehicleId, original.id))
            if (target != null || remove) saveIntent(c, original, target, id)
            id
        }
    }

    suspend fun queue(c: Credentials, original: Reminder, target: ReminderValues?) = writeMutex.withLock {
        target?.draft()?.validationError()?.let { error(it) }
        database.withTransaction {
            val key = ReminderRepository.accountKey(c)
            val old = dao.reminders(key).firstOrNull { it.vehicleId == original.vehicleId && it.reminderId == original.id }
            check(old == null || old.status in MaintenanceState.terminal || old.status == "QUEUED") { "Review the pending change before editing this reminder." }
            // Editing an unsent intent retains its original conflict baseline and dependency.
            if (old?.status == "QUEUED") dao.save(old.copy(targetJson = target?.let(::encode), error = null))
            else saveIntent(c, original, target, null)
        }
    }

    private suspend fun checkAvailable(key: String, r: Reminder) {
        check(dao.reminders(key).none { it.vehicleId == r.vehicleId && it.reminderId == r.id && it.status !in MaintenanceState.terminal }) {
            "This reminder already has a pending change. Edit or cancel that change first."
        }
    }
    private suspend fun saveIntent(c: Credentials, original: Reminder, target: ReminderValues?, expenseId: Int?) {
        dao.save(ReminderOperationEntity(ReminderRepository.accountKey(c), c.serverUrl, original.vehicleId, original.id,
            encode(ReminderValues.from(original)), target?.let(::encode), expenseId))
    }

    suspend fun cancel(c: Credentials, vehicleId: Int, reminderId: Int) = writeMutex.withLock {
        val op = dao.reminders(ReminderRepository.accountKey(c)).firstOrNull { it.vehicleId == vehicleId && it.reminderId == reminderId } ?: return@withLock
        check(op.status == "QUEUED" || op.status == "CONFLICT" || op.status == "PAUSED") { "This change has already been sent. Sync to verify its outcome first." }
        dao.save(op.copy(status = "CANCELLED", error = null))
    }

    suspend fun resolve(c: Credentials, vehicleId: Int, reminderId: Int, applyMine: Boolean) = writeMutex.withLock {
        val op = dao.reminders(ReminderRepository.accountKey(c)).first { it.vehicleId == vehicleId && it.reminderId == reminderId }
        check(op.status == "CONFLICT" || op.status == "PAUSED")
        dao.save(op.copy(status = if (applyMine) "QUEUED" else "CANCELLED", force = applyMine, error = null))
    }

    suspend fun matchingServices(c: Credentials, expenseId: Int): List<Expense> {
        val upload = requireNotNull(dao.upload(expenseId))
        require(upload.accountKey == ReminderRepository.accountKey(c))
        val local = requireNotNull(database.expenseDao().getByLocalId(expenseId))
        return remote.services(c, upload.vehicleId).filter { it.date.toString() == local.date && it.odometer == local.odometer &&
            it.description.trim() == local.description.trim() && kotlin.math.abs(it.cost - local.cost) < 0.005 }
    }

    /** A retry is only reachable after an explicit review; an uncertain POST is never replayed by sync. */
    suspend fun reviewUpload(c: Credentials, expenseId: Int, matchedId: Int?) = writeMutex.withLock {
        val upload = requireNotNull(dao.upload(expenseId))
        require(upload.accountKey == ReminderRepository.accountKey(c))
        check(upload.status in setOf("NEEDS_REVIEW", "PAUSED"))
        if (matchedId != null) {
            require(matchingServices(c, expenseId).any { it.id == matchedId }) { "The matching record is no longer available. Refresh and review again." }
            confirmUpload(upload, matchedId)
        } else dao.save(upload.copy(status = "QUEUED", error = null))
    }

    suspend fun editUnsent(c: Credentials, expense: ExpenseEntity) = writeMutex.withLock {
        val upload = requireNotNull(dao.upload(expense.localId))
        require(upload.accountKey == ReminderRepository.accountKey(c))
        check(upload.status in setOf("QUEUED", "PAUSED")) { "This service has been sent. Review its upload in Maintenance and manage the confirmed record in LubeLogger." }
        require(expense.type == "SERVICE") { "Linked work must remain a service expense." }
        database.withTransaction {
            database.expenseDao().updateExpense(expense)
            dao.save(upload.copy(status = "QUEUED", error = null))
        }
    }

    suspend fun deleteUnsent(c: Credentials, expenseId: Int): Boolean = writeMutex.withLock {
        val upload = dao.upload(expenseId) ?: return@withLock false
        require(upload.accountKey == ReminderRepository.accountKey(c))
        check(upload.status in setOf("QUEUED", "PAUSED")) { "This service has been sent. Verify the upload before deleting it." }
        database.withTransaction {
            dao.save(upload.copy(status = "CANCELLED", error = null))
            dao.reminders(upload.accountKey).filter { it.expenseLocalId == expenseId }.forEach { dao.save(it.copy(status = "CANCELLED", error = null)) }
            database.expenseDao().deleteByLocalId(expenseId)
        }
        true
    }

    // Called under writeMutex by OfflineSyncEngine for worker, sign-in, and manual sync.
    internal suspend fun syncLocked(c: Credentials) = withContext(NonCancellable) {
        val key = ReminderRepository.accountKey(c)
        for (upload in dao.uploads(key)) {
            if (upload.status == "DISPATCHING") {
                dao.save(upload.copy(status = "NEEDS_REVIEW", error = uncertainMessage))
                continue
            }
            if (upload.status != "QUEUED") continue
            val expense = database.expenseDao().getByLocalId(upload.expenseLocalId) ?: continue
            dao.save(upload.copy(status = "DISPATCHING", error = null))
            try {
                val response = remote.create(c, expense)
                val id = response.body()?.additionalData?.recordId
                when {
                    response.isSuccessful && response.body()?.success == true && id != null && id > 0 -> confirmUpload(upload, id)
                    response.code() in setOf(401, 403, 404, 405, 501) -> dao.save(upload.copy(status = "PAUSED", error = permissionMessage(response.code())))
                    response.body()?.success == false && response.code() < 500 -> dao.save(upload.copy(status = "PAUSED", error = "Service rejected. Check the details in LubeLogger, then retry after review."))
                    else -> dao.save(upload.copy(status = "NEEDS_REVIEW", error = uncertainMessage))
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { dao.save(upload.copy(status = "NEEDS_REVIEW", error = uncertainMessage)) }
        }
        for (op in dao.reminders(key)) {
            if (op.status !in setOf("QUEUED", "DISPATCHING", "VERIFYING")) continue
            if (op.expenseLocalId != null && dao.upload(op.expenseLocalId)?.status != "CONFIRMED") continue
            syncReminder(c, op)
        }
    }

    private suspend fun confirmUpload(upload: ServiceUploadEntity, id: Int) = database.withTransaction {
        dao.save(upload.copy(status = "CONFIRMED", remoteId = id, error = null))
        database.expenseDao().getByLocalId(upload.expenseLocalId)?.let {
            database.expenseDao().updateExpense(it.copy(remoteId = id, syncState = "SYNCED", lastSyncError = null))
        }
    }

    private suspend fun syncReminder(c: Credentials, op: ReminderOperationEntity) {
        try {
            val response = remote.reminders(c, op.vehicleId)
            if (!response.isSuccessful) {
                val paused = response.code() in setOf(401, 403, 404, 405, 501)
                dao.save(op.copy(status = if (paused) "PAUSED" else op.status, error = if (paused) permissionMessage(response.code()) else "Couldn't check the reminder. Sync again when connected."))
                return
            }
            val records = requireNotNull(response.body()).map { it.toReminder() }
            require(records.all { it.vehicleId == op.vehicleId })
            val current = records.firstOrNull { it.id == op.reminderId }?.let(ReminderValues::from)
            val original = values(op.originalJson)
            val target = op.targetJson?.let(::values)
            // Recheck the target even after process death or a lost write response.
            if ((target == null && current == null) || (target != null && current != null && target.merge(original, current) == current)) {
                dao.save(op.copy(status = "CONFIRMED", error = null))
                return
            }
            if (current == null || (!op.force && (target?.conflicts(original, current) ?: (original != current)))) {
                dao.save(op.copy(status = "CONFLICT", error = if (current == null) "This reminder was removed on the server. Use server version to dismiss the queued change." else "The same fields changed on the server. Review before applying your change."))
                return
            }
            val merged = target?.merge(original, current)
            merged?.draft()?.validationError()?.let { error(it) }
            dao.save(op.copy(status = "DISPATCHING", error = null))
            val result = if (merged == null) remote.delete(c, op.reminderId) else remote.update(c, op.reminderId, merged)
            when {
                result.isSuccessful && result.body()?.success == true -> dao.save(op.copy(status = "CONFIRMED", error = null))
                result.code() in setOf(401, 403, 404, 405, 501) -> dao.save(op.copy(status = "PAUSED", error = permissionMessage(result.code())))
                result.body()?.success == false && result.code() < 500 -> dao.save(op.copy(status = "PAUSED", error = "Reminder change rejected. Check its details and permissions, then retry."))
                else -> dao.save(op.copy(status = "VERIFYING", error = "Couldn't confirm the reminder change. Sync will check the server before retrying."))
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            // Persisted DISPATCHING is deliberately retained for verification after interruption.
            val latest = dao.reminders(op.accountKey).first { it.vehicleId == op.vehicleId && it.reminderId == op.reminderId }
            dao.save(latest.copy(error = "Couldn't connect. The next sync will check the server before sending this change."))
        }
    }

    companion object {
        val writeMutex = Mutex()
        private const val uncertainMessage = "The service may have uploaded. Find a matching server record or explicitly retry after reviewing LubeLogger."
        private fun permissionMessage(code: Int) = when (code) {
            401, 403 -> "Access denied. Restore this API key's vehicle edit/delete permissions, then choose Retry."
            else -> "This endpoint is unsupported. Update LubeLogger or manage the reminder there, then choose Retry."
        }
    }
}
