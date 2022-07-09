package com.github.ppotseluev.algorate.ta4j.test

import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.github.ppotseluev.algorate.util.Interval
import com.softwaremill.tagging.Tagger
import java.time.OffsetDateTime

object TestSetup {
//  val interval = Interval.minutes(
//    OffsetDateTime.parse("2021-02-09T10:30+03:00"),
//    OffsetDateTime.parse("2021-10-22T23:30+03:00")
//  )

  val interval = Interval.minutes(
    OffsetDateTime.parse("2021-04-03T10:30+03:00"),
    OffsetDateTime.parse("2021-07-03T23:30+03:00")
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
