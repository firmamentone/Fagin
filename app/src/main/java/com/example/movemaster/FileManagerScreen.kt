package com.example.movemaster

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build // 用於版本判斷
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background // 用於標記選中項目
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed // 導入 itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions // 用於數字鍵盤
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Folder

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // 用於背景色
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType // 用於數字鍵盤
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerApp(mainViewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current

    val sourceFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    mainViewModel.setSourceFolderUri(uri)
                } catch (e: SecurityException) {
                    mainViewModel.setSourceFolderUri(null)
                }
            }
        }
    }

    val targetFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    mainViewModel.setTargetFolderUri(uri)
                } catch (e: SecurityException) {
                    mainViewModel.setTargetFolderUri(null)
                }
            }
        }
    }

    // --- 從 ViewModel 觀察狀態 ---
    val sourceFolderUri by mainViewModel.sourceFolderUri.observeAsState()
    val targetFolderUri by mainViewModel.targetFolderUri.observeAsState()
    val files by mainViewModel.files.observeAsState(emptyList())
    val isLoading by mainViewModel.isLoading.observeAsState(false)
    val statusMessage by mainViewModel.statusMessage.observeAsState("等待操作")
    val currentSortOrder by mainViewModel.currentSortOrder.observeAsState(SortOrder.NAME)
    val startRangeInput by mainViewModel.startRangeInput.observeAsState("")
    val endRangeInput by mainViewModel.endRangeInput.observeAsState("")
    val filesToOperate by mainViewModel.filesToOperate.observeAsState(emptyList())
    // --- 結束觀察狀態 ---


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("檔案總管") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp) // 水平 padding
                .padding(top = 16.dp) // 頂部 padding
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- 來源資料夾選擇 ---
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    sourceFolderPickerLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("選擇來源資料夾")
            }
            Text(
                text = "來源: ${getDisplayableUriPath(sourceFolderUri)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- 目標資料夾選擇 ---
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    targetFolderPickerLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("選擇目標資料夾 (用於複製/移動)")
            }
            Text(
                text = "目標: ${getDisplayableUriPath(targetFolderUri)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- 排序選項 ---
            SortOptionsRow(
                currentSortOrder = currentSortOrder,
                onSortOrderChange = { newSortOrder ->
                    mainViewModel.setSortOrder(newSortOrder)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- 檔案範圍選擇 UI ---
            Text("選擇操作範圍 (依列表編號):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startRangeInput,
                    onValueChange = { mainViewModel.onStartRangeChanged(it) },
                    label = { Text("起始") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = endRangeInput,
                    onValueChange = { mainViewModel.onEndRangeChanged(it) },
                    label = { Text("結束") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { mainViewModel.selectFilesByRange() },
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Text("選取")
                }
            }
            Button(
                onClick = { mainViewModel.clearFileSelection() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("清除選擇")
            }
            // --- 結束檔案範圍選擇 UI ---

            Spacer(modifier = Modifier.height(8.dp))

            // --- 狀態訊息 ---
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // --- 載入指示器 ---
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(8.dp)) // 指示器和列表間的間距
            }

            // --- 檔案列表 ---
            Box(modifier = Modifier.weight(1f)) { // 使用 Box 和 weight(1f) 使列表區域可擴展
                if (!isLoading && files.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize() // LazyColumn 填滿 Box
                    ) {
                        itemsIndexed(
                            items = files,
                            key = { _, fileItem -> fileItem.uri }
                        ) { index, fileItem ->
                            FileListItem(
                                fileItem = fileItem,
                                index = index,
                                isSelected = filesToOperate.contains(fileItem)
                            )
                            Divider()
                        }
                    }
                } else if (!isLoading && sourceFolderUri != null && files.isEmpty() && !statusMessage.contains("找到")) {
                    if (statusMessage == "等待操作" || statusMessage.startsWith("找到 0 個項目")) { //
                        Text("資料夾為空或無權限讀取。", modifier = Modifier.padding(16.dp).align(Alignment.Center))
                    }
                } else if (!isLoading && sourceFolderUri == null) {
                    Text("請先選擇來源資料夾以載入檔案。", modifier = Modifier.padding(16.dp).align(Alignment.Center))
                }
            }
            // --- 結束檔案列表 ---

            Spacer(modifier = Modifier.height(8.dp)) // 列表與操作按鈕之間的間距

            // --- 操作按鈕 ---
            Text("對選定檔案執行操作:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth()) //
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround //
            ) {
                Button(onClick = { mainViewModel.performOperation(OperationType.COPY) }) { Text("複製") } //
                Button(onClick = { mainViewModel.performOperation(OperationType.MOVE) }) { Text("移動") } //
                Button(onClick = { mainViewModel.performOperation(OperationType.DELETE) }) { Text("刪除") } //
            }

            Spacer(modifier = Modifier.height(16.dp)) // 操作按鈕與版本資訊之間的間距

            // --- 版本資訊顯示 ---
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }
            val versionName = packageInfo?.versionName ?: "N/A"
            val versionCode = packageInfo?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode.toLong()
                }
            } ?: "N/A"

            Text(
                text = "版本: $versionName ($versionCode)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp)) // 螢幕底部的一些額外間距
        }
    }
}

