# MoveMaster

MoveMaster is an Android file management application designed for efficient file Browse, sorting, selection, and operations (copy, move, delete). It features an MVVM architecture, Jetpack Compose for the UI, Kotlin Coroutines for asynchronous tasks, and utilizes Android's Storage Access Framework (SAF) for robust file handling.

## 專案目標

開發一款 Android 檔案管理應用程式，允許使用者方便地瀏覽、排序、選擇指定範圍的檔案，並對選中的檔案執行複製、移動和刪除操作。應用程式應提供清晰的使用者介面和直觀的操作流程。

## 主要功能

* **資料夾選擇**：允許使用者選擇來源資料夾和目標資料夾（用於複製/移動操作）。
* **檔案列表顯示**：列出所選來源資料夾中的檔案和子資料夾。
* **檔案排序**：支援按檔案名稱、修改日期、EXIF 拍攝日期（針對圖片）進行排序。
* **檔案資訊顯示**：顯示檔案名稱、圖示、修改日期、EXIF 日期（若有）、檔案大小（非資料夾）。
* **列表編號**：在排序後的檔案列表項前顯示編號。
* **範圍選擇**：允許使用者輸入列表編號範圍來選取一批檔案。
* **檔案操作**：對選取的檔案執行複製、移動、刪除操作。
* **狀態反饋**：顯示操作過程中的載入狀態和結果訊息。
* **版本資訊**：在應用程式介面顯示版本號和版本名稱。
* **權限處理**：使用 Android 的儲存存取框架 (SAF) 進行檔案操作並處理相關權限。

## 系統架構

本專案採用 **MVVM (Model-View-ViewModel)** 架構模式。

* **Model**: `FileOperationsHelper.kt` (底層檔案操作邏輯), `FileItem.kt` (資料類別)。
* **View**: `FileManagerScreen.kt`, `MainActivity.kt` (Jetpack Compose UI)。
* **ViewModel**: `MainViewModel.kt` (UI 狀態持有者和業務邏輯處理)。

### 組件間交互流程

應用程式的資料和操作流程遵循 MVVM 模式，各主要組件之間的交互如下：

1.  **View (UI 層 - `FileManagerScreen.kt`)**:
    * 捕捉使用者操作（如點擊按鈕、輸入文字）。
    * 呼叫 `MainViewModel` 中對應的函式來處理這些操作。
    * 觀察 `MainViewModel` 中的 `LiveData` 狀態，並根據狀態變化自動更新 UI。

2.  **ViewModel (`MainViewModel.kt`)**:
    * 接收來自 View 的操作請求。
    * 處理業務邏輯（如驗證輸入、準備資料）。
    * 若需執行檔案系統操作，則呼叫 `FileOperationsHelper` 中的方法。
    * 更新內部的 `LiveData`（如檔案列表、載入狀態、訊息）以反映操作結果或狀態變更，進而觸發 View 的更新。

3.  **Model (資料/邏輯層 - `FileOperationsHelper.kt`)**:
    * 執行所有底層的檔案操作（如列舉、複製、移動、刪除、讀取 EXIF、排序）。
    * 使用 Android 的 SAF (`DocumentFile`) 和 `ContentResolver` 與檔案系統互動。
    * 將操作結果或讀取的數據（例如 `List<FileItem>`）返回給 `MainViewModel`。

**簡化流程示意:**

`View (UI)` <-> `ViewModel (業務邏輯 & 狀態)` <-> `Model (檔案操作 & 資料)`

* 使用者操作從 `View` 流向 `ViewModel`。
* `ViewModel` 處理邏輯，可能調用 `Model` 進行資料處理。
* `Model` 返回結果給 `ViewModel`。
* `ViewModel` 更新狀態，`View` 觀察到狀態變化並更新顯示。

## 關鍵組件

