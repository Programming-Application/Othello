# 変形盤面対応オセロAI タスク一覧

この文書は、`docs/direction.md` と `docs/functiondesign.md` の内容をもとに、実装タスクを個別チケット形式へ分解したものである。

---

## Task 1: 提出用パッケージと `OurPlayer` を作成する

### 概要

大会提出条件を満たすため、参考実装 `myplayer/` を土台に、提出用パッケージ `p26xdd` とプレイヤークラス `OurPlayer` を作成する。

`OurPlayer` は `ap26.Player` を継承し、コンストラクタ `public OurPlayer(Color color)` を必ず持つ。大会共通実装である `ap26/` は変更しない。

### Scope

- `p26xdd` パッケージを作成する。
- `p26xdd.OurPlayer` を作成する。
- `OurPlayer` で `ap26.Player` を継承する。
- `public OurPlayer(Color color)` を実装する。
- `think(Board board)` と `setBoard(Board board)` を大会 API に合わせて用意する。
- `RandomPlayer`、`MyGame`、`MyBoardFormatter` は提出対象から外す。
- 直接のファイル I/O、通信、過剰な標準出力を入れない。

### Files

新規作成

- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`
- `p26xdd/MyEval.java`

参考

- `myplayer/MyPlayer.java`
- `myplayer/MyBoard.java`
- `myplayer/MyEval.java`
- `ap26/Player.java`
- `ap26/Board.java`

### 依存関係

- Blocks: Task 2 初期盤面コピーと `BLOCK` 保持
- Blocks: Task 3 `think` の差分盤面同期
- Blocks: Task 5 時間制御付き反復深化探索

### Definition of Done

- `p26xdd.OurPlayer` が `ap26.Player` を継承している。
- `public OurPlayer(Color color)` が実装されている。
- `think(Board board)` が `ap26.Board` インタフェースだけに依存している。
- `setBoard(Board board)` が用意されている。
- `ap26/` 配下を変更していない。
- 提出対象にローカル検証用クラスを含めない方針が明確になっている。

---

## Task 2: 初期盤面コピーと `BLOCK` 保持を実装する

### 概要

`setBoard(Board board)` で大会システムから渡される初期盤面を内部盤面へコピーする。標準盤面だけでなく、`Color.BLOCK` を含む変形盤面もそのまま保持する。

`BLOCK` はゲーム中に変化しないため、初期化時に記録し、以後の `think` 呼び出しで消えないようにする。

### Scope

- `Board` から全 36 マスを読み取る。
- 各マスの状態を内部配列へコピーする。
- `BLOCK` マスを記録する。
- 初期石配置を記録する。
- 持ち時間管理用の状態をリセットする。
- `Board` の具象クラスを仮定しない。

### Files

編集

- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`

参考

- `myplayer/MyBoard.java`
- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 1 提出用パッケージと `OurPlayer` 作成
- Blocks: Task 3 `think` の差分盤面同期
- Blocks: Task 4 合法手生成・着手・反転処理の `BLOCK` 対応

### Definition of Done

- `setBoard(Board board)` で全 36 マスを内部盤面へコピーできる。
- `BLOCK` マスが内部盤面に記録される。
- `BLOCK` が `think` 呼び出しのたびに消えない設計になっている。
- 標準盤面と変形盤面の両方を取り込める。
- `ap26.Board` インタフェース経由で盤面情報を取得している。

---

## Task 3: `think` の差分盤面同期を実装する

### 概要

`think(Board board)` では、毎回全 36 マスをコピーせず、`board.getMove()` を使って前回からの差分だけを内部盤面へ反映する。

相手の直前手を反映し、探索で決めた自分の手も内部盤面へ反映してから返す。これにより、次回の `think` 呼び出し時にも内部盤面が大会盤面と同期された状態になる。

### Scope

- `board.getMove()` で相手の直前の着手を取得する。
- 相手の着手がパスの場合は内部盤面を変更しない。
- 通常の着手の場合は、置かれたマスと反転されたマスを内部盤面へ反映する。
- 探索後、自分の着手を内部盤面へ反映してから返す。
- デバッグ時に、外部盤面と内部盤面の一致を確認できるようにする。

### Files

編集

- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`

参考

- `myplayer/MyPlayer.java`
- `myplayer/MyBoard.java`

### 依存関係

- Blocked by: Task 2 初期盤面コピーと `BLOCK` 保持
- Blocks: Task 5 時間制御付き反復深化探索
- Blocks: Task 9 標準盤面・変形盤面ローカル対戦検証

### Definition of Done

- `think(Board board)` で相手の直前手を内部盤面へ反映できる。
- パスの場合に不要な盤面変更を行わない。
- 自分の着手を内部盤面へ反映してから返している。
- 差分更新後も `BLOCK` が保持される。
- 内部盤面と大会盤面のずれを検証できる。

---

## Task 4: 合法手生成・着手・反転処理を `BLOCK` 対応にする

### 概要

`MyBoard` の合法手生成、着手適用、石の反転、パス、終局判定を変形盤面に対応させる。

`BLOCK` は石を置けないマスであり、反転判定でも探索線を止める障害物として扱う。`BLOCK` を合法手として返したり、`BLOCK` を挟んだ方向で石を反転したりしないようにする。

### Scope

- `BLOCK` マスを合法手候補から除外する。
- `BLOCK` を反転判定の通過不可マスとして扱う。
- `BLOCK` を挟んだ方向で石を反転しない。
- 合法手がない場合はパスを扱う。
- 双方が合法手なしの場合は終局として扱う。
- `findLegalMoves()` が探索中でも正しく使えるようにする。

### Files

編集

- `p26xdd/MyBoard.java`
- `p26xdd/OurPlayer.java`

参考

- `myplayer/MyBoard.java`
- `docs/direction.md`

### 依存関係

- Blocked by: Task 2 初期盤面コピーと `BLOCK` 保持
- Blocks: Task 5 時間制御付き反復深化探索
- Blocks: Task 7 変形盤面対応評価関数

### Definition of Done

- `BLOCK` マスを合法手として返さない。
- `BLOCK` を挟んだ方向で石を反転しない。
- 合法手がない場合に `Move.ofPass(getColor())` を返せる。
- 双方が合法手なしの場合に終局判定できる。
- 標準盤面でも既存の合法手生成・反転処理が破綻しない。

---

## Task 5: 時間制御付き反復深化探索を実装する

### 概要

固定深さの αβ 探索を、1 ゲーム 60 秒の持ち時間に対応した時間制御付き反復深化へ変更する。

探索は深さ 1 から順に実行し、各深さが完了した時点の最善手を暫定最善手として保持する。時間切れが近づいたら探索を打ち切り、最後に完了した深さの合法手を返す。

### Scope

- `Search` クラスを作成する。
- 深さ 1 からの反復深化探索を実装する。
- Negamax + αβ枝刈りを整理する。
- 各深さ完了時に暫定最善手を保存する。
- 1 手の探索制限時間を計算する。
- 締切の 150ms 前に探索を打ち切る。
- 終盤で空きマスが少ない場合は深く読む。

### 実装方針

```text
1手の制限時間 = 残り持ち時間 / 残り空きマス数 × 0.8
ただし、最小 0.1 秒、最大 2.0 秒にクランプする
```

### Files

新規作成

- `p26xdd/Search.java`
- `p26xdd/TimeManager.java`

編集

- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`
- `p26xdd/MyEval.java`

参考

- `myplayer/MyPlayer.java`
- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 3 `think` の差分盤面同期
- Blocked by: Task 4 合法手生成・着手・反転処理の `BLOCK` 対応
- Blocks: Task 6 手順並び替え
- Blocks: Task 8 終盤完全読み

### Definition of Done

- 深さ 1 から順に探索できる。
- αβ枝刈りが探索に組み込まれている。
- 探索中に常に暫定最善手を保持している。
- 時間切れ前に探索を打ち切れる。
- 探索打ち切り時にも合法手を返せる。
- 1 ゲーム 60 秒以内に終局できる設計になっている。

---

## Task 6: 手順並び替えとキラーヒューリスティックを実装する

### 概要

現在のランダムな手順並び替えを改善し、αβ探索の枝刈り効率を高める。

終局で勝てる手、角、疑似角、相手の合法手数を減らす手、安定石を増やす手を優先し、角を取られる可能性が高い危険マスは後回しにする。講義資料から追加する高速化機能として、まずキラーヒューリスティックを導入する。

### Scope

- `MoveOrdering` クラスを作成する。
- 合法手を評価して並び替える。
- 角を取る手を優先する。
- `BLOCK` による疑似角を取る手を優先する。
- 相手の合法手数を減らす手を優先する。
- 危険マスを後回しにする。
- `KillerMoveTable` を作成する。
- 深さごとのキラー手を最大 2 個保存する。

### Files

新規作成

- `p26xdd/MoveOrdering.java`
- `p26xdd/KillerMoveTable.java`

編集

