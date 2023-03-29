package com.github.ppotseluev.algorate.tools.strategy

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Archive
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.strategy.Strategies
import java.time.LocalDate
import munit.FunSuite
import org.graalvm.polyglot.io.FileSystem

class StrategySpec extends FunSuite {
  val strategy = Strategies.intraChannel
  val asset = TradingAsset("BBG000BBS2Y0", "AMGN", "usd")
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2022, 1, 1),
      LocalDate.of(2022, 12, 31)
    ),
    resolution = CandleResolution.OneMinute
  )

  type F[T] = IO[T]

  val path = "tools-app/data/archive"
  val archive = new Archive[F](FileSystem.newDefaultFileSystem().parsePath(path))

  test("strategy works as expected") {
    val seriesProvider = new BarSeriesProvider[F](archive)
    val series = seriesProvider.getBarSeries(asset, interval).unsafeRunSync()
    val stats = StrategyTester(strategy).test(series, asset)
    assertEquals(stats.long.totalClosedPositions, 17)
    assertEquals(stats.short.totalClosedPositions, 25)
    assertEquals(stats.long.winningPositions(fee = false), 8)
    assertEquals(stats.short.winningPositions(fee = false), 12)
  }
}
