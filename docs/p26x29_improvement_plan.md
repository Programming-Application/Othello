# p26x29 改善案: 評価関数と探索切り替えの弱点対策

## 目的

`p26x29.OurPlayer` は、終盤の読み切り性能は高い一方で、空き 25 以上の局面では静的評価関数に依存している。この範囲で評価関数が誤りやすい形を作られると、完全読みまたは WLD 証明へ入る前に不利な終盤へ誘導される。

本書では、次の弱点を前提に、`p26x29` をより崩されにくくするための具体的な修正案とアルゴリズムを記述する。

- 空き 25 以上はヒューリスティック探索のみで、終局結果を保証しない。
- `CMOB = 31` により mobility を強く評価するため、短期的な合法手数差に過剰反応しやすい。
- `CFRONT = -20` により frontier を嫌うため、あえて frontier を増やすが辺・角・パリティで得をする手を過小評価しやすい。
- WLD 領域では勝敗だけを見るため、同じ勝ちの中で石差や安全度を最大化しない。

## 現状コードの根拠

`p26x29/OurPlayer.java` の `MyEval.value()` は、非終局局面を次の線形和で評価している。

```java
value = CPOS * pos + CMOB * mob + CFRONT * front + CSTAB * stableDiff(b)
```

現在の係数は次の通りである。

```java
static final int CPOS = 10, CMOB = 31, CFRONT = -20, CSTAB = 20;
```

探索切り替えは `searchBestMove()` で行われる。

- `empties <= 20`: `solveExactRoot()` で最終石差を完全読みする。
- `21 <= empties <= 24`: `wldRoot()` で勝敗のみを証明する。
- `empties >= 25`: 反復深化 + PVS + 中盤評価関数で選ぶ。

つまり、空き 25 以上では「位置、mobility、frontier、簡易安定石」の近似評価が最終判断になる。ここが対戦相手に狙われる主な箇所である。

## 修正方針

### 1. 評価関数を局面段階別にする

固定係数のままだと、空き 25 以上でも mobility が強すぎる。空き数に応じて係数を切り替え、終盤へ近づくほど parity、角アクセス、辺安定、危険マスの文脈評価を強める。

推奨する段階は次の通り。

| 段階 | 条件 | 主目的 |
| --- | --- | --- |
| 序盤 | 空き 29 以上 | 石を増やしすぎず、悪形を避ける |
| 中盤前半 | 空き 25-28 | mobility を見るが過信しない |
| 中盤後半 | 空き 21-24 より前、特に 25 付近 | WLD/完全読みに入る形を整える |
| WLD | 空き 21-24 | 勝敗証明 + 勝ち手の質でタイブレーク |
| 完全読み | 空き 20 以下 | 最終石差最大化 |

### 2. mobility を「数」ではなく「質」で補正する

現在は合法手数差 `mob` のみを評価している。これに次の補正を加える。

- 相手の合法手が多くても、X マス、C マス、角を渡す手が多いなら相手 mobility を過大評価しない。
- 自分の合法手が多くても、危険マスばかりなら自分 mobility を過大評価しない。
- 中盤後半では、次に相手へ角を渡す手を強く減点する。

### 3. frontier を絶対悪にしない

`CFRONT = -20` は frontier を強く嫌う。これは一般には有効だが、辺を固める準備、偶奇調整、相手の危険手誘導では frontier 増加が必要になる。

修正後は、frontier 差に一律の重みを掛けるのではなく、次のように分ける。

- 内部 frontier: 従来通り減点。
- 辺 frontier: 角が安全または辺が連結している場合は減点を弱める。
- 罠 frontier: 相手の X/C 着手を誘う場合は軽く加点する。

### 4. WLD の勝ち手を石差・安全度でタイブレークする

`wldRoot()` は勝ち、引分、負けの順に手を選び、勝ちが見つかると打ち切れる。これは速いが、複数の勝ち手の中で石差や安全性を見ない。

WLD 領域では次の方針にする。

1. まず全合法手を WLD で分類する。
2. 勝ち手が複数あれば、浅い exact または中盤評価でタイブレークする。
3. 引分手が複数あれば、相手のミスを誘いやすい形を選ぶ。
4. 全手負けなら、従来通りヒューリスティック探索で粘る。

## 具体的アルゴリズム

### A. 段階別評価 `phaseEval`

`MyEval.value()` を空き数付きの評価に変更する。

```java
int value(OurBoard b) {
  int empties = Long.bitCount(b.empty());
  Features f = extractFeatures(b);
  PhaseWeights w = weightsFor(empties);

  return w.pos * f.pos
       + w.mob * qualityMobility(b)
       + w.front * safeFrontier(b)
       + w.stable * stableDiff(b)
       + w.parity * parityScore(b)
       + w.cornerAccess * cornerAccessScore(b)
       + w.danger * dangerSquareScore(b);
}
```

推奨初期値は次の通り。

