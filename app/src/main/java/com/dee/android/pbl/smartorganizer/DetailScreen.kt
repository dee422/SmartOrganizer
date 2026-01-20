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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: Container,
    containerDao: ContainerDao,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val itemList = remember { mutableStateListOf<StorageItem>() }

    // 输入框状态
    var itemName by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") } // 默认可以为空，即无限期
    var note by remember { mutableStateOf("") }

    // 初始加载该柜子的物品
    LaunchedEffect(container.id) {
        itemList.clear()
        itemList.addAll(containerDao.getItemsByContainer(container.id))
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
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = { expiryDate = it },
                            label = { Text("过期日期 (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("不填为无限期") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (itemName.isNotBlank()) {
                                coroutineScope.launch {
                                    val newItem = StorageItem(
                                        containerId = container.id,
                                        name = itemName,
                                        expiryDate = if (expiryDate.isBlank()) "无限期" else expiryDate,
                                        note = note
                                    )
                                    containerDao.insertItem(newItem)
                                    itemList.add(newItem)
                                    // 清空输入
                                    itemName = ""; expiryDate = ""; note = ""
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                    ) {
                        Text("添加")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 物品列表区域 ---
            LazyColumn {
                items(itemList) { item ->
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
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    containerDao.deleteItem(item)
                                    itemList.remove(item)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
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