* **`MainActivity.kt`**: 應用程式入口點，設定 Compose 主題和內容。
* **`FileManagerScreen.kt`**: 主要 UI 介面，負責顯示檔案列表、操作按鈕、狀態訊息等。
* **`MainViewModel.kt`**: UI 的狀態持有者和業務邏輯處理中心。
* **`FileOperationsHelper.kt`**: 封裝所有底層檔案操作（列舉、複製、移動、刪除、排序、EXIF 讀取）。
* **`FileItem.kt`**: 資料模型，代表一個檔案或資料夾。

## 關鍵資料模型

* **`FileItem`**:
    * `name: String`
    * `uri: Uri`
    * `size: Long`
    * `lastModified: Date`
    * `exifDate: Date?`
    * `isDirectory: Boolean`
* **`SortOrder` (Enum)**: `NAME`, `MODIFICATION_DATE`, `EXIF_DATE`
* **`OperationType` (Enum)**: `COPY`, `MOVE`, `DELETE`

## 主要功能運作流程

### 選擇來源/目標資料夾
1.  使用者點擊選擇按鈕。
2.  `FileManagerScreen` 啟動 SAF (Storage Access Framework)。
3.  使用者透過系統選擇器選擇資料夾。
4.  `FileManagerScreen` 獲取返回的 `Uri` 並請求持久化權限。
5.  `FileManagerScreen` 呼叫 `MainViewModel` 設定 URI。
6.  若設定來源資料夾，`MainViewModel` 觸發檔案載入。

### 載入並顯示檔案列表
1.  `MainViewModel` 的 `loadFiles()` 被呼叫。
2.  `MainViewModel` 設定載入狀態，並在協程中呼叫 `FileOperationsHelper.listFiles()`。
3.  `FileOperationsHelper` 使用 `DocumentFile` 列舉檔案，讀取 EXIF，並根據當前排序方式排序。
4.  `listFiles()` 返回排序後的 `List<FileItem>`。
5.  `MainViewModel` 更新 `files` LiveData 和狀態訊息。
6.  `FileManagerScreen` 的 `LazyColumn` 觀察到 `files` 變化並顯示列表。

### 檔案排序
1.  使用者點擊排序按鈕。
2.  `FileManagerScreen` 呼叫 `MainViewModel.setSortOrder()`。
3.  `MainViewModel` 更新排序狀態並觸發 `loadFiles()`。

### 依編號範圍選擇檔案
1.  使用者輸入起始/結束編號，點擊「選取」。
2.  `FileManagerScreen` 更新 `MainViewModel` 中的範圍輸入值，並呼叫 `selectFilesByRange()`。
3.  `MainViewModel` 驗證輸入，篩選檔案並更新 `filesToOperate` LiveData。
4.  `FileManagerScreen` 根據 `filesToOperate` 標記選中項目。

### 執行檔案操作 (複製/移動/刪除)
1.  使用者點擊操作按鈕。
2.  `FileManagerScreen` 呼叫 `MainViewModel.performOperation()`。
3.  `MainViewModel` 檢查 `filesToOperate` 和目標路徑（若需）。
4.  `MainViewModel` 在協程中遍歷 `filesToOperate`，呼叫 `FileOperationsHelper` 中對應方法。
5.  `FileOperationsHelper` 執行實際檔案操作。
6.  `MainViewModel` 更新狀態訊息，操作完成後重新載入檔案列表並清除選擇。

## 錯誤處理與日誌
* `FileOperationsHelper` 和 `MainViewModel` 中使用 `try-catch` 處理潛在異常。
* 使用 `android.util.Log` 進行調試。
* 透過 `MainViewModel` 的 `statusMessage` LiveData 向使用者反饋操作狀態和錯誤訊息。

## 未來可擴展性/改進建議
* 進階檔案選擇 (點擊多選、全選/取消全選)。
* 大型檔案操作的進度條顯示。
* 更詳細的錯誤提示和恢復機制。
* 檔案/資料夾重新命名、新建資料夾功能。
* 檔案名稱搜尋與篩選功能。
* 設定頁面 (例如預設排序方式、主題選擇)。
* 國際化 (i18n) 支援。
* 為 `MainViewModel` 和 `FileOperationsHelper` 編寫單元測試。
* 為 `FileManagerScreen` 編寫 UI 測試。
