package com.dee.android.pbl.smartorganizer

import androidx.room.*

@Dao
interface ContainerDao {
    // --- 原有的柜子相关操作 ---
    @Query("SELECT * FROM containers")
    suspend fun getAll(): List<Container>

    @Insert
    suspend fun insert(container: Container)

    @Update
    suspend fun update(container: Container)

    @Delete
    suspend fun delete(container: Container)

    @Query("""
    SELECT DISTINCT containers.* FROM containers 
    LEFT JOIN storage_items ON containers.id = storage_items.containerId 
    WHERE containers.name LIKE '%' || :search || '%' 
    OR containers.location LIKE '%' || :search || '%' 
    OR storage_items.name LIKE '%' || :search || '%'
""")
    suspend fun searchContainers(search: String): List<Container>

    // --- 💡 必须添加以下三个物品相关操作，修复你的报错 ---

    @Query("SELECT * FROM storage_items WHERE containerId = :containerId")
    suspend fun getItemsByContainer(containerId: Int): List<StorageItem>

    @Insert
    suspend fun insertItem(item: StorageItem) // 👈 对应报错 1

    @Delete
    suspend fun deleteItem(item: StorageItem) // 👈 对应报错 2
}