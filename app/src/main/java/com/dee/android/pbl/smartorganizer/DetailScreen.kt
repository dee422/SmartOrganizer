package com.dee.android.pbl.smartorganizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.Instant
import java.time.ZoneId
import androidx.compose.material.icons.filled.DateRange // 💡 导入日历图标
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: Container,
    containerDao: ContainerDao,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val itemsList = remember { mutableStateListOf<StorageItem>() }

    // 输入框状态
    var itemName by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") } // 默认可以为空，即无限期
    var note by remember { mutableStateOf("") }

    // 💡 1. 日历弹窗控制状态
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // 💡 2. 格式化日期的工具 (yyyy-MM-dd)
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    // 建议在 DetailScreen 顶部这样定义刷新逻辑
    fun refreshItems() {
        coroutineScope.launch {
            // 直接从数据库获取最新的、带有自增 ID 的完整列表
            val newData = containerDao.getItemsByContainer(container.id)
            itemsList.clear()
            itemsList.addAll(newData)
        }
    }

    // 💡 3. 初次进入页面时加载数据
    LaunchedEffect(container.id) {
        refreshItems()
    }

    var itemToDelete by remember { mutableStateOf<StorageItem?>(null) } // 💡 新增状态

    // --- 💡 新增删除确认弹窗 ---
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除物品") },
            text = { Text("确定要从柜子中移除「${itemToDelete!!.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        containerDao.deleteItem(itemToDelete!!)
                        // 刷新列表的逻辑...
                        refreshItems()
                        itemToDelete = null
                    }
                }) { Text("确定", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("取消") }
            }
        )
    }

    // 初始加载该柜子的物品
    LaunchedEffect(container.id) {
        itemsList.clear()
        itemsList.addAll(containerDao.getItemsByContainer(container.id))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${container.name} - 物品清单") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // --- 物品添加区域 ---
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("添加新物品", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("物品名称") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // 💡 4. 修改后的日期输入框
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { expiryDate = it },
                            label = { Text("过期日期 (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("不填为无限期") },
                            // 添加末尾图标按钮
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "选择日期")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("备注") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // 找到添加物品的 Button
                    Button(onClick = {
                        if (itemName.isNotBlank()) {
                            coroutineScope.launch {
                                // 1. 插入数据库
                                containerDao.insertItem(
                                    StorageItem(
                                        containerId = container.id,
                                        name = itemName,
                                        expiryDate = if (expiryDate.isBlank()) "无限期" else expiryDate,
                                        note = note
                                    )
                                )
                                // 2. 💡 关键：清空输入框并【立即调用刷新函数】
                                itemName = ""
                                expiryDate = ""
                                note = ""

                                refreshItems() // 重新从数据库读取，确保 UI 上的所有 item 都有真实的 ID
                            }
                        }
                    }) { Text("添加物品")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 物品列表区域 ---
            LazyColumn {
                items(itemsList) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        supportingContent = {
                            Column {
                                // 💡 修复点：调用 getExpiryColor 函数来动态决定颜色
                                val dateColor = getExpiryColor(item.expiryDate)
                                Text(
                                    text = "有效期: ${item.expiryDate}",
                                    color = dateColor
                                )

                                if (item.note.isNotBlank()) {
                                    Text(text = "备注: ${item.note}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { itemToDelete = item }) { // 💡 改为赋值给状态，不直接删除
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// 💡 计算日期对应的颜色函数
fun getExpiryColor(dateString: String): Color {
    if (dateString == "无限期" || dateString.isBlank()) return Color.Gray

    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val expiryDate = LocalDate.parse(dateString, formatter)
        val today = LocalDate.now()

        // 计算今天到过期日的天数差
        val daysUntil = ChronoUnit.DAYS.between(today, expiryDate)

        when {
            daysUntil < 0 -> Color.Red          // 已过期
            daysUntil <= 7 -> Color(0xFFFFA500) // 临期（7天内），显示橙色
            else -> Color(0xFF4CAF50)           // 安全（7天以上），显示绿色
        }
    } catch (e: Exception) {
        // 如果用户格式输入错误，显示红色作为警告
        Color.Red
    }
}