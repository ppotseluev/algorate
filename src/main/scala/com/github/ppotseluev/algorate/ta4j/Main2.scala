package com.github.ppotseluev.algorate.ta4j

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.util.Interval
import com.softwaremill.tagging.Tagger

import java.time.OffsetDateTime

object Main2 extends App {
  val seriesProvider = new BarSeriesProvider[IO](args.head)
  val ticker = "YNDX".taggedWith[Tags.Ticker]
  val interval = Interval.minutes(
    OffsetDateTime.parse("2021-02-09T10:30+03:00"),
    OffsetDateTime.parse("2021-02-19T23:30+03:00")
  )
  val series = seriesProvider.getBarSeries(ticker, interval)
  val strategy = Strategies.emaCrossAndRsiStrategy.andThen(
    _.copy(
      shortStrategy = Strategies.doNothing
    )
  )
  val tester = new StrategyTester(strategy)
  val result = tester.test(series)
  println(result)
  Charts.display(
    strategy,
    series,
    result,
    "results"
  )
}
