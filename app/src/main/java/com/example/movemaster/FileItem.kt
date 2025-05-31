package com.example.movemaster

import android.net.Uri
import java.util.Date // 注意：這裡使用的是 java.util.Date

data class FileItem(
    val name: String,          // 檔案或資料夾名稱
    val uri: Uri,              // 檔案或資料夾的 Uri，用於後續操作
    val size: Long,            // 檔案大小 (位元組)，資料夾通常為 0 或特定值
    val lastModified: Date,    // 最後修改日期
    val exifDate: Date?,       // EXIF 拍攝日期 (圖片專用，可能為 null)
    val isDirectory: Boolean   // 是否為資料夾
)
