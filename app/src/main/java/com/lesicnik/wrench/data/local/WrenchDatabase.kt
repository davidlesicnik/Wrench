package com.lesicnik.wrench.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lesicnik.wrench.data.local.dao.ReminderDao
import com.lesicnik.wrench.data.local.entity.ReminderEntity
import com.lesicnik.wrench.data.local.entity.ReminderSyncEntity
import com.lesicnik.wrench.data.local.dao.ExpenseDao
import com.lesicnik.wrench.data.local.dao.PendingSyncOpDao
import com.lesicnik.wrench.data.local.dao.SyncConflictDao
import com.lesicnik.wrench.data.local.dao.SyncMetaDao
import com.lesicnik.wrench.data.local.dao.VehicleDao
import com.lesicnik.wrench.data.local.entity.ExpenseEntity
import com.lesicnik.wrench.data.local.entity.PendingSyncOpEntity
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
        ReminderSyncEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WrenchDatabase : RoomDatabase() {

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

        @Volatile
        private var instance: WrenchDatabase? = null

        fun getInstance(context: Context): WrenchDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrenchDatabase::class.java,
                    "wrench_offline.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
        }
    }
}
