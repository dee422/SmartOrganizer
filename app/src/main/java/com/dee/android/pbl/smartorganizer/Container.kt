package com.dee.android.pbl.smartorganizer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "containers")
data class Container(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val location: String,
    val imagePath: String? = null, // 保留路径字段，不影响之前的逻辑
    val imageData: ByteArray? = null // 用于存储缩略图的二进制数据
)