| 空き数 | pos | mob | front | stable | parity | cornerAccess | danger |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 29 以上 | 10 | 24 | -16 | 12 | 0 | 18 | 24 |
| 25-28 | 10 | 20 | -12 | 18 | 8 | 24 | 32 |
| 21-24 | 8 | 14 | -8 | 24 | 18 | 36 | 44 |
| 20 以下 | exact に委譲 | exact に委譲 | exact に委譲 | exact に委譲 | exact に委譲 | exact に委譲 | exact に委譲 |

狙いは、空き 25 付近で `CMOB = 31` による短期 mobility 過信を下げ、WLD/完全読みへ入る形の良さを評価へ入れることである。

### B. mobility の質補正 `qualityMobility`

合法手数差をそのまま使わず、危険手を割り引く。

```java
int qualityMobility(OurBoard b) {
  int black = mobilityQuality(b, BLACK);
  int white = mobilityQuality(b, WHITE);
  return black - white;
}

int mobilityQuality(OurBoard b, Color c) {
  int[] moves = tmpMoves;
  int n = b.genLegal(c, moves);
  int score = 0;

  for (int i = 0; i < n; i++) {
    int m = moves[i];
    int q = 1;

    if (isCorner(m)) q += 5;
    if (isSafeEdgeMove(b, c, m)) q += 2;
    if (givesOpponentCorner(b, c, m)) q -= 6;
    if (isXSquare(m) && adjacentCornerEmpty(b, m)) q -= 4;
    if (isCSquare(m) && adjacentCornerEmpty(b, m)) q -= 2;

    score += q;
  }
  return score;
}
```

この補正により、「相手の合法手が多いが、その多くが危険手」という局面を正しく扱いやすくなる。

### C. frontier の安全度補正 `safeFrontier`

frontier 石数差を一律に減点せず、辺と角の文脈を見る。

```java
int safeFrontier(OurBoard b) {
  long empty = b.empty();
  long frontierMask = neighborMask(empty);

  int black = frontierPenalty(b, BLACK, frontierMask);
  int white = frontierPenalty(b, WHITE, frontierMask);
  return black - white;
}

int frontierPenalty(OurBoard b, Color c, long frontierMask) {
  long stones = stonesOf(b, c) & frontierMask;
  int penalty = 0;

  while (stones != 0) {
    int k = Long.numberOfTrailingZeros(stones);
    stones &= stones - 1;

    if (isStableFromCornerOrBlock(b, c, k)) {
      penalty += 0;
    } else if (isEdge(k)) {
      penalty += 1;
    } else {
      penalty += 2;
    }
  }
  return penalty;
}
```

返り値は従来の `front = blackFront - whiteFront` と同じ向きにし、重みは負にする。これにより、安全な辺 frontier まで強く嫌う挙動を抑える。

### D. parity 評価 `parityScore`

空き領域を連結成分に分け、奇数空き領域を終盤で重視する。

```java
int parityScore(OurBoard b) {
  long empties = b.empty();
  int score = 0;

  while (empties != 0) {
    long region = floodFillOneEmptyRegion(empties);
    int size = Long.bitCount(region);
    empties &= ~region;

    if ((size & 1) == 1) {
      score += regionParityOwnership(b, region);
    } else {
      score -= regionRisk(b, region);
    }
  }
  return score;
}
```

`regionParityOwnership` は、領域周辺でどちらが最後に打ちやすいかを簡易判定する。初期実装では次でよい。

- 領域に自分だけが合法手を持つなら `+2`。
- 相手だけが合法手を持つなら `-2`。
- 両者が持つなら `+1` または `-1` を mobility quality で決める。

6x6 では空き領域が小さくなりやすいため、この簡易 parity でも空き 25 付近の終盤形成に効きやすい。

### E. 危険マス文脈評価 `dangerSquareScore`

X マス、C マスを固定で悪く見るのではなく、隣接角の状態で評価する。

```java
int dangerSquareScore(OurBoard b) {
  int score = 0;
  for (int m : X_AND_C_SQUARES) {
    Color owner = b.get(m);
    if (owner != BLACK && owner != WHITE) continue;

    int corner = adjacentCorner(m);
    int v;
    if (b.get(corner) == owner) {
      v = 2;       // 自角に接続済みなら悪くない
    } else if (b.get(corner) == NONE) {
      v = -5;      // 空角の隣は危険
    } else {
      v = -1;      // 相手角なら大きな価値はない
    }

    score += (owner == BLACK) ? v : -v;
  }
  return score;
}
```

これにより、AI が X マスを機械的に避けすぎたり、逆に安全化した X/C を過小評価したりする問題を減らせる。

### F. WLD タイブレーク `wldRootWithTieBreak`

現行の `wldRoot()` は勝ちを見つけると `alpha >= beta` で打ち切る。修正後は、時間が許す範囲で全候補を分類し、同じ WLD 結果の中で評価する。

