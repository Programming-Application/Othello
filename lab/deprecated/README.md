# lab/deprecated — 不採用の実験（記録用）

ここにある実験は **すべて検証の結果「本番 p26x42 に採用しない」と結論したもの**。
復活させないこと。経緯は `docs/ap26unit2-devlog.md` の Phase F を参照。

## 結論（held-out で測った要点）
- **トポロジ種類重み（薄い版）**: 固定重みより変形盤で控えめに良い程度（~+11%）。重みが不安定でゲイン小。
- **rich トポロジ特徴（F1: adjBlock/openRays 追加）**: thin と同等（held-out で差は誤差）。**効果なし**。
- **phase 別係数（F2）**: 単一係数と同等（held-out 50.8%）。**効果なし**。
- **231 レイアウト個別最適（per-layout 重み表）**: 過適合。held-out で固定並み。**主軸不可**。

→ 6×6＋強い終盤完全読み＋mobility 主体の評価では、**評価の精緻化より探索基盤（TT/PVS/パターン評価）が支配的**。
基本路線（Egaroucid 教科書／指示書の段階的強化）に戻して進める。

## ファイル
- `Tuner42 / Tuner4b42 / TunerF42` … 重み/特徴/トポロジ種類重みの座標降下
- `CeilingF42 / ThickF42` … 個別最適の上限ギャップ計測（過適合の確認）
- `CompareF42 / CompareF2` … rich特徴・phase係数の held-out 公平比較
- `MatchTune42 / MatchTune4b42` … 重み検証
- `p26x42tune/` … 上記のための調整用プレイヤーパッケージ（重み注入・固定深さ・topo/phase モード）

## 残した実験は使わないが、生きている検証ツールは `lab/` 直下にある
- `Bench42`（nodes/sec）, `Match42`（vs サンプル勝率）, `EndBench42`（終盤解の所要時間）。
