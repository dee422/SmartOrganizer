package com.dee.android.pbl.smartorganizer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index // 💡 导入 Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "storage_items",
    foreignKeys = [
        ForeignKey(
            entity = Container::class,
            parentColumns = ["id"],
            childColumns = ["containerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // 💡 必须添加这一行，为 containerId 建立索引
    indices = [Index(value = ["containerId"])]
)
data class StorageItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val containerId: Int,
    val name: String,
    val expiryDate: String = "无限期",
    val note: String = ""
)