package com.github.ppotseluev.algorate.ta4j.test

import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.{CandlesInterval, Interval}
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.softwaremill.tagging.Tagger
import org.jfree.data.time.Day

import java.time.ZoneId

object TestSetup {
//  val interval = Interval.minutes(
//    OffsetDateTime.parse("2021-02-09T10:30+03:00"),
//    OffsetDateTime.parse("2021-10-22T23:30+03:00")
//  )

  val interval = CandlesInterval(
    interval = Interval(
      new Day(3, 4, 2021),
      new Day(3, 7, 2021)
    ),
    resolution = OneMinute,
    zoneId = ZoneId.of("+03:00")
  )

  val strategy = Strategies.test

  val tickers = List(
    "YNDX",
    "GAZP"
//    "LKOH",
//    "GMKN",
//    "ROSN",
//    "CHMF",
//    "TATNP",
//    "NVTK",
//    "POLY"
  ).map(_.taggedWith[Tags.Ticker])

}
