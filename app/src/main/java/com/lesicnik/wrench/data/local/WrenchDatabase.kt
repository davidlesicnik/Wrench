package com.lesicnik.wrench.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lesicnik.wrench.data.local.dao.ExpenseDao
import com.lesicnik.wrench.data.local.dao.MaintenanceDao
import com.lesicnik.wrench.data.local.dao.PendingSyncOpDao
import com.lesicnik.wrench.data.local.dao.ReminderDao
import com.lesicnik.wrench.data.local.dao.SyncConflictDao
import com.lesicnik.wrench.data.local.dao.SyncMetaDao
import com.lesicnik.wrench.data.local.dao.VehicleDao
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
import com.lesicnik.wrench.data.local.entity.ReminderEntity
import com.lesicnik.wrench.data.local.entity.ReminderOperationEntity
import com.lesicnik.wrench.data.local.entity.ReminderSyncEntity
import com.lesicnik.wrench.data.local.entity.ServiceUploadEntity
import com.lesicnik.wrench.data.local.entity.SyncConflictEntity
import com.lesicnik.wrench.data.local.entity.SyncMetaEntity
import com.lesicnik.wrench.data.local.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        ExpenseEntity::class,
        PendingSyncOpEntity::class,
        SyncConflictEntity::class,
        SyncMetaEntity::class,
        ReminderEntity::class,
        ReminderSyncEntity::class,
        ReminderOperationEntity::class,
        ServiceUploadEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class WrenchDatabase : RoomDatabase() {

    abstract fun maintenanceDao(): MaintenanceDao

    abstract fun reminderDao(): ReminderDao

    abstract fun vehicleDao(): VehicleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun pendingSyncOpDao(): PendingSyncOpDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS reminders (
                    accountKey TEXT NOT NULL, vehicleId INTEGER NOT NULL, id INTEGER NOT NULL,
                    description TEXT NOT NULL, metric TEXT NOT NULL, urgency TEXT NOT NULL,
                    dueDate TEXT, dueOdometer INTEGER, dueDistance INTEGER,
                    notes TEXT NOT NULL, tags TEXT NOT NULL,
                    PRIMARY KEY(accountKey, vehicleId, id))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS reminder_sync (
                    accountKey TEXT NOT NULL, vehicleId INTEGER NOT NULL, fetchedAt INTEGER NOT NULL,
                    PRIMARY KEY(accountKey, vehicleId))""")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountKey TEXT")
                db.execSQL("ALTER TABLE pending_sync_ops ADD COLUMN accountKey TEXT")
                db.execSQL("""CREATE TABLE IF NOT EXISTS reminder_operations (
                    accountKey TEXT NOT NULL, serverUrl TEXT NOT NULL, vehicleId INTEGER NOT NULL,
                    reminderId INTEGER NOT NULL, originalJson TEXT NOT NULL, targetJson TEXT,
                    expenseLocalId INTEGER, status TEXT NOT NULL, error TEXT, force INTEGER NOT NULL,
                    PRIMARY KEY(accountKey, vehicleId, reminderId))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS service_uploads (
                    expenseLocalId INTEGER NOT NULL, accountKey TEXT NOT NULL, serverUrl TEXT NOT NULL,
                    vehicleId INTEGER NOT NULL, reminderId INTEGER NOT NULL, status TEXT NOT NULL,
                    remoteId INTEGER, error TEXT, PRIMARY KEY(expenseLocalId))""")
            }
        }

        @Volatile
        private var instance: WrenchDatabase? = null

        fun getInstance(context: Context): WrenchDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrenchDatabase::class.java,
                    "wrench_offline.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }
    }
}