- `p26xdd/Search.java`
- `p26xdd/MyBoard.java`
- `p26xdd/MyEval.java`

参考

- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 5 時間制御付き反復深化探索
- Blocks: Task 8 終盤完全読み
- Blocks: Task 10 ローカル対戦による重み調整

### Definition of Done

- `Collections.shuffle()` のみに依存しない手順並び替えになっている。
- 角を取る手が優先される。
- 疑似角を取る手が優先される。
- 相手の合法手数を減らす手が優先される。
- 危険マスが後回しにされる。
- キラーヒューリスティックが探索に組み込まれている。

---

## Task 7: 局面段階別の複合評価関数を実装する

### 概要

固定マス重みだけに依存した評価関数を拡張し、序盤・中盤・終盤で評価項目を切り替える複合評価関数を実装する。

序盤は機動力を重視し、石を取りすぎる手を抑制する。中盤は角、疑似角、安定石、相手の手数制限を重視する。終盤は実石差と確定勝敗を重視する。

### Scope

- 空きマス数で局面段階を分類する。
- 序盤は機動力を重視する。
- 序盤の石差重みを負にする。
- 中盤は角、疑似角、安定石、相手の手数制限を評価する。
- 終盤は実石差と確定勝敗を評価する。
- 終局評価では勝敗を最優先する。
- 引き分けに負の評価を与える。

### 評価方針

| 局面 | 条件 | 主な評価項目 |
| --- | --- | --- |
| 序盤 | 空きマス `>= 20` | 機動力、石差の抑制 |
| 中盤 | `10 <=` 空きマス `< 20` | 角、疑似角、安定石、相手の手数制限 |
| 終盤 | 空きマス `< 10` | 実石差、確定勝敗 |

終局評価:

- 勝ち: `+1,000,000 + 石差`
- 引き分け: `-10,000`
- 負け: `-1,000,000 + 石差`

### Files

編集

- `p26xdd/MyEval.java`
- `p26xdd/MyBoard.java`

新規作成

- `p26xdd/BoardAnalyzer.java`

参考

- `myplayer/MyEval.java`
- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 4 合法手生成・着手・反転処理の `BLOCK` 対応
- Blocks: Task 10 ローカル対戦による重み調整

### Definition of Done

- 空きマス数で序盤・中盤・終盤を分類できる。
- 序盤で石を取りすぎる手を過大評価しない。
- 機動力を評価に含めている。
- 角と疑似角を評価に含めている。
- 安定石を評価に含めている。
- 終局時は勝敗を最優先して評価する。

---

## Task 8: 疑似角・安定領域の解析を実装する

### 概要

`BLOCK` 付き変形盤面では、通常の角だけでなく、`BLOCK` によって片側が塞がれた辺の石も安定しやすい。

固定表だけでなく、盤面上の `BLOCK` 位置から疑似角や安定領域を計算し、手順並び替えと評価関数に反映する。

### Scope

- `BoardAnalyzer` で `BLOCK` 位置を解析する。
- 通常角を判定する。
- 疑似角を判定する。
- 片側のみ `BLOCK` に接するマスを軽く加点できるようにする。
- 疑似角は通常角より低く評価する。
- 疑似角判定では横方向・縦方向のみを考慮する。
- 斜め方向の `BLOCK` や盤外は安定性判定から除外する。

### 判定方針

| 条件 | 評価 |
| --- | --- |
| 横方向・縦方向ともに盤外 | 通常角 |
| 横方向が盤外、縦方向が `BLOCK` | 疑似角 |
| 縦方向が盤外、横方向が `BLOCK` | 疑似角 |
| `BLOCK` 2方向に接する | 疑似角 |
| 片側のみ `BLOCK` | 軽く加点 |

### Files

新規作成

- `p26xdd/BoardAnalyzer.java`

編集

- `p26xdd/MyEval.java`
- `p26xdd/MoveOrdering.java`
- `p26xdd/MyBoard.java`

参考

- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 2 初期盤面コピーと `BLOCK` 保持
- Blocked by: Task 7 局面段階別の複合評価関数
- Blocks: Task 10 ローカル対戦による重み調整

### Definition of Done

- 通常角を判定できる。
- `BLOCK` による疑似角を判定できる。
- 疑似角を通常角より低く評価できる。
- 斜め方向の `BLOCK` を疑似角判定に使っていない。
- 手順並び替えと評価関数の両方から解析結果を利用できる。

---

## Task 9: 終盤完全読みと勝敗優先評価を強化する

### 概要

終盤では中間評価よりも確定勝敗を重視する。空きマスが少ない場合は深く読み、終局まで読める局面では石数差と勝敗を優先して評価する。

