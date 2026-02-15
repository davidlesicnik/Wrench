package com.lesicnik.wrench.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        SyncMetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WrenchDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun pendingSyncOpDao(): PendingSyncOpDao
    abstract fun syncConflictDao(): SyncConflictDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        @Volatile
        private var instance: WrenchDatabase? = null

        fun getInstance(context: Context): WrenchDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrenchDatabase::class.java,
                    "wrench_offline.db"
                ).build().also { instance = it }
            }
        }
    }
}
