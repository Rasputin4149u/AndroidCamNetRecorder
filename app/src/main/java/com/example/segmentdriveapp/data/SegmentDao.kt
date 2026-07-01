package com.example.segmentdriveapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.segmentdriveapp.model.UploadState

@Dao
interface SegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SegmentEntity): Long

    @Update
    suspend fun update(entity: SegmentEntity)

    @Query("SELECT * FROM segments ORDER BY id DESC")
    suspend fun getAll(): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE uploadState IN (:states) ORDER BY segmentIndex ASC")
    suspend fun getByStates(states: List<UploadState>): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SegmentEntity?
}
