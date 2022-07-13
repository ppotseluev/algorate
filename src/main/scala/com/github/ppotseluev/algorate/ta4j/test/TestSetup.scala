package com.github.ppotseluev.algorate.ta4j.test

import com.github.ppotseluev.algorate.core.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.core.Broker.CandlesInterval
import com.github.ppotseluev.algorate.core.Broker.DaysInterval
import com.github.ppotseluev.algorate.model.Tags
import com.github.ppotseluev.algorate.ta4j.strategy.Strategies
import com.softwaremill.tagging.Tagger
import java.time.LocalDate

object TestSetup {

  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2019, 1, 1),
      LocalDate.of(2022, 7, 10)
    ),
    resolution = OneMinute
  )

  val strategy = Strategies.test

  val tickers = List(
//    "YNDX",
//    "GAZP",
//    "LKOH",
//    "GMKN",
//    "ROSN",
//    "CHMF",
//    "TATNP",
//    "NVTK",
//    "POLY"
    "MDMG",
    "FIXP",
    "YNDX",
    "ENPG",
    "CHMK",
    "MGNT",
    "SMLT",
    "LNZL",
    "LKOH",
    "PLZL",
    "UNKL",
    "TCSG",
    "MGTSP",
    "PHOR",
    "UWGN",
    "GEMC",
    "BELU",
    "GLTR",
    "FIVE",
    "BANE",
    "POSI",
    "BANEP",
    "GMKN",
    "QIWI",
    "OZON",
    "ROSN",
    "KRKNP",
    "CHMF",
    "ETLN",
    "SFTL",
    "LENT",
    "GCHE",
    "AQUA",
    "TATNP",
    "VKCO",
    "RNFT",
    "AKRN",
    "AGRO",
    "CIAN",
    "TATN",
    "VSMO",
    "MVID",
    "PIKK",
    "TRNFP",
    "LSRG",
    "LNZLP",
    "SIBN",
    "NVTK",
    "MTLR",
    "RKKE",
    "POLY"
  ).map(_.taggedWith[Tags.Ticker])

}