@Composable
fun SortOptionsRow(
    currentSortOrder: SortOrder, //
    onSortOrderChange: (SortOrder) -> Unit, //
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth() //
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) //
    ) {
        val sortOptions = listOf( //
            SortOrder.NAME to "名稱", //
            SortOrder.MODIFICATION_DATE to "修改日期", //
            SortOrder.EXIF_DATE to "拍攝日期" //
        )

        sortOptions.forEach { (sortOrder, label) -> //
            Button(
                onClick = { onSortOrderChange(sortOrder) }, //
                shape = MaterialTheme.shapes.small, //
                colors = ButtonDefaults.buttonColors( //
                    containerColor = if (sortOrder == currentSortOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, //
                    contentColor = if (sortOrder == currentSortOrder) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer //
                ),
                modifier = Modifier.weight(1f) //
            ) {
                Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) //
            }
        }
    }
}

@Composable
fun FileListItem(
    fileItem: FileItem, //
    index: Int, //
    isSelected: Boolean, //
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) } //
    val displayIndex = index + 1 //

    Row(
        modifier = modifier
            .fillMaxWidth() //
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent) // 標記選中項目的背景
            .padding(vertical = 8.dp, horizontal = 16.dp), //
        verticalAlignment = Alignment.CenterVertically //
    ) {
        Icon(
            imageVector = if (fileItem.isDirectory) Icons.Filled.Folder else Icons.Filled.Article, //
            contentDescription = if (fileItem.isDirectory) "資料夾" else "檔案", //
            tint = MaterialTheme.colorScheme.primary, //
            modifier = Modifier.size(36.dp) //
        )

        Spacer(modifier = Modifier.width(16.dp)) //

        Column(modifier = Modifier.weight(1f)) { //
            Text(
                text = "$displayIndex. ${fileItem.name}", //
                style = MaterialTheme.typography.titleMedium, //
                maxLines = 2, //
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis //
            )
            Spacer(modifier = Modifier.height(4.dp)) //
            Text(
                text = "修改: ${dateFormat.format(fileItem.lastModified)}" + //
                        (if (fileItem.exifDate != null) " | EXIF: ${dateFormat.format(fileItem.exifDate)}" else "") + //
                        (if (!fileItem.isDirectory) " | 大小: ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, fileItem.size)}" else ""), //
                style = MaterialTheme.typography.bodySmall, //
                color = MaterialTheme.colorScheme.onSurfaceVariant //
            )
        }
    }
}

@Composable
private fun getDisplayableUriPath(uri: Uri?): String {
    return uri?.path ?: "未選擇" //
}