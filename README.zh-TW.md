<p>
  <a href="README.md"><img alt="简体中文" src="https://img.shields.io/badge/简体中文-0969da"></a>
  <a href="README.zh-TW.md"><img alt="繁體中文" src="https://img.shields.io/badge/繁體中文-6f42c1"></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/English-1f883d"></a>
</p>

# OneLab

三星 One UI 功能擴充與摺疊螢幕應用程式適配模組。

OneLab 是一款適用於三星裝置的 LSPosed 模組，主要補足系統中無法直接啟用、
缺少自訂入口的功能，並改善部分應用程式在 Galaxy Fold 展開螢幕、分欄與半摺狀態下的使用體驗。

許多 Android 應用程式本身已具備大螢幕、雙欄或摺疊螢幕版面，但這些功能可能受到
機型、系統廠商、地區、螢幕方向或應用程式內部設定限制。OneLab 會盡可能恢復這些
原生功能，並為三星系統補充更多可自訂的入口。

> OneLab 已結束 Beta 階段，目前正式版本為 1.0。
>
> 專案主要在 Samsung Galaxy Z Fold6、One UI 8.0 上進行實機驗證，
> 同時搭配 One UI 8.5 韌體進行相容性檢查。系統韌體或目標應用程式更新後，
> 部分依賴應用程式內部實作的功能仍可能需要重新適配。

## 主要功能

### 網路與連線

- 自訂驗證頁面的關閉延遲

### 效能與溫控

- Enhanced processing 處理速度
- SIOP 效能限頻攔截
- 手動控制 GPU 頻率範圍，並讀取目前裝置實際支援的頻率點
- 遊戲熱預算調整
- SDHMS 隱藏溫控

### 系統介面

- 記住彈出式視窗的位置與大小
- 展開時使用完整外螢幕
- 依應用程式設定螢幕更新率策略
- 依應用程式自訂長寬比
- 應用程式分欄比例
- 外螢幕顯示內容

### 應用程式與摺疊螢幕適配

OneLab 目前已針對 17 款應用程式提供大螢幕、分欄或半摺版面適配，包括攜程、QQ、
Bilibili、小紅書、Instagram、TikTok、網易雲音樂、飛書等等。

- 將部分應用程式既有的摺疊螢幕宣告轉換為三星分割畫面規則
- 改善 QQ、IT之家、虎撲和同程旅行的原生分欄與三星系統開關連動
- 啟用 Bilibili、百度、小紅書和小米商城既有的大螢幕或摺疊螢幕版面
- 啟用 Instagram 和 TikTok 的影片側邊留言區
- 支援 TikTok 直向側欄與直播抽屜避讓
- 讓三星 Fold 的摺疊狀態觸發網易雲音樂內建的半摺播放器
- 支援飛書應用程式內雙欄的左右比例調整
- 為微信、京東、酷安等應用程式提供通用分欄比例支援

QQ、IT之家和虎撲的功能開關由三星系統統一管理：

> 設定 → 進階功能 → 實驗室 → 應用程式分割畫面檢視

其中，IT之家自帶實驗室中的分割畫面檢視開關會與三星系統開關連動。

這些功能並未從 OneLab 中刪除。OneLab 仍會在背景提供摺疊螢幕辨識、
分欄規則、狀態同步和比例支援，只是將開關交由三星系統統一管理。

應用程式本身必須具備相應的大螢幕或分欄程式碼，OneLab 無法為完全沒有大螢幕版面的
應用程式憑空建立一套介面。建議優先使用較新的應用程式版本。

### 實驗功能

- 顯示 Samsung Gallery Labs
- 可選的 Gallery Labs 簡體中文翻譯
- 外螢幕側邊防誤觸參數

Gallery Labs 翻譯不依賴固定的媒體瀏覽器版本號。更新後，只要既有項目名稱保持不變，
通常仍可繼續翻譯；新增且尚未收錄的項目會保留英文，不會影響其他內容。

## 語言支援

OneLab 支援簡體中文、繁體中文和 English。Android 13 及以上系統可以透過
「設定 → 應用程式 → OneLab → 語言」單獨設定介面語言，也可以從 OneLab 的
外觀設定頁面開啟相同的系統入口。

## 使用需求

- Samsung One UI 裝置
- Android 13 或更新版本
- Root
- LSPosed，Xposed API 100

部分功能取決於特定三星服務、硬體功能或目標應用程式版本，因此不一定能在所有裝置上產生相同效果。

## 安裝

1. 下載並安裝 APK。[OneLab 1.0 APK](https://github.com/pigerzhu/OneLab/releases/download/v1.0/OneLab-v1.0.apk)
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

報告也會附上三星分割畫面清單快照、應用程式資格與比例設定對照，
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

## 致謝

感謝以下貢獻者協助翻譯 OneLab：

- English：[@matheuslive](https://github.com/matheuslive)
- 한국어：[@ssch71](https://github.com/ssch71)

## 注意事項

OneLab 包含實驗性系統功能。錯誤的溫控、效能或視窗參數可能導致裝置發熱、耗電增加、
應用程式當機或介面異常。請逐項啟用並確認效果；若發生問題，請先關閉對應功能並重新啟動裝置。