双方が合法手を持たない場合は終局であり、勝てる終局手順が見えた場合は中間評価より優先する。

### Scope

- 空きマスが少ない局面で探索深さを伸ばす。
- 空きマスが 8 以下の場合に完全読みへ移行する。
- パスを含む終盤手順を正しく読む。
- 双方パスを終局として扱う。
- 終局時は `score()` を最優先する。

### Files

編集

- `p26xdd/Search.java`
- `p26xdd/MyEval.java`
- `p26xdd/MyBoard.java`

参考

- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 5 時間制御付き反復深化探索
- Blocked by: Task 7 局面段階別の複合評価関数
- Blocks: Task 10 ローカル対戦による重み調整

### Definition of Done

- 空きマスが少ない局面で深く読める。
- 空きマス 8 以下で完全読みへ移行できる。
- 終盤でも時間制御を守れる。
- パスを含む終盤手順を探索できる。
- 終局評価で勝敗を最優先できる。

---

## Task 10: 標準盤面・変形盤面でローカル対戦検証を行う

### 概要

標準盤面と `BLOCK` 付き変形盤面でローカル対戦を繰り返し、合法手、時間制御、評価関数、探索の安全性を確認する。

まずは `RandomPlayer` に安定して勝てることを確認し、その後、自分の AI 同士で深さや評価重みを変えて対戦する。

### Scope

- 標準盤面でローカル対戦を行う。
- `BLOCK` を 1 から 3 個置いた変形盤面を複数用意する。
- 変形盤面でローカル対戦を行う。
- 反則手の有無を確認する。
- 平均思考時間を確認する。
- 終盤での取りこぼしを確認する。
- 変形盤面での不自然な着手を確認する。
- 標準盤面と変形盤面の両方で 60 秒以内に終局することを確認する。

### Files

編集または利用

- `myplayer/MyGame.java`
- `myplayer/RandomPlayer.java`
- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`
- `p26xdd/Search.java`
- `p26xdd/MyEval.java`

参考

- `docs/direction.md`
- `docs/functiondesign.md`

### 依存関係

- Blocked by: Task 4 合法手生成・着手・反転処理の `BLOCK` 対応
- Blocked by: Task 5 時間制御付き反復深化探索
- Blocked by: Task 7 局面段階別の複合評価関数
- Blocks: Task 11 提出前整理

### Definition of Done

- 標準盤面でローカル対戦できる。
- `BLOCK` 付き変形盤面でローカル対戦できる。
- `BLOCK` を挟んだ方向で石を反転しないことを確認できる。
- `BLOCK` マスを合法手として返さないことを確認できる。
- パスが必要な局面で不正手を返さない。
- 標準盤面と変形盤面の両方で 60 秒以内に終局する。
- 勝率、反則手、平均思考時間、終盤の取りこぼしを確認できる。

---

## Task 11: 提出前整理を行う

### 概要

大会提出前に、提出条件に反する要素を取り除き、`p26xdd` パッケージ配下のプレイヤー実装のみを提出できる状態にする。

`ap26/` は大会共通実装なので変更・同梱しない。直接のファイル I/O、通信、過剰な標準出力、デバッグ出力は削除する。

### Scope

- 提出対象を `p26xdd` パッケージ配下に限定する。
- `ap26/` を変更していないことを確認する。
- `RandomPlayer`、`MyGame`、`MyBoardFormatter` を提出対象から外す。
- デバッグ出力を削除する。
- 直接のファイル I/O を削除する。
- 通信処理がないことを確認する。
- 複数対局が並列実行されても状態が混ざらないよう、不要な `static` 状態を避ける。

### Files

確認

- `p26xdd/OurPlayer.java`
- `p26xdd/MyBoard.java`
- `p26xdd/MyEval.java`
- `p26xdd/Search.java`
- `p26xdd/MoveOrdering.java`
- `p26xdd/TimeManager.java`
- `p26xdd/BoardAnalyzer.java`
- `p26xdd/KillerMoveTable.java`
- `ap26/`

### 依存関係

- Blocked by: Task 10 標準盤面・変形盤面ローカル対戦検証

### Definition of Done

- `p26xdd` パッケージ配下のプレイヤー実装のみを提出対象にできる。
- `ap26/` を変更していない。
- デバッグ出力が提出対象から削除されている。
- 直接のファイル I/O が提出対象にない。
- 通信処理が提出対象にない。
- 過剰な標準出力が提出対象にない。
- `OurPlayer` が大会 API に準拠している。
