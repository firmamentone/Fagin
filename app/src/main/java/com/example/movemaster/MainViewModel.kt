package com.example.movemaster

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// 定義操作類型，方便 ViewModel 處理
enum class OperationType {
    COPY, MOVE, DELETE
}

// SortOrder enum 應該在此檔案或 FileOperationsHelper.kt 中定義
// enum class SortOrder { NAME, MODIFICATION_DATE, EXIF_DATE } // 假設已定義

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _sourceFolderUri = MutableLiveData<Uri?>()
    val sourceFolderUri: LiveData<Uri?> = _sourceFolderUri

    private val _targetFolderUri = MutableLiveData<Uri?>()
    val targetFolderUri: LiveData<Uri?> = _targetFolderUri

    private val _files = MutableLiveData<List<FileItem>>(emptyList())
    val files: LiveData<List<FileItem>> = _files

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _statusMessage = MutableLiveData<String>("等待操作")
    val statusMessage: LiveData<String> = _statusMessage

    private val _currentSortOrder = MutableLiveData<SortOrder>(SortOrder.NAME)
    val currentSortOrder: LiveData<SortOrder> = _currentSortOrder

    // 新增：用於管理輸入框的狀態
    private val _startRangeInput = MutableLiveData<String>("")
    val startRangeInput: LiveData<String> = _startRangeInput

    private val _endRangeInput = MutableLiveData<String>("")
    val endRangeInput: LiveData<String> = _endRangeInput

    // 新增：用於存放被選中待操作的檔案
    private val _filesToOperate = MutableLiveData<List<FileItem>>(emptyList())
    val filesToOperate: LiveData<List<FileItem>> = _filesToOperate // 讓 UI 可以觀察選中的檔案


    fun setSourceFolderUri(uri: Uri?) {
        _sourceFolderUri.value = uri
        if (uri != null) {
            loadFiles() // 選擇新資料夾後自動載入檔案
        } else {
            _files.value = emptyList() // 如果 URI 為 null，清空檔案列表
            _filesToOperate.value = emptyList() // 清除已選檔案
            clearRangeInputs() // 清空輸入框
            _statusMessage.value = "來源資料夾未選擇"
        }
    }

    fun setTargetFolderUri(uri: Uri?) {
        _targetFolderUri.value = uri
        if (uri == null) {
            _statusMessage.value = "目標資料夾未選擇"
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        if (_currentSortOrder.value != sortOrder) {
            _currentSortOrder.value = sortOrder
            _filesToOperate.value = emptyList() // 排序改變，清除已選檔案
            clearRangeInputs() // 清空輸入框
            // 如果已有來源資料夾，則根據新的排序順序重新載入和排序檔案
            _sourceFolderUri.value?.let {
                loadFiles()
            }
        }
    }

    fun loadFiles() {
        _sourceFolderUri.value?.let { uri ->
            _isLoading.value = true
            _statusMessage.value = "正在載入檔案..."
            viewModelScope.launch { // 在 ViewModel 的協程範圍內啟動協程
                try {
                    val fileList = FileOperationsHelper.listFiles(
                        getApplication(), // AndroidViewModel 提供的 Application Context
                        uri,
                        _currentSortOrder.value ?: SortOrder.NAME // 使用當前排序順序
                    )
                    _files.value = fileList
                    _filesToOperate.value = emptyList() // 重新載入檔案，清除已選檔案
                    clearRangeInputs() // 清空輸入框
                    _statusMessage.value = if (fileList.isEmpty()) "資料夾為空或無權限" else "找到 ${fileList.size} 個項目"
                } catch (e: Exception) {
                    Log.e("MainViewModel", "載入檔案時發生錯誤", e)
                    _files.value = emptyList()
                    _filesToOperate.value = emptyList()
                    clearRangeInputs()
                    _statusMessage.value = "載入檔案失敗: ${e.message}"
                } finally {
                    _isLoading.value = false
                }
            }
        } ?: run {
            _files.value = emptyList()
            _filesToOperate.value = emptyList()
            clearRangeInputs()
            _statusMessage.value = "請先選擇來源資料夾"
        }
    }

    // 新增：更新輸入框狀態的函式
    fun onStartRangeChanged(text: String) {
        _startRangeInput.value = text
    }

    fun onEndRangeChanged(text: String) {
        _endRangeInput.value = text
    }

    private fun clearRangeInputs() {
        _startRangeInput.value = ""
        _endRangeInput.value = ""
    }

    // 新增：根據輸入的編號範圍選擇檔案
    fun selectFilesByRange() {
        val currentFiles = _files.value ?: emptyList()
        if (currentFiles.isEmpty()) {
            _statusMessage.value = "沒有檔案可供選擇"
            _filesToOperate.value = emptyList()
            return
        }

        val startNum = _startRangeInput.value?.trim()?.toIntOrNull()
        val endNum = _endRangeInput.value?.trim()?.toIntOrNull()

        if (startNum == null || endNum == null || startNum <= 0 || endNum < startNum) {
            _statusMessage.value = "請輸入有效的數字範圍 (例如：1 到 5，且結束編號需大於等於起始編號)"
            _filesToOperate.value = emptyList() // 清除非法選擇
            return
        }
        // 檢查是否超出列表實際大小 (使用者看到的是 1-based index)
        if (startNum > currentFiles.size || endNum > currentFiles.size) {
            _statusMessage.value = "輸入的編號超出檔案列表範圍 (最大 ${currentFiles.size})"
            _filesToOperate.value = emptyList() // 清除非法選擇
            return
        }

        // 使用者輸入的是 1-based 索引，轉換為 0-based
        // subList 的第二個參數 toIndex 是不包含的，所以 endNum 不需要 -1
        val selected = currentFiles.subList(startNum - 1, endNum)
        _filesToOperate.value = selected
        if (selected.isNotEmpty()) {
            _statusMessage.value = "已選擇 ${selected.size} 個檔案 (編號 $startNum 到 $endNum)"
        } else {
            // 這種情況理論上如果 startNum <= endNum 且在有效範圍內， subList 不會是空的
            // 但如果 startNum > endNum (已被上面的檢查攔截) 或 startNum == endNum+1 (例如範圍 5-4)
            _statusMessage.value = "指定範圍內沒有選中任何檔案"
        }
    }

    // 新增：清除選擇
    fun clearFileSelection() {
        _filesToOperate.value = emptyList()
        clearRangeInputs()
        _statusMessage.value = "已清除選擇"
    }

    // 修改：performOperation不再接收 countStr，而是操作 _filesToOperate.value
    fun performOperation(operationType: OperationType) {
        val targetUri = _targetFolderUri.value
        val filesForOperation = _filesToOperate.value ?: emptyList()

        if ((operationType == OperationType.COPY || operationType == OperationType.MOVE) && targetUri == null) {
            _statusMessage.value = "請先選擇目標資料夾 (用於複製/移動)"
            return
        }
        if (filesForOperation.isEmpty()) {
            _statusMessage.value = "沒有選擇任何檔案進行操作"
            return
        }

        _isLoading.value = true
        _statusMessage.value = "開始執行 ${operationType.name.lowercase()} 操作 (${filesForOperation.size} 個檔案)..."

        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            for (fileItem in filesForOperation) {
                val result = when (operationType) {
                    OperationType.COPY -> {
                        if (targetUri != null) FileOperationsHelper.copyFile(getApplication(), fileItem.uri, targetUri, fileItem.name) else false
                    }
                    OperationType.MOVE -> {
                        if (targetUri != null) FileOperationsHelper.moveFile(getApplication(), fileItem.uri, targetUri, fileItem.name) else false
                    }
                    OperationType.DELETE -> FileOperationsHelper.deleteFile(getApplication(), fileItem.uri)
                }
                if (result) successCount++ else failCount++
            }

            _isLoading.value = false
            val opName = when(operationType) {
                OperationType.COPY -> "複製"
                OperationType.MOVE -> "移動"
                OperationType.DELETE -> "刪除"
            }
            _statusMessage.value = "$opName 操作完成: $successCount 個成功, $failCount 個失敗。"

            // 操作完成後清除選擇並重新載入來源資料夾的檔案列表
            _filesToOperate.value = emptyList()
            clearRangeInputs()
            _sourceFolderUri.value?.let { loadFiles() } // Refresh source folder
        }
    }
}