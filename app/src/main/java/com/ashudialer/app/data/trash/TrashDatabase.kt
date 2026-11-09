/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.data.trash

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One thing in Trash: a deleted contact, or a batch of deleted calls from one number.
 *
 * [payload] is everything needed to put it back (JSON text), [title] / [subtitle] are what the Trash list shows.
 * Trash lives in its OWN small database file (ashu_trash.db) on purpose: the main call-log database is untouched, so this
 * feature can never trigger a migration of it.
 */
@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [TrashRepository.KIND_CONTACT] or [TrashRepository.KIND_CALLS] */
    val kind: String,
    val title: String,
    val subtitle: String,
    val payload: String,
    /** How many calls are inside (1 for a contact). */
    val itemCount: Int,
    val deletedAtMillis: Long,
    val expiresAtMillis: Long
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAtMillis DESC")
    fun observeAll(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrashItemEntity?

    @Insert
    suspend fun insert(item: TrashItemEntity): Long

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trash_items WHERE expiresAtMillis < :now")
    suspend fun deleteExpired(now: Long): Int

    @Query("DELETE FROM trash_items")
    suspend fun clear()
}

@Database(entities = [TrashItemEntity::class], version = 1, exportSchema = false)
abstract class TrashDatabase : RoomDatabase() {
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var instance: TrashDatabase? = null

        fun getInstance(context: Context): TrashDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrashDatabase::class.java,
                    "ashu_trash.db"
                ).build().also { instance = it }
            }
    }
}
