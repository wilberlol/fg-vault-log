# FGVaultLog

獨立的 Purpur/Paper 1.20.1 金流稽核插件。它不依賴 FGFarming，也不讀取 FGFarming 的類別、設定或資料庫；插件只觀測並記錄，不提供存款或提款指令。

## 功能

- 以 Vault Economy service proxy 記錄玩家 `depositPlayer`、`withdrawPlayer` 呼叫，包含成功與失敗結果。
- CMI 存在時，額外接收 CMI 原生 `CMIUserBalanceChangeEvent`，所以 CMI 的 `/money give`、`/money take`、`/money set` 等原生命令也能留下紀錄。
- CMI 事件與 Vault 事件若是同一筆變更，會自動短時間去重，避免重複寫入。
- 內建餘額快照兜底：即使某個插件直接呼叫經濟插件 API，也能偵測玩家餘額差額。
- 銀行 API 只原樣轉發，不寫入玩家金流紀錄。
- 記錄交易 UUID、時間、玩家 UUID/名稱、世界、方向、要求金額、實際金額、前後餘額、事件名稱、來源插件、來源 class/method 與訊息。
- SQLite 非同步寫入：資料位於 `plugins/FGVaultLog/transactions.db`。
- `/vaultlog status` 查看 Vault 與 CMI event bridge 狀態。
- `/vaultlog latest [玩家] [頁數]` 查詢玩家金流與詳細 event。
- `/vaultlog reload` 重新載入 `config.yml`。

## 安裝

1. 伺服器使用 Purpur 1.20.1、Java 17 或以上。
2. 安裝 Vault，以及一個真正提供 Vault Economy 的插件。
3. 若要記錄 CMI 原生命令，另外安裝 CMI；CMI 是可選依賴。
4. 將 `FGVaultLog-1.1.0.jar` 放入 `plugins/`。
5. 重啟伺服器後執行 `/vaultlog status`，確認 Vault 顯示已掛接、CMI event 顯示 registered。

Purpur 會依照 `plugin.yml` 的 `libraries` 自動載入 SQLite JDBC。

## 指令

```text
/vaultlog status
/vaultlog latest
/vaultlog latest Wilberlol 1
/vaultlog reload
```

所有指令需要 `vaultlog.admin`，預設只有 OP。插件本身沒有存款或提款指令。

## 設定

```yaml
cmi:
  enabled: true

fallback:
  enabled: true
  poll-interval-ticks: 100
```

關閉 `cmi.enabled` 後仍會保留 Vault Economy proxy 紀錄，但不會接收 CMI 原生餘額事件。
`fallback.enabled` 會啟用快照兜底；它只能知道前後餘額差額，無法事後還原沒有提供 event/API 的插件原因。這類紀錄的 `event` 會是 `UNKNOWN_EXTERNAL_CHANGE`。

## 重要限制

Vault API 沒有標準的全域交易事件。Vault proxy 能記錄其他插件透過 Vault Economy 呼叫的玩家金流；CMI 則透過自己的原生餘額事件補足直接由 CMI 處理的命令。

其他經濟插件若直接修改自己的資料庫或 API，且沒有使用 Vault、沒有可接收的原生事件，任何獨立的通用插件都無法在不修改該插件的情況下保證攔截。這時需要為該經濟插件增加對應的 event bridge。

因此本插件的完整覆蓋範圍是：Vault 呼叫會記錄呼叫來源插件與 class/method；已支援的原生事件會記錄事件細節；完全繞過兩者的變更則由快照偵測並標記為未知原因。若要求每一筆都是真實原因，所有金流來源必須使用共同的 Vault/API 入口，或提供可接收的事件。

CMI 的來源使用者或人類可讀命令資訊可能是空值，因此插件會安全地記錄 `sourceUser=unknown`，但仍會保存 action type、from、to、事件名稱與 CMI provider。
