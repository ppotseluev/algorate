package com.github.ppotseluev.algorate.tools.strategy

import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.strategy.Strategies
import java.time.LocalDate
//import scala.util.Random

private[strategy] object TestSetup {

  val interval = CandlesInterval(
    interval = DaysInterval(
//      LocalDate.of(2020, 1, 1),
//      LocalDate.of(2022, 7, 20)

//      LocalDate.of(2020, 1, 1),
//      LocalDate.of(2021, 1, 1)
      LocalDate.of(2022, 1, 2),
      LocalDate.of(2022, 8, 20)
    ),
    resolution = OneMinute
  )

  val strategy = Strategies.intraChannel

  private val sampleSize = 30

  private val allTickers = List( //actually not all...
    "OKE",
    "MCD",
    "MET",
    "STX",
    "DFS",
    "TTD",
    "CME",
    "IRBT",
    "ED",
    "MAS",
    "COIN",
    "BRK.B",
    "VZ",
    "TTWO",
    "CRTX",
    "WMT",
    "ASTR",
    "LOW",
    "TSLA",
    "MSTR",
    "IVZ",
    "YNDX",
    "APA",
    "ZM",
    "RCL",
    "BAC",
    "OVV",
    "AMAT",
    "EHTH",
    "DIS",
    "ANAB",
    "AAPL",
    "ROST",
    "CFG",
    "BKNG",
    "EXPE",
    "NEE",
    "MAR",
    "OXY",
    "SCHW",
    "MDT",
    "PFGC",
    "NFLX",
    "EVH",
    "INTC",
    "MOS",
    "INCY",
    "SRPT",
    "KHC",
    "CLOV",
    "TRIP",
    "GD",
    "DVN",
    "MTCH",
    "ETRN",
    "PG",
    "SMAR",
    "META",
    "WDC",
    "CHX",
    "PAGS",
    "HLT",
    "GPS",
    "RACE",
    "GILD",
    "CLF",
    "PINS",
    "ARWR",
    "MGNT",
    "ACAD",
    "M",
    "CNC",
    "GTHX",
    "IDXX",
    "CPRI",
    "ROKU",
    "ORCL",
    "SPR",
    "CHEF",
    "PPC",
    "F",
    "LKOH",
    "GLW",
    "PLZL",
    "MDLZ",
    "FSLY",
    "LHX",
    "CTXS",
    "ABBV",
    "SQ",
    "AMGN",
    "WYNN",
    "EA",
    "UNH",
    "PPL",
    "LVS",
    "EBAY",
    "RL",
    "NVAX",
    "H",
    "PHOR",
    "HA",
    "ETSY",
    "DXC",
    "HEAR",
    "JPM",
    "PBI",
    "HD",
    "MED",
    "NET",
    "SWN",
    "CNK",
    "MO",
    "W",
    "SNAP",
    "PBF",
    "RRC",
    "TSN",
    "BKR",
    "AA",
    "KMI",
    "COTY",
    "BALL",
    "PSX",
    "REGI",
    "HPQ",
    "KO",
    "PM",
    "RRGB",
    "EQT",
    "UBER",
    "COP",
    "GRMN",
    "RKLB",
    "WM",
    "QCOM",
    "ILMN",
    "MDB",
    "WRK",
    "BJRI",
    "TWLO",
    "TXN",
    "UAL",
    "MPC",
    "SPGI",
    "PLTR",
    "HOG",
    "AXP",
    "PRU",
    "BEN",
    "TDOC",
    "TMUS",
    "GS",
    "MA",
    "AIG",
    "OMC",
    "TER",
    "SBUX",
    "BANE",
    "CAT",
    "PYPL",
    "CVS",
    "HCA",
    "UPS",
    "WFC",
    "FDX",
    "SIG",
    "HIG",
    "RIOT",
    "CHGG",
    "YUM",
    "MS",
    "NDAQ",
    "SEDG",
    "GMKN",
    "HPE",
    "Z",
    "SPCE",
    "SPOT",
    "XOM",
    "IOVA",
    "PVH",
    "ALK",
    "SLB",
    "TROW",
    "PARA",
    "WKHS",
    "DAL",
    "MSFT",
    "DHI",
    "ROSN",
    "VRTX",
    "HAS",
    "EIX",
    "SLDB",
    "ALB",
    "KEY",
    "BFH",
    "LYB",
    "DRI",
    "BSX",
    "WMB",
    "PLUG",
    "CHMF",
    "QRVO",
    "HON",
    "LMT",
    "BYND",
    "DOCU",
    "LUV",
    "LEVI",
    "LYFT",
    "CAH",
    "AMD",
    "PUMP",
    "RF",
    "USB",
    "JNJ",
    "KR",
    "TJX",
    "DOW",
    "MNST",
    "NXPI",
    "CMCSA",
    "CCL",
    "UPWK",
    "ADBE",
    "KMB",
    "USFD",
    "ATRA",
    "ZYXI",
    "MU",
    "ATVI",
    "HAL",
    "LRCX",
    "BA",
    "T",
    "REGN",
    "RTX",
    "CVX",
    "AERI",
    "PCG",
    "AYX",
    "GPN",
    "HBAN",
    "V",
    "NVDA",
    "TATN",
    "CLR",
    "WU",
    "ZYNE",
    "MARA",
    "EOG",
    "HRTX",
    "SNPS",
    "NKE",
    "PLAY",
    "MVID",
    "EPAM",
    "ADM",
    "JWN",
    "CRM",
    "TGT",
    "VLO",
    "BMY",
    "PIKK",
    "CNP",
    "OII",
    "TWTR",
    "ABMD",
    "BK",
    "GT",
    "ZS",
    "BIIB",
    "DHR",
    "ADSK",
    "SO",
    "D",
    "XRX",
    "LSRG",
    "SAGE",
    "SYY",
    "UAA",
    "CINF",
    "VREX",
    "HII",
    "NTNX",
    "MRNA",
    "BYSI",
    "CLSK",
    "SIBN",
    "AVGO",
    "CRWD",
    "NEM",
    "EMR",
    "GM",
    "NVTK",
    "MMM",
    "EAR",
    "MTLR",
    "NRG",
    "DDOG",
    "TWOU",
    "FSLR",
    "C",
    "BBY",
    "SAVE",
    "ABT",
    "DXCM",
    "WBA",
    "ACN",
    "PEP",
    "POLY",
    "LLY",
    "ENPH",
    "NTAP",
    "PTON",
    "GOSS",
    "LTHM",
    "CSCO",
    "FCX"
  )

  val tickers = List(
    "LUV",
    "FDX",
    "DAL",
    "LYB",
    "CHMF",
    "PHOR",
    "POLY",
    "QRVO",
    "MU",
    "LRCX",
    "NVDA",
    "XRX",
    "CSCO",
    "PINS",
    "WDC",
    "QCOM",
    "GLW",
    "LKOH",
    "XOM",
    "ROSN",
    "CVX",
    "OXY",
    "PPL",
    "CNP",
    "NKE",
    "TGT",
    "MDLZ",
    "MCD",
    "RL",
    "NFLX",
    "YNDX",
    "TRIP",
    "MET",
    "KEY",
    "V",
    "CFG",
    "BAC",
    "HIG",
    "RF",
    "BIIB",
    "AMGN",
    "CVS",
    "ABBV"
  )
  //Random.shuffle(allTickers).take(sampleSize)
//    List(
//      "ASTR",
//      "GD",
//      "LHX",
//      "BALL",
//      "WRK",
//      "GPN",
//      "LRCX",
//      "AVGO",
//      "CTXS",
//      "WMB",
//      "OKE",
//      "PPL",
//      "GM",
//      "KR",
//      "HEAR",
//      "WMT",
//      "USFD",
//      "BKNG",
//      "RL",
//      "TTWO",
//      "DIS",
//      "MTCH",
//      "Z",
//      "AIG",
//      "JPM",
//      "MET",
//      "RF",
//      "VRTX",
//      "CVS",
//      "REGN"
//    ).map(_.taggedWith[Tags.Ticker])
}