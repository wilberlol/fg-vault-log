# FGVaultLog

獨立的 Purpur/Paper 1.20.1 Vault Economy 金流稽核插件。它不依賴 FGFarming，也不讀取 FGFarming 的類別、設定或資料庫。插件只觀測並記錄，不提供也不主動執行任何存款/提款操作。

## 功能

- 以高優先權註冊透明 Vault `Economy` 代理。
- 只記錄玩家 `depositPlayer`、`withdrawPlayer` 的成功與失敗結果；銀行 API 只原樣轉發，不寫入玩家金流紀錄。
- 記錄交易 UUID、時間、玩家 UUID/名稱、世界、方向、要求金額、實際金額、前後餘額、Vault event method、provider、來源插件、來源 class/method 與錯誤訊息。
- SQLite 非同步寫入：資料位於 `plugins/FGVaultLog/transactions.db`。
- `/vaultlog status` 查看目前是否已掛接 Economy provider。
- `/vaultlog latest [玩家] [頁數]` 查詢玩家金流與詳細 event。
- `/vaultlog reload` 重新載入 `config.yml`。

## 安裝

1. 伺服器使用 Purpur 1.20.1、Java 17 或以上。
2. 安裝 Vault，以及一個真正提供 Vault Economy 的插件，例如 EssentialsX Economy。
3. 將 `FGVaultLog-1.0.0.jar` 放入 `plugins/`。
4. 重啟伺服器後執行 `/vaultlog status`，確認顯示 `已掛接`。

Purpur 會依照 `plugin.yml` 的 `libraries` 自動載入 SQLite JDBC；若伺服器版本不支援 libraries，請改用能提供 SQLite JDBC 的啟動環境，或告知我再改成 shaded jar 版本。

## 指令

```text
/vaultlog status
/vaultlog latest
/vaultlog latest Steve 1
/vaultlog reload
```

所有指令需要 `vaultlog.admin`，預設只有 OP。插件本身沒有存款或提款指令。

## 重要限制

Vault API 沒有標準的全域交易事件。這個插件採用 Economy service proxy，因此能記錄「其他插件透過 Vault service 取得的代理所呼叫」的玩家金流，並把 `event_name`、來源插件、來源 class/method、餘額與結果寫入 SQLite。以下情況可能無法攔截：

- 其他插件在 FGVaultLog 掛接前就快取了原本的 Economy provider。
- 其他插件直接呼叫 EssentialsX、CMI 等經濟插件自己的 API，而不是 Vault。
- 另一個插件在 FGVaultLog 之後以更高優先權覆蓋 Economy service。

Vault 不會把「商店購買、任務獎勵、指令提款」等人類可讀原因傳給代理；本插件的詳細 event 是實際 Vault API method 與呼叫端 class/method。若要得到商品 ID 或任務 ID，來源插件必須自己提供事件/API。`/vaultlog status` 會顯示實際掛接的 provider；若需要百分之百完整的帳本，應在真正的 Economy provider 或所有金流來源加入原生事件/API，而不能只依靠 Vault。
