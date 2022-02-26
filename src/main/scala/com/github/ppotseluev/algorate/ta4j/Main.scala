package com.github.ppotseluev.algorate.ta4j

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.github.ppotseluev.algorate.util.Interval
import com.softwaremill.tagging.Tagger

import java.time.OffsetDateTime

object Main extends App {
  val seriesProvider = new BarSeriesProvider[IO](args.head)
//  val ticker = "YNDX".taggedWith[Tags.Ticker]
  val ticker = "ROSN".taggedWith[Tags.Ticker]
  val interval = Interval.minutes(
    OffsetDateTime.parse("2021-02-09T10:30+03:00"),
    OffsetDateTime.parse("2021-10-22T23:30+03:00")
  )
    //.map(_.plusDays(8))
  val series = seriesProvider.getBarSeries(ticker, interval)
  val strategy = Strategies.test
  val tester = new StrategyTester(strategy)
  val result = tester.test(series)
  println(result)
  Charts.display(
    strategy,
    series,
    Some(result),
    "results"
  )
}
