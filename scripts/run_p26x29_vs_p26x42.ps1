param(
  [int]$Games = 5,
  [string]$LogPath = "logs/p26x29_vs_p26x42_5games.log",
  [long]$TimeLimitSeconds = 60
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$work = Join-Path $root ".tmp/p26x29_vs_p26x42"
$srcDir = Join-Path $work "src/ap26/league"
$classes = Join-Path $work "classes"
$runner = Join-Path $srcDir "P26x29VsP26x42Runner.java"
$logFullPath = Join-Path $root $LogPath

New-Item -ItemType Directory -Force -Path $srcDir, $classes, (Split-Path -Parent $logFullPath) | Out-Null

$runnerSource = @"
package ap26.league;

import ap26.*;
import static ap26.Color.*;
import java.util.*;

public class P26x29VsP26x42Runner {
  static class TraceGame extends Game {
    final int round;
    final String blackName;
    final String whiteName;

    TraceGame(int round, Board board, Player black, Player white, long timeLimit, String blackName, String whiteName) {
      super(board, black, white, timeLimit);
      this.round = round;
      this.blackName = blackName;
      this.whiteName = whiteName;
    }

    void playWithBoards() {
      this.players.entrySet().forEach(i -> setBoard(i, this.board.clone()));

      System.out.printf("=== Round %d: %s(BLACK) vs %s(WHITE) ===%n", round, blackName, whiteName);
      System.out.println("Initial board:");
      System.out.println(this.board);

      int moveNo = 1;
      while (this.board.isEnd() == false) {
        var turn = this.board.getTurn();
        var player = this.players.get(turn);
        var playerName = turn == BLACK ? blackName : whiteName;

        Throwable error = null;
        long tm = System.currentTimeMillis();
        Move move;

        try {
          move = player.think(this.board.clone()).colored(turn);
        } catch (Throwable e) {
          error = e;
          move = Move.ofError(turn);
        }

        tm = System.currentTimeMillis() - tm;
        final var t = (float) Math.max(tm, 1) / 1000.f;
        this.times.compute(turn, (k, v) -> v + t);

        System.out.printf("-- Move %d --%n", moveNo);
        System.out.printf("Turn: %s (%s)%n", turn, playerName);
        System.out.printf("Selected: %s in %.3f ms%n", move, (double) tm);

        move = check(turn, move, error);
        this.moves.add(move);
        System.out.printf("Legal: %s%n", move.isLegal());

        if (move.isLegal()) {
          this.board = this.board.placed(move);
        } else {
          this.board.foul(turn);
          System.out.printf("Foul: %s%n", turn);
          break;
        }

        System.out.printf("After move %d:%n", moveNo);
        System.out.println(this.board);
        moveNo++;
      }

      int score = this.board.score();
      String winner = score > 0 ? blackName : score < 0 ? whiteName : "draw";
      System.out.printf("Result: Round %d, Winner=%s, StoneDiff=%d%n%n", round, winner, Math.abs(score));
    }
  }

  public static void main(String[] args) {
    int games = Integer.parseInt(args[0]);
    long timeLimit = Long.parseLong(args[1]);
    String[] summary = new String[games + 1];
    summary[0] = "Round,Black,White,Winner,StoneDiff";

    for (int round = 1; round <= games; round++) {
      boolean p29Black = (round % 2) == 1;
      Player black = p29Black ? new p26x29.OurPlayer(BLACK) : new p26x42.OurPlayer(BLACK);
      Player white = p29Black ? new p26x42.OurPlayer(WHITE) : new p26x29.OurPlayer(WHITE);
      String blackName = p29Black ? "p26x29" : "p26x42";
      String whiteName = p29Black ? "p26x42" : "p26x29";

      TraceGame game = new TraceGame(round, new OfficialBoard(), black, white, timeLimit, blackName, whiteName);
      game.playWithBoards();

      int score = game.board.score();
      String winner = score > 0 ? blackName : score < 0 ? whiteName : "draw";
      summary[round] = String.format("%d,%s,%s,%s,%d", round, blackName, whiteName, winner, Math.abs(score));
    }

    System.out.println("=== Summary ===");
    for (String line : summary) {
      System.out.println(line);
    }
  }
}
"@

[System.IO.File]::WriteAllText($runner, $runnerSource, [System.Text.UTF8Encoding]::new($false))

$sources = @(
  Get-ChildItem -Path (Join-Path $root "ap26"), (Join-Path $root "p26x29"), (Join-Path $root "p26x42") -Recurse -Filter "*.java"
  Get-Item $runner
)

javac -encoding UTF-8 -d $classes @($sources.FullName)
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

java -cp $classes ap26.league.P26x29VsP26x42Runner $Games $TimeLimitSeconds | Tee-Object -FilePath $logFullPath
if ($LASTEXITCODE -ne 0) { throw "java failed with exit code $LASTEXITCODE" }

Write-Host "Log written to $logFullPath"