```java
int wldRootWithTieBreak(OurBoard root) {
  int n = root.genLegal(BLACK, rootBuf);
  orderStatic(rootBuf, n);

  int bestMove = rootBuf[0];
  int bestClass = -2;      // +1 win, 0 draw, -1 loss
  int bestTie = -INF;

  for (int i = 0; i < n; i++) {
    int m = rootBuf[i];
    OurBoard child = root.placedIndex(m, BLACK);
    int cls = Integer.signum(solveMin(child, -1, 1, 1));
    if (timeUp) return -1;

    int tie = 0;
    if (cls > 0) {
      tie = shallowExactOrEval(child);
    } else if (cls == 0) {
      tie = eval.value(child) + mistakePotential(child);
    } else {
      tie = eval.value(child);
    }

    if (cls > bestClass || (cls == bestClass && tie > bestTie)) {
      bestClass = cls;
      bestTie = tie;
      bestMove = m;
    }
  }

  wldRootValue = bestClass;
  return bestMove;
}
```

`shallowExactOrEval` は、残り時間に応じて次の順に行う。

1. 空き 21-22 なら exact 石差まで読む。
2. 空き 23-24 なら 2-4 ply の探索値を使う。
3. 時間が足りなければ `eval.value(child)` を使う。

WLD の目的は勝敗を落とさないことなので、タイブレークで時間を使いすぎない。残り予算の 20% 程度を上限にする。

### G. 空き 25 の事前 WLD プローブ

相手が狙う勝負所は空き 25 付近である。現在は 25 では WLD に入らないため、全探索ではなく候補手だけを WLD 風に調べる。

```java
int rootSearchAt25(OurBoard root, int depth, int pv) {
  int n = root.genLegal(BLACK, rootBuf);
  orderStatic(rootBuf, n);

  int[] candidates = topKByEval(root, rootBuf, n, 4);
  int best = pv;
  int bestScore = -INF;

  for (int m : candidates) {
    OurBoard child = root.placedIndex(m, BLACK);
    int probe = boundedWldProbe(child, 24, remainingProbeBudget());
    int evalScore = searchValue(child, depth - 1, true);
    int total = evalScore + 2000 * probe;

    if (total > bestScore) {
      bestScore = total;
      best = m;
    }
  }
  return best;
}
```

`boundedWldProbe` は時間切れなら 0 を返す。勝ちを示す候補に大きなボーナス、負けを示す候補に大きなペナルティを与え、空き 25 で悪い WLD 入口を避ける。

## 実装順序

1. `MyEval.value()` に空き数を渡せるようにし、段階別重みへ変更する。
2. `qualityMobility()` と `dangerSquareScore()` を追加する。
3. `safeFrontier()` を追加し、従来の frontier 生カウントを置き換える。
4. 空き 25-28 で `parityScore()` を有効化する。
5. `wldRoot()` を `wldRootWithTieBreak()` に置き換える。
6. 空き 25 のときだけ、上位候補に `boundedWldProbe()` をかける。
7. A/B 対戦で係数を調整する。

## 検証方法

### 1. 評価関数の単体比較

同一局面に対して、旧評価と新評価を出力し、差分を確認する。

- 相手の合法手が多いが危険手ばかりの局面で、新評価が過剰に恐れないこと。
- 自分の frontier が増えるが安全な辺を作る局面で、新評価が過小評価しないこと。
- 空き 25 付近で parity が悪い手を選びにくくなること。

### 2. WLD 領域の比較

空き 21-24 の局面を複数用意し、旧 `wldRoot()` と新 `wldRootWithTieBreak()` の選択手を比較する。

- 勝ち手が 1 つだけなら同じ手を選ぶこと。
- 勝ち手が複数ある場合、新実装がより石差または安全度の高い手を選ぶこと。
- 全手負けの場合、従来通りヒューリスティック探索で粘ること。

### 3. 対戦評価

最低限、次の組み合わせで 100 局以上ずつ対戦する。

- 旧 `p26x29` vs 新 `p26x29`
- 新 `p26x29` vs `p26x42`
- 標準盤、BLOCK 1 個、BLOCK 2 個、BLOCK 3 個の各盤面

見る指標は勝率だけではなく、次も記録する。

- 空き 25 で選んだ手。
- WLD に入った時点の勝敗分類。
- WLD fallback 回数。
- 完全読み fallback 回数。
- 1 局あたりの累積思考時間。

## 期待される効果

この修正の主眼は、終盤完全読みをさらに強くすることではなく、完全読みへ入る前の形を悪くしないことである。

特に、空き 25 以上で相手が次のような罠を作った場合に耐性が上がる。

- 一時的に相手 mobility が増えるが、実際には危険手が多い局面。
- frontier を増やす代わりに辺・角・parity で得をする局面。
- X/C マスを囮にして、次の角または安定辺を狙う局面。
- WLD に入った時点では勝ちだが、石差や安全度の低い勝ち手を選ばされる局面。

最終的な目標は、空き 25 付近で評価関数の穴を突かれて理論負けの WLD 領域へ入る回数を減らすことである。
