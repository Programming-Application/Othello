# プロジェクト概要：プログラミング応用杯 オセロ AI フレームワーク

2026年度「プログラミング応用杯」用のオセロ AI 対戦フレームワーク。**6×6 盤**のオセロで複数の AI が総当たり戦をする。

---

## ap26/ パッケージ（改変禁止の共通フレームワーク）

### Board.java
6×6 盤面を表す**インタフェース**。マス番号は `k = 6×row + col`（0〜35）。

| メソッド | 役割 |
|---|---|
| `get(k)` | マスの状態（BLACK/WHITE/NONE/BLOCK）を返す |
| `findLegalMoves(color)` | 合法手を全列挙（パスのみなら PASS を含むリスト） |
| `placed(move)` | 着手後の**新しい盤面**を返す（元は不変） |
| `flipped()` | 黒白を反転した新盤面（対称性利用の探索テクニック用） |
| `isEnd()` / `winner()` | 終局判定・勝者 |
| `score()` | 黒石数 − 白石数（全滅時は空マスも加算） |
| `foul(color)` | 反則時に全マスを相手色で埋める |

変形盤面では `Color.BLOCK`（石を置けない障害マス）が存在する。

---

### Color.java
4状態の列挙型：

| 定数 | 値 | 表示 | 意味 |
|---|---|---|---|
| `BLACK` | +1 | `o` | 先手（黒石） |
| `WHITE` | -1 | `x` | 後手（白石） |
| `NONE` | 0 | ` ` | 空マス |
| `BLOCK` | +3 | `#` | 障害マス |

`getValue()` を使えば `weight[k] * board.get(k).getValue()` の1行で評価値計算できる（BLOCK は +3 なので明示的に除外が必要）。

---

### Move.java
「マス番号 + 色」で1手を表すクラス。

- 通常手：index = 0〜35
- 特殊手：PASS(-1)、ERROR(-2)、TIMEOUT(-3)、ILLEGAL(-100〜)
- 棋譜表記（`"d3"` など）↔ index の相互変換機能あり
- `adjacent(k)` で隣接マス一覧、`line(k, dir)` で方向別の延長線を取得できる

---

### Player.java
学生が継承すべき**抽象基底クラス**。

```
1. コンストラクタ(Color color) で生成
2. ゲーム開始 → setBoard(board) が呼ばれる
3. 手番ごと → think(board) が呼ばれる → Move を返す
```

`think(Board board, long remainingTimeMs)` を override すると残り時間を活用した高度な実装が可能。

---

### ResourceLoader.java
学習済みモデルなどの静的リソースを読み込むユーティリティ。

- `open(getClass(), "model.bin")` → InputStream
- `readAllBytes(...)` / `readString(...)` で一括読み込みも可
- 書き込み系 API は一切なし（カンニング防止）
- **上限 10MB**（超えると IOException）
- `java.io.File*` や `java.nio.file.Files` の直接使用は禁止

---

## ap26/league/ パッケージ（対戦システム）

### League.java
リーグ全体の司令塔。

- **総当たり戦**：全ペア (i, j) × 全盤面 × 黒/白交互 でゲームを生成
- **盤面**：標準盤(#0) ＋ 変形盤(#1, #2...) — 変形盤は上辺・左辺にランダムで BLOCK を1〜3個配置
- **並列実行**：`PARALLELISM=2` で同時2ゲームを ForkJoinPool で実行
- 各プレイヤーは**独立した JVM プロセス**で動作（Socket通信）→ static 変数の干渉なし、暴走しても他に影響なし
- **勝ち点**：勝=`10+min(石差,10)` / 引=5 / 負=0

---

### Game.java
**同一 JVM 内**で動く簡易ゲームドライバ（テスト・デバッグ用）。

- `play()` → 終局まで黒白交互に `think()` を呼ぶ
- 反則検出（例外・タイムアウト・不正手）→ 即時 `foul()` で終了
- 本番は `ProxyGame`（Socket通信版）が使われる

---

### RandomPlayer.java
合法手をランダムに選ぶ**最弱ベースライン**。名前は `"R"`。新プレイヤーの動作確認の第一関門。

---

## p26x00/ パッケージ（サンプル学生プレイヤー）

### OurBoard.java
`Board` インタフェースの学生側実装例（`Color[]` 配列で盤面を保持）。

- `placed(move)` で石を裏返す処理（ラインを8方向スキャン）
- `flipped()` で黒白反転コピー
- `findNoPassLegalIndexes()` で BLOCK/NONE を考慮した合法手列挙
- 全メソッドがイミュータブル（元盤面は変更しない）

---

### OurPlayer.java
**α-β 探索（深さ4）+ 位置評価関数**のサンプル実装。名前は `"2500"`。

#### 評価関数 `MyEval`

```
位置重みテーブル M (6×6):
+10 +10 +10 +10 +10 +10   ← 角・辺は高評価
+10  -5  +1  +1  -5 +10   ← C マス（角の隣）は低評価
+10  +1  +1  +1  +1 +10
+10  +1  +1  +1  +1 +10
+10  -5  +1  +1  -5 +10
+10 +10 +10 +10 +10 +10
```

スコア = Σ(weight[k] × color.getValue())、終局時は石差 × 1,000,000

#### 探索アルゴリズム

- `maxSearch` / `minSearch` の相互再帰 α-β 探索
- 「常に黒視点」で探索するため、黒番ならそのまま、白番なら `flipped()` して使う
- `order()` でシャッフル（簡易 move ordering）
- 非合法手検出時のフォールバック処理あり（デバッグ用エラー出力 ＋ 最初の合法手を選択）

---

### OurBoardFormatter.java
盤面のテキスト表示クラス。現在の手番の合法手に `.` を表示し、直前手を大文字で強調表示する。

---

## Competition26.java（エントリポイント）

```java
// 現在の設定
new p26x42.OurPlayer(color),         // チーム42（メイン）
new p26x00.OurPlayer(color),         // サンプル（比較用）
new ap26.league.RandomPlayer(color), // ベースライン
new p26x06.OurPlayer(color)          // 別チーム
```

- `NUM_BOARD = 3`（標準盤1 + 変形盤2）
- `TIME_LIMIT_SECONDS = 60`（1ゲーム60秒）
- 上記4プレイヤーで総当たり → `League.run()` を呼ぶだけ

---

## 全体の関係図

```
Competition26 (エントリポイント)
    └── League (総当たり戦の管理)
         ├── 盤面生成: OfficialBoard (#0 標準, #1〜 変形)
         ├── 各ゲーム: ProxyGame (Socket通信 / プロセス分離)
         │    └── think() 呼び出し → Player (別JVM)
         └── 結果集計: GameStatistics → 勝ち点表示

ap26.Player ← 学生が継承する基底クラス
    ├── ap26.league.RandomPlayer (ランダム)
    ├── p26x00.OurPlayer (α-β depth=4, 位置評価)
    ├── p26x06.OurPlayer (別チームの実装)
    └── p26x42.OurPlayer (このリポジトリの本命実装)
```

---

## まとめ

| 要素 | 概要 |
|---|---|
| **競技形式** | 6×6 オセロ、変形盤あり、プロセス分離で並列総当たり |
| **実装義務** | `ap26.Player` を継承し `think(Board)` を override |
| **参照実装** | `p26x00` = 位置重みテーブル + α-β depth=4（ベースラインより強い） |
| **強化の方向** | 探索深さ増加・α-β枝刈り改善・終盤完全読み・評価関数改良 |
| **制約** | `ap26` パッケージ改変禁止・ファイルI/Oは `ResourceLoader` のみ・思考時間60秒/ゲーム |
