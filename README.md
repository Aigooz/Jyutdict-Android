# 粤辞 · Jyutdict-Android 非官方维护版

**原作者：弋琉希霅。原始开源仓库：[JyutdictEB/Jyutdict-Android](https://github.com/JyutdictEB/Jyutdict-Android)。感谢原作者与上游贡献者的工作。**

本仓库由 [Aigooz](https://github.com/Aigooz) 维护，基于原项目修改，非原作者官方发行版。沿用 [MIT 许可证](LICENSE)，完整保留原作者版权声明。内容来源及许可边界见 [NOTICE.md](NOTICE.md)。

- [下载安装包](https://github.com/Aigooz/Jyutdict-Android/releases/latest)：选择 `Yueci-Android-dual-debug.apk`，无需登录、授权码或密钥。
- [公开源码](https://github.com/Aigooz/Jyutdict-Android) 与 [问题反馈](https://github.com/Aigooz/Jyutdict-Android/issues)。
- 应用 ID 为 `cc.ecisr.jyutdict.dual`，可与原版共存；发布安装包沿用本分支既有签名，可覆盖此前双开版本。
- 更新由 App 前台及 Android 定期任务检查；后台时效受系统省电、网络和通知权限影响。

## 本分支功能

主词典检索、地区归类、繁简显示、深色主题、搜索历史与词条收藏、搜索归一化、台山话网站查询、本机词库导入、按需联网录音，以及公开更新检查和下载。

### 本机词库

公开版本不附带未获得再分发授权的《台山方言词典》抽取数据，也不批量镜像网站词库或音频。无本机词库时默认使用网站模式。持有合法可用 JSON 的用户可在台山话页右上角菜单中导入，最大 16 MiB。

文件为 UTF-8 JSON，顶层含 `entries` 数组。每条至少有唯一非空 `id` 和非空 `search` 字段；其余字段为 `headword`、`ipa_display`、`ipa`、`definition`、`category`、`subcategory`、`book_page`。示例结构（不含书籍内容）：

```json
{"entries":[{"id":"user-1","headword":"示例","ipa_display":"","ipa":"","definition":"用户自有资料","category":"自建","subcategory":"","book_page":0,"search":"示例 用户自有资料"}]}
```

导入在本机验证并原子替换，失败保留已有词库。升级前旧 APK 内置的书籍数据不会自动转存，需自行导入合法持有的文件。已保存的历史、收藏及录音不受影响。音频仍按需联网，不下载整库。

## 编译与发布

需要 Android SDK 37、JDK 21（本地 Windows 单元测试也可使用 JDK 17），以及可访问 Google/Maven 的网络。

```sh
git clone https://github.com/Aigooz/Jyutdict-Android.git
cd Jyutdict-Android
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Windows 使用 `gradlew.bat`。在 Android Studio 配置 SDK，或在未跟踪的 `local.properties` 中设置 `sdk.dir`。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。自编译默认使用自己的调试签名，不能覆盖安装维护者签名的版本；发布正式更新需使用同一私有签名，不得提交签名文件。

维护者推送 `master` 后，GitHub Actions 测试、构建并向**当前公开仓库**发布 Release。构建号为 `1000 + github.run_number`，新仓库必须从高于既有渠道的构建号继续。仓库 Secret `ANDROID_DEBUG_KEYSTORE_B64` 仅供 CI 签名；Release 使用 GitHub 自动提供的 `GITHUB_TOKEN`，用户端不需要任何令牌。Fork 后须配置自己的签名及 `appUpdateRepo`，不能沿用本维护分支的签名指纹检查。

本公开分支保留上游历史；因旧私有分支含未授权公开的数据，新增功能以整理后的提交接在上游之后，不公开旧私有历史。后续代码直接在本公开仓库持续维护，不需要向两个仓库重复推送。

## 上游说明与历史

## 泛粵大典

[Jyutdict](https://www.jyutdict.org) 的安卓版本

API 基於 [API List](https://www.jyutdict.org/api/)

可檢索：通語字+音和泛粵字表中的錔字+綜合音+各地音（若有）+釋義，詳見 [關於泛粵典](https://www.jyutdict.org/about)。

## 版本歷史

### v0.8.24

- 修復台山話詞典把「無匹配錄音」誤報為查詢失敗
- 確認失效的來源錄音會暫時從候選中排除，避免重複播放失敗
- 區分錄音不存在、來源連結失效與網絡或播放錯誤

### v0.8.23/261008

- 台山話詞典接入 Stephen Li 台山話詞典的線上參考錄音
- 按原詞查詢，分別展示來源音標與釋義；未確認同音時需選擇試聽
- 用戶本機錄音優先；缺失錄音與失效連結有獨立提示
- 不將普通粵語錄音代作台山話，也不拼接單字代作整詞

### v0.8.21/261007

- 重新設計更新提示與下載進度彈窗
- 更新說明改為分節與條列顯示
- 主界面搜索不再展示台山話結果

### v0.8.20/261007

- 主界面搜索會同時展示台山話結果
- 離線結果最多先顯示 5 筆，並可跳轉查看全部
- 網絡查詢失敗時仍可查看本地台山話結果

### v0.8.19/261006

- 新增台山話詞典頁
- 內置 4521 條離線詞條
- 支持按 33 個大類篩選
- 支持按詞頭、讀音和釋義搜索
- 保留音標、釋義和原書頁碼

### v0.8.3/260816

- 跟上網頁端的功能：
  - 地點介紹
  - 評論
  - 查音
- 優化與查詢結果的交互
  - 對過淡或過深的地點顔色描邊
  - 長按與短按的觸發區域更加正確了
  - 地點讀音不會再被從内部換行了
  - 通表正確對齊地名
  - 粵表的讀音注釋可以完整展開了
- 低時效性内容的緩存
- 反查釋義時可以無視例句
- 關於頁會從綫上獲取

### v0.6.7/230102

全面使用 HTTPS（原使用 HTTP 部分與上行傳輸無關）
*舊版本不再可用*

### v0.6.6/220811

- 修復：查音崩潰
- 改進數處顯示

### v0.6.5/220609

- 修復：橫屏、切換主題時保留原結果
- 自由複製字項內容
- 允許使用檢索音
優化通表的顯示：
- 對地區和字音著色
- 篩選地點

### v0.5.0/211007

修復幾個崩潰 bugs

- 橫屏顯示
- 黑暗主題
- 支持單元格附註的顯示

### v0.3.7/210205

修復幾個顯示 bugs
泛粵表部分支持隨機展示
加了個輸入清空按鈕
加了指示無結果的 Toast

### v0.3.1/210113

詞場顯示開關
支持顯示 IDS 和詞性標記
優化錔字來源的顯示

### v0.2.7b/201119

同時顯示域外音作爲參考

### v0.2.6/200721

首個公開的版本
