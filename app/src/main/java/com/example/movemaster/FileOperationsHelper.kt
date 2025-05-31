package com.example.movemaster


import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract // 如果需要直接使用 DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 排序順序的枚舉
enum class SortOrder {
    NAME, MODIFICATION_DATE, EXIF_DATE
}

object FileOperationsHelper {
    private const val TAG = "FileOperationsHelper"
    // EXIF 日期通常是 "yyyy:MM:dd HH:mm:ss" 格式
    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())

    /**
     * 列出指定目錄下的檔案和資料夾
     */
    suspend fun listFiles(context: Context, directoryUri: Uri, sortOrder: SortOrder): List<FileItem> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileItem>()
        val contentResolver = context.contentResolver
        val parentDocumentFile = DocumentFile.fromTreeUri(context, directoryUri)

        parentDocumentFile?.listFiles()?.forEach { docFile ->
            // 確保檔案名稱不為 null
            docFile.name?.let { fileName ->
                val lastModifiedDate = Date(docFile.lastModified())
                var exifDate: Date? = null

                if (docFile.isFile && (docFile.type?.startsWith("image/") == true ||
                            fileName.endsWith(".jpg", true) ||
                            fileName.endsWith(".jpeg", true) ||
                            fileName.endsWith(".png", true) || // 可選：也為 png 讀取 (儘管 png 通常沒有標準 EXIF)
                            fileName.endsWith(".webp", true) ||
                            fileName.endsWith(".heic", true) ||
                            fileName.endsWith(".heif", true))) {
                    try {
                        contentResolver.openInputStream(docFile.uri)?.use { inputStream ->
                            val exifInterface = ExifInterface(inputStream)
                            // 嘗試多個常見的日期標籤
                            val dateStr = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
                            dateStr?.let {
                                try {
                                    exifDate = exifDateFormat.parse(it)
                                } catch (e: ParseException) {
                                    Log.w(TAG, "無法解析 EXIF 日期字串: '$it' for ${docFile.name}", e)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "讀取 EXIF 資訊時發生 IO 錯誤 for ${docFile.name}", e)
                    } catch (e: Exception) { // 捕捉 ExifInterface 可能拋出的其他受檢或未受檢異常
                        Log.e(TAG, "讀取 EXIF 資訊時發生未知錯誤 for ${docFile.name} (type: ${docFile.type})", e)
                    }
                }
                files.add(
                    FileItem(
                        name = fileName,
                        uri = docFile.uri,
                        size = if (docFile.isFile) docFile.length() else 0L, // 資料夾大小通常設為0或不顯示
                        lastModified = lastModifiedDate,
                        exifDate = exifDate,
                        isDirectory = docFile.isDirectory
                    )
                )
            }
        }
        return@withContext sortFiles(files, sortOrder) // 返回前排序
    }

    /**
     * 排序檔案列表
     */
    fun sortFiles(files: List<FileItem>, sortOrder: SortOrder): List<FileItem> {
        return when (sortOrder) {
            SortOrder.NAME -> files.sortedBy { it.name.lowercase(Locale.getDefault()) }
            SortOrder.MODIFICATION_DATE -> files.sortedByDescending { it.lastModified }
            SortOrder.EXIF_DATE -> files.sortedWith(
                compareByDescending<FileItem> { it.exifDate ?: Date(0) } // EXIF 日期優先 (nulls last if descending using Date(0) as placeholder)
                    .thenByDescending { it.lastModified } // 然後按修改日期
                    .thenBy { it.name.lowercase(Locale.getDefault()) } // 最後按名稱
            )
        }
    }

    /**
     * 複製檔案
     * @param newFileName 可選，如果為 null，則使用來源檔案名稱
     * @return true 如果成功，false 如果失敗
     */
    suspend fun copyFile(context: Context, sourceFileUri: Uri, targetDirectoryUri: Uri, newFileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val sourceDocument = DocumentFile.fromSingleUri(context, sourceFileUri)
        val targetDirectory = DocumentFile.fromTreeUri(context, targetDirectoryUri)

        if (sourceDocument == null || !sourceDocument.exists() || !sourceDocument.isFile) {
            Log.e(TAG, "來源檔案無效、不存在或不是檔案: $sourceFileUri")
            return@withContext false
        }
        if (targetDirectory == null || !targetDirectory.isDirectory) {
            Log.e(TAG, "目標資料夾無效或不是資料夾: $targetDirectoryUri")
            return@withContext false
        }

        val fileNameToUse = newFileName ?: sourceDocument.name ?: "copied_file_${System.currentTimeMillis()}"
        val mimeType = sourceDocument.type ?: contentResolver.getType(sourceFileUri) ?: "application/octet-stream"

        var targetFile: DocumentFile? = null
        try {
            // 檢查目標檔案是否存在，若存在則嘗試創建帶有後綴的新名稱 (例如 file_1.txt)
            var currentFileName = fileNameToUse
            var count = 1
            while (targetDirectory.findFile(currentFileName) != null) {
                val nameWithoutExtension = fileNameToUse.substringBeforeLast('.', fileNameToUse)
                val extension = fileNameToUse.substringAfterLast('.', "")
                currentFileName = if (extension.isNotEmpty()) {
                    "${nameWithoutExtension}_copy${count++}.$extension"
                } else {
                    "${nameWithoutExtension}_copy${count++}"
                }
                if (count > 100) { // 防止無限迴圈
                    Log.e(TAG, "無法為複製的檔案找到唯一的名稱: $fileNameToUse in $targetDirectoryUri")
                    return@withContext false
                }
            }
            targetFile = targetDirectory.createFile(mimeType, currentFileName)
            if (targetFile == null) {
                Log.e(TAG, "無法在目標資料夾創建檔案: $currentFileName (MIME: $mimeType)")
                return@withContext false
            }

            contentResolver.openInputStream(sourceFileUri)?.use { inputStream ->
                contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                    copyStream(inputStream, outputStream)
                } ?: run {
                    Log.e(TAG, "無法打開目標檔案的輸出流: ${targetFile.uri}")
                    targetFile.delete() // 清理部分創建的檔案
                    return@withContext false
                }
            } ?: run {
                Log.e(TAG, "無法打開來源檔案的輸入流: $sourceFileUri")
                targetFile.delete() // 清理部分創建的檔案
                return@withContext false
            }
            Log.i(TAG, "檔案複製成功: ${sourceFileUri} -> ${targetFile.uri}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "複製檔案時出錯: ${sourceFileUri} -> ${targetDirectoryUri}/${fileNameToUse}", e)
            targetFile?.delete() // 嘗試清理
            return@withContext false
        }
    }

    /**
     * 移動檔案 (實現為複製後刪除)
     */
    suspend fun moveFile(context: Context, sourceFileUri: Uri, targetDirectoryUri: Uri, newFileName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val sourceDocument = DocumentFile.fromSingleUri(context, sourceFileUri)
        if (sourceDocument == null || !sourceDocument.exists()) { // 移動操作前也檢查來源是否存在
            Log.e(TAG, "用於移動的來源檔案不存在: $sourceFileUri")
            return@withContext false
        }
        val fileNameForMove = newFileName ?: sourceDocument.name

        if (copyFile(context, sourceFileUri, targetDirectoryUri, fileNameForMove)) {
            if (deleteFile(context, sourceFileUri)) {
                Log.i(TAG, "檔案移動成功 (複製後刪除): $sourceFileUri -> $targetDirectoryUri")
                return@withContext true
            } else {
                Log.e(TAG, "檔案移動失敗: 複製成功但刪除來源檔案失敗: $sourceFileUri")
                // 這裡可以考慮是否要刪除已複製的檔案以進行回滾
                // val targetDirectory = DocumentFile.fromTreeUri(context, targetDirectoryUri)
                // targetDirectory?.findFile(fileNameForMove)?.delete()
                return@withContext false
            }
        } else {
            Log.e(TAG, "檔案移動失敗: 複製步驟失敗 for $sourceFileUri")
            return@withContext false
        }
    }

    /**
     * 刪除檔案
     */
    suspend fun deleteFile(context: Context, fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, fileUri)
            if (documentFile != null && documentFile.exists()) {
                if (documentFile.delete()) {
                    Log.i(TAG, "檔案刪除成功: $fileUri")
                    return@withContext true
                } else {
                    Log.e(TAG, "刪除檔案失敗: $fileUri (存在: ${documentFile.exists()}, 可寫: ${documentFile.canWrite()})")
                }
            } else {
                Log.w(TAG, "嘗試刪除的檔案不存在或為 null: $fileUri")
            }
        } catch (e: Exception) { // 例如 SecurityException
            Log.e(TAG, "刪除檔案時發生異常: $fileUri", e)
        }
        return@withContext false
    }

    /**
     * 輔助函數，用於將 InputStream 的內容複製到 OutputStream
     */
    @Throws(IOException::class)
    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8 * 1024) // 8KB buffer
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush() // 確保所有緩衝區數據都已寫入
    }
}