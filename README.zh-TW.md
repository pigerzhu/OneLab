<p>
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-0969da"></a>
  <a href="README.zh-TW.md"><img alt="繁體中文" src="https://img.shields.io/badge/繁體中文-6f42c1"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f883d"></a>
</p>

# OneLab

三星 One UI 功能擴充與摺疊螢幕應用程式適配模組。

OneLab 是一款適用於三星裝置的 LSPosed 模組，主要補足系統中無法直接啟用、
缺少自訂入口的功能，並改善部分應用程式在 Galaxy Fold 展開螢幕上的使用體驗。

> 目前版本為公開測試版，主要在 Samsung Galaxy Z Fold6、One UI 8.0 上完成驗證。
> 系統韌體或目標應用程式更新後，部分功能可能需要重新適配。

## 主要功能

### 網路與連線

- 自訂驗證頁面的關閉延遲

### 效能與溫控

- Enhanced processing 處理速度
- SIOP 效能限頻攔截

### 系統介面

- 記住彈出式視窗的位置與大小
- 展開時使用完整外螢幕
- 依應用程式設定螢幕更新率策略
- 依應用程式自訂長寬比
- 應用程式分欄比例
- 外螢幕顯示內容

### 應用程式與摺疊螢幕適配

- 三星媒體瀏覽器開發人員 Labs
- Bilibili 大螢幕適配入口
- 小紅書摺疊螢幕首頁與新版影片貼文版面
- QQ 摺疊螢幕版面辨識
- 攜程旅行、航旅縱橫、美團、同程旅行、轉轉的三星分割畫面規則
- 小米商城原生摺疊螢幕功能

### 實驗功能

- 遊戲熱預算調整
- SDHMS 隱藏溫控
- 外螢幕側邊防誤觸參數

## 使用需求

- Samsung One UI 裝置
- Android 13 或更新版本
- Root
- LSPosed，Xposed API 100

部分功能取決於特定三星服務、硬體功能或目標應用程式版本，因此不一定能在所有裝置上產生相同效果。

## 安裝

1. 下載並安裝 APK。[OneLab 0.1.0 Beta 4 APK](https://github.com/pigerzhu/OneLab/releases/download/v0.1.0-beta.4/OneLab-v0.1.0-beta.4.apk)
2. 在 LSPosed 中啟用 OneLab。
3. 依照實際使用的功能設定作用範圍。
4. 重新啟動對應的應用程式；若功能涉及系統框架或三星系統服務，請重新啟動手機。
5. 開啟 OneLab 並設定所需功能。

請勿同時啟用會修改相同設定或 Hook 相同方法的模組。升級前建議保留上一版 APK，方便發生相容性問題時還原。

## 問題回報

遇到問題時，請在 OneLab 設定中的「診斷與意見回饋」依序操作：

1. 點選「開始記錄」。
2. 重現問題。
3. 點選「停止記錄」。
4. 點選「產生並分享」。

報告會儲存到 `下載/OneLab/`。提交 Issue 時請一併提供：

- OneLab 版本
- 手機型號與 One UI 版本
- 目標應用程式名稱與版本
- LSPosed 作用範圍
- 清楚的重現步驟
- 預期結果與實際結果
- 診斷報告

診斷報告會過濾常見的帳號與網路敏感欄位，但上傳前仍建議自行檢查內容。

從 Beta 4 開始，報告也會附上三星分割畫面清單快照、應用程式資格與比例設定對照，
以及裝置執行狀態，方便判斷應用程式未出現在清單中或比例未生效的原因。

## 建置

專案使用 Gradle Wrapper：

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 輸出資料夾：

```text
app/build/outputs/apk/debug/
```

開發規範與目錄慣例請參閱 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 注意事項

OneLab 包含實驗性系統功能。錯誤的溫控、效能或視窗參數可能導致裝置發熱、耗電增加、
應用程式當機或介面異常。請逐項啟用並確認效果；若發生問題，請先關閉對應功能並重新啟動裝置。
