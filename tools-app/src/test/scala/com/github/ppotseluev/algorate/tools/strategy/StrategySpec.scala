package com.github.ppotseluev.algorate.tools.strategy

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.broker.Archive
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.trader.policy.Policy.Decision
import java.io.File
import java.time.LocalDate
import munit.FunSuite

class StrategySpec extends FunSuite {
  val strategy = Strategies.default
  val asset = TradingAsset("BBG000BBS2Y0", "AMGN", "usd", "")
  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2022, 1, 1),
      LocalDate.of(2022, 12, 31)
    ),
    resolution = CandleResolution.OneMinute
  )

  type F[T] = IO[T]

  val path = new File("tools-app/data/archive").toPath
  val archive = new Archive[F]("", path)

  test("strategy works as expected") {
    val seriesProvider = new BarSeriesProvider[F](archive)
    val series = seriesProvider.getBarSeries(asset, interval).unsafeRunSync()
    val stats = StrategyTester[F](
      strategy,
      StrategyTester
        .fixedTradeCostPolicy(allowFractionalLots = false)
        .andThen(_.allowedOrElse(Decision.Allowed(1)))
    ).test(series, asset).unsafeRunSync()
    assertEquals(stats.long.totalClosedPositions, 9)
    assertEquals(stats.short.totalClosedPositions, 20)
    assertEquals(stats.long.winRatio(fee = false), 0.4444444444444444)
    assertEquals(stats.short.winRatio(fee = false), 0.65)
  }
}
