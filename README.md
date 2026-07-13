# FGVaultLog

獨立的 Purpur/Paper 1.20.1 Vault Economy 金流稽核插件。它不依賴 FGFarming，也不讀取 FGFarming 的類別、設定或資料庫。

## 功能

- 以高優先權註冊透明 Vault `Economy` 代理。
- 記錄 `depositPlayer`、`withdrawPlayer`、`bankDeposit`、`bankWithdraw` 的成功與失敗結果。
- 記錄交易 UUID、時間、操作、玩家/銀行、世界、要求金額、實際金額、前後餘額、provider、來源插件與錯誤訊息。
- SQLite 非同步寫入：資料位於 `plugins/FGVaultLog/transactions.db`。
- `/vaultlog status` 查看目前是否已掛接 Economy provider。
- `/vaultlog latest [玩家或銀行] [頁數]` 查詢最近金流。
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

所有指令需要 `vaultlog.admin`，預設只有 OP。

## 重要限制

Vault API 沒有標準的全域交易事件。這個插件採用 Economy service proxy，因此能記錄「其他插件透過 Vault service 取得的代理所呼叫」的金流。以下情況可能無法攔截：

- 其他插件在 FGVaultLog 掛接前就快取了原本的 Economy provider。
- 其他插件直接呼叫 EssentialsX、CMI 等經濟插件自己的 API，而不是 Vault。
- 另一個插件在 FGVaultLog 之後以更高優先權覆蓋 Economy service。

`/vaultlog status` 會顯示實際掛接的 provider；若需要百分之百完整的帳本，應在真正的 Economy provider 或所有金流來源加入原生事件/API，而不能只依靠 Vault。
