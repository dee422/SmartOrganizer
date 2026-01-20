package com.dee.android.pbl.smartorganizer

import android.content.Context
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.gson.Gson
import android.content.Intent
import androidx.compose.material.icons.filled.Share

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // 💡 必须在 super.onCreate 之前调用
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val containerDao = db.containerDao()

        setContent {
            val context = LocalContext.current

            // --- 状态管理 ---
            val containerList = remember { mutableStateListOf<Container>() }
            var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            var showDialogImage by remember { mutableStateOf<Bitmap?>(null) }
            var itemToDelete by remember { mutableStateOf<Container?>(null) }
            var currentContainer by remember { mutableStateOf<Container?>(null) }
            var activeTargetContainer by remember { mutableStateOf<Container?>(null) }
            var searchQuery by remember { mutableStateOf("") }

            // --- 拍照启动器 ---
            val cameraLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicturePreview()
            ) { bitmap ->
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()

                    lifecycleScope.launch {
                        if (activeTargetContainer != null) {
                            val updated = activeTargetContainer!!.copy(imageData = byteArray)
                            containerDao.update(updated)
                            activeTargetContainer = null
                        } else {
                            capturedBitmap = bitmap
                        }
                        containerList.clear()
                        containerList.addAll(containerDao.getAll())
                    }
                }
            }

            // --- 在 setContent 内部，SmartOrganizerTheme 之前定义 ---
            val coroutineScope = rememberCoroutineScope() // 💡 专门为 Compose 按钮点击准备的协程作用域

            fun exportData(context: Context, containerDao: ContainerDao) {
                coroutineScope.launch {
                    try {
                        val allContainers = containerDao.getAll()
                        // 💡 关键修改：备份时去除图片字节数据，防止数据量过大导致系统崩溃
                        val containersMinimal = allContainers.map {
                            it.copy(imageData = null)
                        }

                        val allItems = mutableListOf<StorageItem>()
                        allContainers.forEach { container ->
                            allItems.addAll(containerDao.getItemsByContainer(container.id))
                        }

                        val backupMap = mapOf(
                            "app" to "SmartOrganizer",
                            "date" to java.time.LocalDate.now().toString(),
                            "containers" to containersMinimal,
                            "items" to allItems
                        )

                        val jsonString = Gson().toJson(backupMap)

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, jsonString)
                            type = "text/plain"
                        }
                        // 💡 增加一个判断，确保有应用可以接收
                        val shareIntent = Intent.createChooser(sendIntent, "导出文本备份 (不含照片)")
                        context.startActivity(shareIntent)

                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // 初始加载
            LaunchedEffect(Unit) {
                containerList.addAll(containerDao.getAll())
            }

            SmartOrganizerTheme {
                // --- 1. 弹窗层 ---
                // 大图预览
                if (showDialogImage != null) {
                    Dialog(onDismissRequest = { showDialogImage = null }) {
                        Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                            Image(bitmap = showDialogImage!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                }

                // 删除确认
                if (itemToDelete != null) {
                    AlertDialog(
                        onDismissRequest = { itemToDelete = null },
                        title = { Text("确认删除") },
                        text = { Text("确定要删除柜子「${itemToDelete!!.name}」及其内部所有物品吗？") },
                        confirmButton = {
                            TextButton(onClick = {
                                lifecycleScope.launch {
                                    containerDao.delete(itemToDelete!!)
                                    containerList.remove(itemToDelete!!)
                                    itemToDelete = null
                                }
                            }) { Text("确定", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { itemToDelete = null }) { Text("取消") }
                        }
                    )
                }

                // --- 2. 页面内容层 ---
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (currentContainer == null) {
                        // 【主页面】
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏠 家庭收纳助手", style = MaterialTheme.typography.headlineMedium)

                                // 💡 备份按钮
                                IconButton(onClick = { exportData(context, containerDao) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "导出备份",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 搜索框
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    lifecycleScope.launch {
                                        val results = if (it.isBlank()) containerDao.getAll() else containerDao.searchContainers(it)
                                        containerList.clear()
                                        containerList.addAll(results)
                                    }
                                },
                                label = { Text("🔍 搜索柜子或位置...") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                singleLine = true
                            )

                            // 新增区域
                            var name by remember { mutableStateOf("") }
                            var location by remember { mutableStateOf("") }
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("柜子名称") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("位置") }, modifier = Modifier.fillMaxWidth())

                            Button(onClick = {
                                activeTargetContainer = null
                                cameraLauncher.launch()
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(if (capturedBitmap == null) "📸 拍摄预览照片" else "✅ 照片已拍好")
                            }

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

                            // 柜子列表
                            LazyColumn {
                                items(containerList) { item ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .combinedClickable(
                                                onClick = { currentContainer = item }, // 💡 修复：点击进入详情页
                                                onLongClick = { itemToDelete = item } // 💡 修复：长按弹出删除确认
                                            )
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (item.imageData != null) {
                                                val bitmap = BitmapFactory.decodeByteArray(item.imageData, 0, item.imageData.size)
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(60.dp).clickable { showDialogImage = bitmap },
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                IconButton(onClick = {
                                                    activeTargetContainer = item
                                                    cameraLauncher.launch()
                                                }) {
                                                    Icon(Icons.Default.AddAPhoto, contentDescription = "补拍", tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(item.name, style = MaterialTheme.typography.titleLarge)
                                                Text("位置：${item.location}", style = MaterialTheme.typography.bodyMedium)
                                            }

                                            if (item.imageData != null) {
                                                IconButton(onClick = {
                                                    activeTargetContainer = item
                                                    cameraLauncher.launch()
                                                }) {
                                                    Icon(Icons.Default.AddAPhoto, contentDescription = "重拍", modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 【详情页】
                        DetailScreen(
                            container = currentContainer!!,
                            containerDao = containerDao,
                            onBack = { currentContainer = null }
                        )
                    }
                }
            }
        }
    }
}