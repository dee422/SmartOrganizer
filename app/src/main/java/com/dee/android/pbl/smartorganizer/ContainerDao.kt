package com.dee.android.pbl.smartorganizer

import androidx.room.*

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers")
    suspend fun getAll(): List<Container>

    @Insert
    suspend fun insert(container: Container)

    @Delete
    suspend fun delete(container: Container)

    // 💡 新增：用于补拍照片后更新数据库条目
    @Update
    suspend fun update(container: Container)
}