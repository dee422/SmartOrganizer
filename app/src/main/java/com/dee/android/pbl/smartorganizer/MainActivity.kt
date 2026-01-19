package com.dee.android.pbl.smartorganizer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.dee.android.pbl.smartorganizer.ui.theme.SmartOrganizerTheme
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val containerDao = db.containerDao()

        setContent {
            val context = LocalContext.current
            val containerList = remember { mutableStateListOf<Container>() }
            var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var showDialogImage by remember { mutableStateOf<Bitmap?>(null) }

            // 💡 关键：记录当前正在为哪个 Container 拍照
            var activeTargetContainer by remember { mutableStateOf<Container?>(null) }

            // 📸 拍照启动器
            val cameraLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicturePreview()
            ) { bitmap ->
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()

                    lifecycleScope.launch {
                        if (activeTargetContainer != null) {
                            // 情况 A：这是“补拍”逻辑
                            val updated = activeTargetContainer!!.copy(imageData = byteArray)
                            containerDao.update(updated)
                            activeTargetContainer = null // 处理完清空
                        } else {
                            // 情况 B：这是“新增”时的预览
                            capturedBitmap = bitmap
                        }
                        // 统一刷新列表
                        containerList.clear()
                        containerList.addAll(containerDao.getAll())
                    }
                }
            }

            LaunchedEffect(Unit) {
                containerList.addAll(containerDao.getAll())
            }

            SmartOrganizerTheme {
                if (showDialogImage != null) {
                    Dialog(onDismissRequest = { showDialogImage = null }) {
                        Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Image(bitmap = showDialogImage!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🏠 家庭收纳助手", style = MaterialTheme.typography.headlineMedium)

                        var name by remember { mutableStateOf("") }
                        var location by remember { mutableStateOf("") }

                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("柜子名称") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("位置") }, modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        // 按钮 1：新增拍照
                        Button(onClick = {
                            activeTargetContainer = null // 确保不是补拍
                            cameraLauncher.launch()
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (capturedBitmap == null) "📸 拍摄预览照片" else "✅ 照片已拍好")
                        }

                        // 按钮 2：保存
                        Button(onClick = {
                            if (name.isNotBlank()) {
                                val stream = ByteArrayOutputStream()
                                capturedBitmap?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                val byteArray = if (capturedBitmap != null) stream.toByteArray() else null
                                lifecycleScope.launch {
                                    containerDao.insert(Container(name = name, location = location, imageData = byteArray))
                                    containerList.clear()
                                    containerList.addAll(containerDao.getAll())
                                    name = ""; location = ""; capturedBitmap = null
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("💾 保存新柜子") }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        LazyColumn {
                            items(containerList) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(
                                        onClick = { },
                                        onLongClick = {
                                            lifecycleScope.launch {
                                                containerDao.delete(item)
                                                containerList.remove(item)
                                            }
                                        }
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // 图片展示区
                                        if (item.imageData != null) {
                                            val bitmap = BitmapFactory.decodeByteArray(item.imageData, 0, item.imageData.size)
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(60.dp).clickable { showDialogImage = bitmap },
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            // 💡 补拍按钮：如果没照片，显示一个小相机图标
                                            IconButton(onClick = {
                                                activeTargetContainer = item // 标记现在是给谁补拍
                                                cameraLauncher.launch()
                                            }) {
                                                Icon(Icons.Default.AddAPhoto, contentDescription = "补拍", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.titleLarge)
                                            Text("位置：${item.location}")
                                        }

                                        // 💡 如果已经有照片了，也留一个小图标方便“重拍”
                                        if (item.imageData != null) {
                                            IconButton(onClick = {
                                                activeTargetContainer = item
                                                cameraLauncher.launch()
                                            }) {
                                                Icon(Icons.Default.AddAPhoto, contentDescription = "重拍", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}