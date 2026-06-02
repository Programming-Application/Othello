package p26x42tune;

import ap26.*;
import static ap26.Color.*;
import static ap26.Board.*;

/**
 * 盤面トポロジ分類器 (Phase F, 提出物に含めない実験用)。
 *
 * 壁 = 盤の外周 ∪ BLOCK と定義し、各マスを種類に分類する。これにより BLOCK が作る
 * <b>擬似的な角・辺・X/C</b> を物理的な角と同じ枠組みで扱える。BLOCK は位置も数も
 * ランダムだが、盤面は setBoard 時点で確定するので「その場で壁を見て分類」すればよい。
 *
 * <p>種類コード: 0=CORNER, 1=C, 2=EDGE, 3=X, 4=INTERIOR, -1=BLOCK(評価対象外)。
 */
public final class Topo {
  public static final int CORNER = 0, C = 1, EDGE = 2, X = 3, INTERIOR = 4, BLOCKED = -1;
  public static final int NTYPES = 5;

  private Topo() {}

  /** マス k が壁か (盤外 or BLOCK)。*/
  static boolean isWall(Board b, int r, int c) {
    if (r < 0 || r >= SIZE || c < 0 || c >= SIZE)
      return true;
    return b.get(r * SIZE + c) == BLOCK;
  }

  /**
   * 盤面の各マスを種類に分類して返す (長さ 36)。BLOCK マスは {@link #BLOCKED}。
   */
  public static int[] classify(Board b) {
    int[] t = new int[LENGTH];
    java.util.Arrays.fill(t, -2); // 未分類
    for (int k = 0; k < LENGTH; k++)
      if (b.get(k) == BLOCK)
        t[k] = BLOCKED;

    // 1) 角: 直交2方向(縦の壁 と 横の壁)が隣接
    for (int k = 0; k < LENGTH; k++) {
      if (t[k] == BLOCKED) continue;
      int r = k / SIZE, c = k % SIZE;
      boolean wN = isWall(b, r - 1, c), wS = isWall(b, r + 1, c);
      boolean wE = isWall(b, r, c + 1), wW = isWall(b, r, c - 1);
      boolean vert = wN || wS, horiz = wE || wW;
      if (vert && horiz)
        t[k] = CORNER;
    }

    // 2) 角から X(盤内側ナナメ隣) と C(盤内側の直交隣) を決める
    for (int k = 0; k < LENGTH; k++) {
      if (t[k] != CORNER) continue;
      int r = k / SIZE, c = k % SIZE;
      boolean wN = isWall(b, r - 1, c), wS = isWall(b, r + 1, c);
      boolean wW = isWall(b, r, c - 1), wE = isWall(b, r, c + 1);
      int dv = wN ? 1 : (wS ? -1 : 0);  // 盤内側への縦方向
      int dh = wW ? 1 : (wE ? -1 : 0);  // 盤内側への横方向
      mark(t, r + dv, c + dh, X);        // ナナメ内側 = X
      mark(t, r + dv, c, C);             // 縦内側 = C
      mark(t, r, c + dh, C);             // 横内側 = C
    }

    // 3) 残り: 壁に接していれば EDGE、なければ INTERIOR
    for (int k = 0; k < LENGTH; k++) {
      if (t[k] != -2) continue;
      int r = k / SIZE, c = k % SIZE;
      boolean adj = isWall(b, r - 1, c) || isWall(b, r + 1, c)
                 || isWall(b, r, c + 1) || isWall(b, r, c - 1);
      t[k] = adj ? EDGE : INTERIOR;
    }
    return t;
  }

  /** (r,c) が盤内かつ未分類(-2)なら type を設定 (角・既設の上書きはしない)。*/
  private static void mark(int[] t, int r, int c, int type) {
    if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) return;
    int k = r * SIZE + c;
    if (t[k] == -2) t[k] = type;
  }

  /** 種類重み (長さ5) から 36 マスの重み配列を生成。BLOCK マスは 0。*/
  public static int[] weightsFromTypes(int[] types, int[] typeW) {
    int[] w = new int[LENGTH];
    for (int k = 0; k < LENGTH; k++)
      w[k] = (types[k] >= 0) ? typeW[types[k]] : 0;
    return w;
  }
}
