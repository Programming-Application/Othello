## 概要 (What / Why)

<!-- 1-3行で、何をなぜ変更したかを記載してください。 -->

- 

## 変更対象

<!-- 変更した領域にチェックしてください。 -->

- [ ] `p26xdd/` 提出用プレイヤー実装 (`OurPlayer` / `MyBoard` / `MyEval` など)
- [ ] `p26xdd/resources/` 評価値・学習済みモデルなどの読み込み専用リソース
- [ ] `Competition26.java` ローカル対戦・検証用の参加プレイヤー設定
- [ ] `docs/` 設計書・タスク・ルールなどのドキュメント
- [ ] `p26x00/` サンプル実装
- [ ] `ap26/` 共通API・対戦基盤
- [ ] その他

## 追加 / 変更したリソース

<!-- 例: `p26xdd/resources/weights.csv` / なし -->

- なし

## テスト

### Java / 対戦システム

- [ ] `mkdir -p bin && javac -encoding UTF-8 -d bin $(find . -name "*.java")`
- [ ] `java -cp "bin:." Competition26`
- [ ] `pgrep -f "ap26.league.proxy.PlayerMain" | wc -l` でプレイヤーJVMが残っていないことを確認
- [ ] 標準盤面で合法手・パス・終局処理を確認
- [ ] `BLOCK` を含む変形盤面で合法手・反転処理を確認
- [ ] 時間超過せずに `think(Board board)` が手を返すことを確認
- [ ] 対象外

### 提出ルール確認

- [ ] `p26xdd.OurPlayer` が `ap26.Player` を継承している
- [ ] `public OurPlayer(Color color)` / `think(Board board)` / `setBoard(Board board)` を実装している
- [ ] `ap26/` 配下を変更していない、または変更理由をレビュー観点に記載している
- [ ] 直接のファイルI/O・通信・外部サービス利用を追加していない
- [ ] リソース読み込みは `ap26.ResourceLoader` 経由にしている
- [ ] 過剰な標準出力・標準エラー出力を残していない
- [ ] 対象外

## レビュー観点 (任意)

<!-- 特に注目してほしい箇所があれば記載してください。 -->

- 

## スクリーンショット / 実行結果 (任意)

<!-- UI変更は通常ないため、対戦結果やログ抜粋があれば記載してください。 -->

- なし
