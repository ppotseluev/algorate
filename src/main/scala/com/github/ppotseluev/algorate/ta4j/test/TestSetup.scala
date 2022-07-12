package com.github.ppotseluev.algorate.ta4j.test

import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.{CandlesInterval, DaysInterval}
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.softwaremill.tagging.Tagger

import java.time.LocalDate

object TestSetup {
//  val interval = Interval.minutes(
//    OffsetDateTime.parse("2021-02-09T10:30+03:00"),
//    OffsetDateTime.parse("2021-10-22T23:30+03:00")
//  )

  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2021, 1, 1),
      LocalDate.of(2021, 12, 31)
    ),
    resolution = OneMinute
  )

  val strategy = Strategies.test

  val tickers = List(
    "YNDX",
    "GAZP",
    "LKOH",
    "GMKN",
    "ROSN",
    "CHMF",
    "TATNP",
    "NVTK",
    "POLY"
  ).map(_.taggedWith[Tags.Ticker])

}
