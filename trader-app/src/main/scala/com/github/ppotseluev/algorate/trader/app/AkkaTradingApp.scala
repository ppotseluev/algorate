package com.github.ppotseluev.algorate.trader.app

import akka.actor.typed.ActorSystem
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.unsafe.implicits.global
import cats.implicits._
import cats.~>
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.broker.Broker.OrderPlacementInfo
import com.github.ppotseluev.algorate.broker.tinkoff.TinkoffBroker
import com.github.ppotseluev.algorate.server.Factory
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.trader.akkabot.Event
import com.github.ppotseluev.algorate.trader.akkabot.EventsSink
import com.github.ppotseluev.algorate.trader.akkabot.TradingManager
import com.github.ppotseluev.algorate.trader.policy.MoneyManagementPolicy
import com.typesafe.scalalogging.LazyLogging

import java.time.LocalDate
import scala.concurrent.Future
import scala.concurrent.duration._

object AkkaTradingApp extends IOApp with LazyLogging {

  case class StubSettings(
      tickersMap: Map[Ticker, InstrumentId],
      streamFrom: LocalDate = LocalDate.now.minusDays(10),
      streamTo: LocalDate = LocalDate.now.minusDays(2),
      rate: FiniteDuration = 1.second
  )

  val assets: Map[InstrumentId, TradingAsset] =
    Map(
      "BBG000FVXD63" -> TradingAsset("RRC", "usd"),
      "BBG000N7MXL8" -> TradingAsset("ETSY", "usd"),
      "BBG000BKNX95" -> TradingAsset("GT", "usd"),
      "BBG00CZNLR47" -> TradingAsset("VREX", "usd"),
      "BBG00SHY90J5" -> TradingAsset("CLOV", "usd"),
      "BBG000G2GJT7" -> TradingAsset("HRTX", "usd"),
      "BBG000BFL116" -> TradingAsset("FSLR", "usd"),
      "BBG004S68C39" -> TradingAsset("LSRG", "rub"),
      "BBG003338H34" -> TradingAsset("ZS", "usd"),
      "BBG0014XR0N5" -> TradingAsset("CHGG", "usd"),
      "BBG000BTVJ25" -> TradingAsset("SYY", "usd"),
      "BBG000PRJ2Z9" -> TradingAsset("SPR", "usd"),
      "BBG00FYCQ352" -> TradingAsset("PUMP", "usd"),
      "BBG000M65M61" -> TradingAsset("UAL", "usd"),
      "BBG000BNJHS8" -> TradingAsset("LUV", "usd"),
      "BBG000BLBVN4" -> TradingAsset("BKNG", "usd"),
      "BBG000C43RR5" -> TradingAsset("EBAY", "usd"),
      "BBG000BFT2L4" -> TradingAsset("CMCSA", "usd"),
      "BBG000BBNYF6" -> TradingAsset("DRI", "usd"),
      "BBG004RVFFC0" -> TradingAsset("TATN", "rub"),
      "BBG00LV3NRG0" -> TradingAsset("LTHM", "usd"),
      "BBG000BB5792" -> TradingAsset("RCL", "usd"),
      "BBG000C734W3" -> TradingAsset("REGN", "usd"),
      "BBG004RVFCY3" -> TradingAsset("MGNT", "rub"),
      "BBG005Q3MQY4" -> TradingAsset("ATRA", "usd"),
      "BBG000BNDN65" -> TradingAsset("LOW", "usd"),
      "BBG000BLGFJ9" -> TradingAsset("LHX", "usd"),
      "BBG000BF0K17" -> TradingAsset("CAT", "usd"),
      "BBG00441QMJ7" -> TradingAsset("SNAP", "usd"),
      "BBG005CPNTQ2" -> TradingAsset("KHC", "usd"),
      "BBG000QDVR53" -> TradingAsset("CNK", "usd"),
      "BBG000BFRF55" -> TradingAsset("CLF", "usd"),
      "BBG000H556T9" -> TradingAsset("HON", "usd"),
      "BBG0022FDRY8" -> TradingAsset("MDB", "usd"),
      "BBG000C1BW00" -> TradingAsset("LMT", "usd"),
      "BBG007TJF1N7" -> TradingAsset("QRVO", "usd"),
      "BBG000CVRFS4" -> TradingAsset("H", "usd"),
      "BBG000CH5208" -> TradingAsset("UNH", "usd"),
      "BBG000BRVKH0" -> TradingAsset("ARWR", "usd"),
      "BBG000BV4DR6" -> TradingAsset("TER", "usd"),
      "BBG000C1S2X2" -> TradingAsset("VRTX", "usd"),
      "BBG001K7WBT8" -> TradingAsset("MARA", "usd"),
      "BBG004PYF2N3" -> TradingAsset("POLY", "rub"),
      "BBG000BP6LJ8" -> TradingAsset("MO", "usd"),
      "BBG000C17X76" -> TradingAsset("BIIB", "usd"),
      "BBG005CHLM96" -> TradingAsset("EVH", "usd"),
      "BBG000BP0KQ8" -> TradingAsset("EA", "usd"),
      "BBG000BNNKG9" -> TradingAsset("MAS", "usd"),
      "BBG000CL9VN6" -> TradingAsset("NFLX", "usd"),
      "BBG000BK67C7" -> TradingAsset("GD", "usd"),
      "BBG0025Y4RY4" -> TradingAsset("ABBV", "usd"),
      "BBG000BKVJK4" -> TradingAsset("HAS", "usd"),
      "BBG00Y61SZL5" -> TradingAsset("RKLB", "usd"),
      "BBG000BXM6V2" -> TradingAsset("UAA", "usd"),
      "BBG00JM7QBR6" -> TradingAsset("PAGS", "usd"),
      "BBG000BMHYD1" -> TradingAsset("JNJ", "usd"),
      "BBG001M8HHB7" -> TradingAsset("TRIP", "usd"),
      "BBG001R3MNY9" -> TradingAsset("ENPH", "usd"),
      "BBG009XW2WB8" -> TradingAsset("PFGC", "usd"),
      "BBG0019JZ882" -> TradingAsset("KMI", "usd"),
      "BBG0018SLC07" -> TradingAsset("SQ", "usd"),
      "BBG000BW8S60" -> TradingAsset("RTX", "usd"),
      "BBG000H8TVT2" -> TradingAsset("TGT", "usd"),
      "BBG000BCQZS4" -> TradingAsset("AXP", "usd"),
      "BBG000BVV7G1" -> TradingAsset("TXN", "usd"),
      "BBG000G8N9C6" -> TradingAsset("JWN", "usd"),
      "BBG000BF6LY3" -> TradingAsset("CCL", "usd"),
      "BBG001KJ2HM9" -> TradingAsset("HII", "usd"),
      "BBG000C0G1D1" -> TradingAsset("INTC", "usd"),
      "BBG000BJBXZ2" -> TradingAsset("ZYXI", "usd"),
      "BBG000FFDM15" -> TradingAsset("USB", "usd"),
      "BBG000BSJK37" -> TradingAsset("T", "usd"),
      "BBG000BBPFB9" -> TradingAsset("AMAT", "usd"),
      "BBG000BC4185" -> TradingAsset("HA", "usd"),
      "BBG000BM6N47" -> TradingAsset("HEAR", "usd"),
      "BBG000BJ26K7" -> TradingAsset("ALB", "usd"),
      "BBG000BGZT72" -> TradingAsset("AYX", "usd"),
      "BBG000BDDNH5" -> TradingAsset("BALL", "usd"),
      "BBG000BCJ161" -> TradingAsset("SRPT", "usd"),
      "BBG000D898T9" -> TradingAsset("CAH", "usd"),
      "BBG000BCWCG1" -> TradingAsset("BBY", "usd"),
      "BBG000BD0TF8" -> TradingAsset("BEN", "usd"),
      "BBG000DQTXY6" -> TradingAsset("DHI", "usd"),
      "BBG000N7QR55" -> TradingAsset("PLTR", "usd"),
      "BBG000BJSBJ0" -> TradingAsset("NEE", "usd"),
      "BBG000BRJL00" -> TradingAsset("PPL", "usd"),
      "BBG0026ZDHR0" -> TradingAsset("ANAB", "usd"),
      "BBG00JH9TZ56" -> TradingAsset("CHX", "usd"),
      "BBG000BQLTW7" -> TradingAsset("ORCL", "usd"),
      "BBG0042V6JM8" -> TradingAsset("ZM", "usd"),
      "BBG000BM7HL0" -> TradingAsset("ADSK", "usd"),
      "BBG000MXH9C1" -> TradingAsset("RRGB", "usd"),
      "BBG000GQJPZ0" -> TradingAsset("MSTR", "usd"),
      "BBG009PH3Q86" -> TradingAsset("RACE", "usd"),
      "BBG000C496P7" -> TradingAsset("PARA", "usd"),
      "BBG000BHZ5J9" -> TradingAsset("EQT", "usd"),
      "BBG000BT41Q8" -> TradingAsset("SLB", "usd"),
      "BBG000BWQFY7" -> TradingAsset("WFC", "usd"),
      "BBG000F395V1" -> TradingAsset("COTY", "usd"),
      "BBG004S68598" -> TradingAsset("MTLR", "rub"),
      "BBG000PSKYX7" -> TradingAsset("V", "usd"),
      "BBG000BGD7W6" -> TradingAsset("MAR", "usd"),
      "BBG000BB6KF5" -> TradingAsset("MET", "usd"),
      "BBG000C3J3C9" -> TradingAsset("CSCO", "usd"),
      "BBG000BSFRF3" -> TradingAsset("SNPS", "usd"),
      "BBG000BBDZG3" -> TradingAsset("AIG", "usd"),
      "BBG000BPH459" -> TradingAsset("MSFT", "usd"),
      "BBG000BMY992" -> TradingAsset("KR", "usd"),
      "BBG000F1ZSQ2" -> TradingAsset("MA", "usd"),
      "BBG000BHLYP4" -> TradingAsset("CME", "usd"),
      "BBG000BKZTP3" -> TradingAsset("HOG", "usd"),
      "BBG000BHG9K0" -> TradingAsset("ACAD", "usd"),
      "BBG000BBJQV0" -> TradingAsset("NVDA", "usd"),
      "BBG00YTS96G2" -> TradingAsset("APA", "usd"),
      "BBG000QW7VC1" -> TradingAsset("HCA", "usd"),
      "BBG000BS1YV5" -> TradingAsset("TTWO", "usd"),
      "BBG000BLZRJ2" -> TradingAsset("MS", "usd"),
      "BBG000D4LWF6" -> TradingAsset("MDLZ", "usd"),
      "BBG000BLRT07" -> TradingAsset("IDXX", "usd"),
      "BBG000BKFZM4" -> TradingAsset("GLW", "usd"),
      "BBG000BNWG87" -> TradingAsset("MDT", "usd"),
      "BBG000BKLH74" -> TradingAsset("GPS", "usd"),
      "BBG000BDPB15" -> TradingAsset("WKHS", "usd"),
      "BBG00GBVBK51" -> TradingAsset("BKR", "usd"),
      "BBG003PHHZT1" -> TradingAsset("MRNA", "usd"),
      "BBG000DWG505" -> TradingAsset("BRK.B", "usd"),
      "BBG000GZQ728" -> TradingAsset("XOM", "usd"),
      "BBG000CGC1X8" -> TradingAsset("QCOM", "usd"),
      "BBG000BS9489" -> TradingAsset("OMC", "usd"),
      "BBG000BNBDC2" -> TradingAsset("LLY", "usd"),
      "BBG000DSMS70" -> TradingAsset("ILMN", "usd"),
      "BBG000C46HM9" -> TradingAsset("M", "usd"),
      "BBG00W7FG4V8" -> TradingAsset("ASTR", "usd"),
      "BBG000R7Z112" -> TradingAsset("DAL", "usd"),
      "BBG001B17MV2" -> TradingAsset("W", "usd"),
      "BBG000BSLZY7" -> TradingAsset("SCHW", "usd"),
      "BBG002B04MT8" -> TradingAsset("UBER", "usd"),
      "BBG000BMX289" -> TradingAsset("KO", "usd"),
      "BBG000BQQ2S6" -> TradingAsset("OXY", "usd"),
      "BBG000C4LN67" -> TradingAsset("GRMN", "usd"),
      "BBG000BH4R78" -> TradingAsset("DIS", "usd"),
      "BBG000BFXHL6" -> TradingAsset("MOS", "usd"),
      "BBG000NDYB67" -> TradingAsset("GM", "usd"),
      "BBG00ZGF7771" -> TradingAsset("COIN", "usd"),
      "BBG000BB5006" -> TradingAsset("ADBE", "usd"),
      "BBG000BBS2Y0" -> TradingAsset("AMGN", "usd"),
      "BBG000BQQH30" -> TradingAsset("COP", "usd"),
      "BBG000LD9JQ8" -> TradingAsset("WYNN", "usd"),
      "BBG0058KMH30" -> TradingAsset("HLT", "usd"),
      "BBG000BCSST7" -> TradingAsset("BA", "usd"),
      "BBG000WCFV84" -> TradingAsset("LYB", "usd"),
      "BBG000C5Z1S3" -> TradingAsset("MU", "usd"),
      "BBG000BCTLF6" -> TradingAsset("BAC", "usd"),
      "BBG000BWLMJ4" -> TradingAsset("WBA", "usd"),
      "BBG000BH3JF8" -> TradingAsset("DHR", "usd"),
      "BBG000CPBCL8" -> TradingAsset("OII", "usd"),
      "BBG005F1DK91" -> TradingAsset("GTHX", "usd"),
      "BBG003CVJP50" -> TradingAsset("BYND", "usd"),
      "BBG000C0LW92" -> TradingAsset("BSX", "usd"),
      "BBG000BPWXK1" -> TradingAsset("NEM", "usd"),
      "BBG003T4VFC2" -> TradingAsset("SPOT", "usd"),
      "BBG000BWVSR1" -> TradingAsset("WM", "usd"),
      "BBG000BQ4512" -> TradingAsset("RIOT", "usd"),
      "BBG00629NGT2" -> TradingAsset("TTD", "usd"),
      "BBG002832GV8" -> TradingAsset("PBF", "usd"),
      "BBG0077VNXV6" -> TradingAsset("PYPL", "usd"),
      "BBG00FN64XT9" -> TradingAsset("DXC", "usd"),
      "BBG001WWJTK5" -> TradingAsset("PLAY", "usd"),
      "BBG000NVSBL7" -> TradingAsset("NVAX", "usd"),
      "BBG0025X16Y5" -> TradingAsset("SAGE", "usd"),
      "BBG000D7RKJ5" -> TradingAsset("EIX", "usd"),
      "BBG000BGVW60" -> TradingAsset("D", "usd"),
      "BBG000DH7JK6" -> TradingAsset("PEP", "usd"),
      "BBG000BJF1Z8" -> TradingAsset("FDX", "usd"),
      "BBG000BQHGR6" -> TradingAsset("OKE", "usd"),
      "BBG000R607Y3" -> TradingAsset("PLZL", "rub"),
      "BBG000BWXBC2" -> TradingAsset("WMT", "usd"),
      "BBG000BKZB36" -> TradingAsset("HD", "usd"),
      "BBG000BD8PN9" -> TradingAsset("BK", "usd"),
      "BBG000HS77T5" -> TradingAsset("VZ", "usd"),
      "BBG000BB5373" -> TradingAsset("WU", "usd"),
      "BBG000Q3JN03" -> TradingAsset("RF", "usd"),
      "BBG007BBS8B7" -> TradingAsset("ZYNE", "usd"),
      "BBG000BNFLM9" -> TradingAsset("LRCX", "usd"),
      "BBG006Q0HY77" -> TradingAsset("CFG", "usd"),
      "BBG000C4ZZ10" -> TradingAsset("SIG", "usd"),
      "BBG000F5VVB6" -> TradingAsset("NDAQ", "usd"),
      "BBG00FBJ6390" -> TradingAsset("UPWK", "usd"),
      "BBG000BND699" -> TradingAsset("NXPI", "usd"),
      "BBG000BWVCP8" -> TradingAsset("WMB", "usd"),
      "BBG000C1XSP8" -> TradingAsset("PLUG", "usd"),
      "BBG000BKWSR6" -> TradingAsset("HBAN", "usd"),
      "BBG000CKGBP2" -> TradingAsset("GILD", "usd"),
      "BBG000QY3XZ2" -> TradingAsset("EXPE", "usd"),
      "BBG000BZ9223" -> TradingAsset("EOG", "usd"),
      "BBG008NVB1C0" -> TradingAsset("MNST", "usd"),
      "BBG000BWNFZ9" -> TradingAsset("WDC", "usd"),
      "BBG0078W3NQ3" -> TradingAsset("HPE", "usd"),
      "BBG0019T5SG0" -> TradingAsset("TDOC", "usd"),
      "BBG001ZZPQJ6" -> TradingAsset("ROKU", "usd"),
      "BBG000FDBX90" -> TradingAsset("CNP", "usd"),
      "BBG000FTLBV7" -> TradingAsset("IOVA", "usd"),
      "BBG00R2NHQ65" -> TradingAsset("OVV", "usd"),
      "BBG000BHLYS1" -> TradingAsset("ED", "usd"),
      "BBG00K53L394" -> TradingAsset("ETRN", "usd"),
      "BBG000BHX7N2" -> TradingAsset("EMR", "usd"),
      "BBG000C101X4" -> TradingAsset("ABMD", "usd"),
      "BBG001KS9450" -> TradingAsset("TWOU", "usd"),
      "BBG0029ZX840" -> TradingAsset("TWLO", "usd"),
      "BBG000BF6RQ9" -> TradingAsset("SAVE", "usd"),
      "BBG000BFLXV3" -> TradingAsset("PPC", "usd"),
      "BBG000FY4S11" -> TradingAsset("C", "usd"),
      "BBG000BRRG02" -> TradingAsset("PVH", "usd"),
      "BBG000BFNR17" -> TradingAsset("BFH", "usd"),
      "BBG000FQ6PY6" -> TradingAsset("CTXS", "usd"),
      "BBG001WMKHH5" -> TradingAsset("NET", "usd"),
      "BBG00475KKY8" -> TradingAsset("NVTK", "rub"),
      "BBG000BMW2Z0" -> TradingAsset("KMB", "usd"),
      "BBG00HTN2CQ3" -> TradingAsset("SPCE", "usd"),
      "BBG000BV8DN6" -> TradingAsset("TJX", "usd"),
      "BBG000BY2Y78" -> TradingAsset("IVZ", "usd"),
      "BBG0113JGQF0" -> TradingAsset("STX", "usd"),
      "BBG000HCJMF9" -> TradingAsset("PRU", "usd"),
      "BBG000QTF8K1" -> TradingAsset("DXCM", "usd"),
      "BBG000BQPC32" -> TradingAsset("F", "usd"),
      "BBG000N9MNX3" -> TradingAsset("TSLA", "usd"),
      "BBG000D9D830" -> TradingAsset("ACN", "usd"),
      "BBG000BQDF10" -> TradingAsset("LEVI", "usd"),
      "BBG001DCCGR8" -> TradingAsset("MPC", "usd"),
      "BBG000BH3GZ2" -> TradingAsset("YUM", "usd"),
      "BBG000BP52R2" -> TradingAsset("MMM", "usd"),
      "BBG000BSBZH7" -> TradingAsset("ROST", "usd"),
      "BBG000BP1Q11" -> TradingAsset("SPGI", "usd"),
      "BBG000JWD753" -> TradingAsset("LVS", "usd"),
      "BBG004S68CP5" -> TradingAsset("MVID", "rub"),
      "BBG000CX0P89" -> TradingAsset("GPN", "usd"),
      "BBG000BBGGQ1" -> TradingAsset("VLO", "usd"),
      "BBG00NNG2ZJ8" -> TradingAsset("XRX", "usd"),
      "BBG000BTR593" -> TradingAsset("SWN", "usd"),
      "BBG000BJDB15" -> TradingAsset("FCX", "usd"),
      "BBG000CTQBF3" -> TradingAsset("SBUX", "usd"),
      "BBG000FP1N32" -> TradingAsset("NTAP", "usd"),
      "BBG000C6CFJ5" -> TradingAsset("GS", "usd"),
      "BBG00F9YLST6" -> TradingAsset("BYSI", "usd"),
      "BBG001MFW6D6" -> TradingAsset("CHEF", "usd"),
      "BBG000BNSZP1" -> TradingAsset("MCD", "usd"),
      "BBG000KHWT55" -> TradingAsset("HPQ", "usd"),
      "BBG00JN4FXG8" -> TradingAsset("SLDB", "usd"),
      "BBG001MB89V6" -> TradingAsset("CLSK", "usd"),
      "BBG000K4ND22" -> TradingAsset("CVX", "usd"),
      "BBG000BKTFN2" -> TradingAsset("HAL", "usd"),
      "BBG000H9LNX1" -> TradingAsset("BJRI", "usd"),
      "BBG003NJHZT9" -> TradingAsset("DDOG", "usd"),
      "BBG000BQTMJ9" -> TradingAsset("PBI", "usd"),
      "BBG000B9ZXB4" -> TradingAsset("ABT", "usd"),
      "BBG00286S4N9" -> TradingAsset("PSX", "usd"),
      "BBG000BS0ZF1" -> TradingAsset("RL", "usd"),
      "BBG000BQWPC5" -> TradingAsset("PCG", "usd"),
      "BBG000H6HNW3" -> TradingAsset("TWTR", "usd"),
      "BBG004S68758" -> TradingAsset("BANE", "rub"),
      "BBG000MHTV89" -> TradingAsset("EPAM", "usd"),
      "BBG00B3T3HD3" -> TradingAsset("AA", "usd"),
      "BBG00KHY5S69" -> TradingAsset("AVGO", "usd"),
      "BBG000N7KJX8" -> TradingAsset("DOCU", "usd"),
      "BBG002583CV8" -> TradingAsset("PINS", "usd"),
      "BBG000BT9DW0" -> TradingAsset("SO", "usd"),
      "BBG00C6H6D40" -> TradingAsset("USFD", "usd"),
      "BBG00475K6C3" -> TradingAsset("CHMF", "rub"),
      "BBG0016SSV00" -> TradingAsset("REGI", "usd"),
      "BBG000BVMPN3" -> TradingAsset("TROW", "usd"),
      "BBG000C5HS04" -> TradingAsset("NKE", "usd"),
      "BBG000G0Z878" -> TradingAsset("HIG", "usd"),
      "BBG00BN96922" -> TradingAsset("DOW", "usd"),
      "BBG000HXJB21" -> TradingAsset("IRBT", "usd"),
      "BBG000L9CV04" -> TradingAsset("UPS", "usd"),
      "BBG000BMQPL1" -> TradingAsset("KEY", "usd"),
      "BBG000BBQCY0" -> TradingAsset("AMD", "usd"),
      "BBG004S684M6" -> TradingAsset("SIBN", "rub"),
      "BBG004S68BH6" -> TradingAsset("PIKK", "rub"),
      "BBG000BBVJZ8" -> TradingAsset("DVN", "usd"),
      "BBG000NDV1D4" -> TradingAsset("TMUS", "usd"),
      "BBG009NRSWJ4" -> TradingAsset("Z", "usd"),
      "BBG000BNPSQ9" -> TradingAsset("INCY", "usd"),
      "BBG000BR2TH3" -> TradingAsset("PG", "usd"),
      "BBG000DMBXR2" -> TradingAsset("JPM", "usd"),
      "BBG001NDW1Z7" -> TradingAsset("NTNX", "usd"),
      "BBG000BGRY34" -> TradingAsset("CVS", "usd"),
      "BBG000P5JQX6" -> TradingAsset("EHTH", "usd"),
      "BBG000CVWGS6" -> TradingAsset("ATVI", "usd"),
      "BBG00GQK3WB5" -> TradingAsset("SMAR", "usd"),
      "BBG00BLYKS03" -> TradingAsset("CRWD", "usd"),
      "BBG0084BBZY6" -> TradingAsset("SEDG", "usd"),
      "BBG008NXC572" -> TradingAsset("WRK", "usd"),
      "BBG000DQLV23" -> TradingAsset("BMY", "usd"),
      "BBG001KJ7WJ5" -> TradingAsset("AERI", "usd"),
      "BBG00B6WH9G3" -> TradingAsset("MTCH", "usd"),
      "BBG000BBL0Y1" -> TradingAsset("ALK", "usd"),
      "BBG009HSTZ10" -> TradingAsset("EAR", "usd"),
      "BBG000BN2DC2" -> TradingAsset("CRM", "usd"),
      "BBG000BDXCJ5" -> TradingAsset("CNC", "usd"),
      "BBG000DKCC19" -> TradingAsset("TSN", "usd"),
      "BBG000B9XRY4" -> TradingAsset("AAPL", "usd"),
      "BBG00MVWLLM2" -> TradingAsset("GOSS", "usd"),
      "BBG000BWBW76" -> TradingAsset("MED", "usd"),
      "BBG000QBR5J5" -> TradingAsset("DFS", "usd"),
      "BBG004NLQHL0" -> TradingAsset("FSLY", "usd"),
      "BBG0029SNR63" -> TradingAsset("CPRI", "usd"),
      "BBG000BFPK65" -> TradingAsset("CINF", "usd"),
      "BBG000BHBGN6" -> TradingAsset("CLR", "usd"),
      "BBG00JG0FFZ2" -> TradingAsset("PTON", "usd"),
      "BBG000BB6WG8" -> TradingAsset("ADM", "usd"),
      "BBG000J2XL74" -> TradingAsset("PM", "usd"),
      "BBG004M9ZHX5" -> TradingAsset("LYFT", "usd"),
      "BBG000D8RG11" -> TradingAsset("NRG", "usd")
    )

  val useHistoricalData: Option[StubSettings] = None
//    Some( //None to stream realtime market data
//      StubSettings(tickersMap)
//    )

  private def wrapBroker[F[_]](toF: IO ~> F)(broker: Broker[IO]): Broker[F] =
    new Broker[F] {
      override def placeOrder(order: Order): F[OrderPlacementInfo] =
        toF(broker.placeOrder(order))

      override def getData(
          instrumentId: InstrumentId,
          interval: Broker.CandlesInterval
      ): F[List[Bar]] =
        toF(broker.getData(instrumentId, interval))

      override def getOrderInfo(orderId: OrderId): F[OrderPlacementInfo] =
        toF(broker.getOrderInfo(orderId))
    }

  private def wrapEventsSink[F[_]](toF: IO ~> F)(eventsSink: EventsSink[IO]): EventsSink[F] =
    (event: Event) => toF(eventsSink.push(event))

  override def run(_a: List[String]): IO[ExitCode] = {
    logger.info("Hello from Algorate!")
    val factory = Factory.io
    val brokerResource = factory.tinkoffBroker.map(
      if (useHistoricalData.isDefined) TinkoffBroker.testBroker else identity
    )
    val eventsSinkResource = factory.telegramEventsSink
    val program = for {
      broker <- brokerResource
      eventsSink <- eventsSinkResource
    } yield {
      val eventsSinkFuture = wrapEventsSink(λ[IO ~> Future](_.unsafeToFuture()))(eventsSink)
      val brokerFuture = wrapBroker(λ[IO ~> Future](_.unsafeToFuture()))(broker)
      val figiList = assets.keys.toList
      val moneyTracker = TinkoffBroker.moneyTracker(broker)
      val policy = new MoneyManagementPolicy(moneyTracker)(
        maxPercentage = 0.025,
        maxAbsolute = Map(
          "usd" -> 200,
          "rub" -> 15000
        )
      )
      val tradingManager = TradingManager(
        assets = assets,
        broker = brokerFuture,
        strategy = Strategies.intraChannel,
        moneyTracker = moneyTracker,
        policy = policy,
        keepLastBars = 12 * 60,
        eventsSink = eventsSinkFuture,
        maxLag = Option.when(useHistoricalData.isEmpty)(90.seconds)
      )
      for {
        actorSystem <- IO(ActorSystem(tradingManager, "Algorate"))
        requestHandler = factory.traderRequestHandler(
          actorSystem = actorSystem,
          assets = assets.map { case (id, asset) => asset.ticker -> id },
          eventsSink = eventsSink
        )
        api = factory.traderApi(requestHandler)
        exitCode <- useHistoricalData.fold {
          {
            val subscriber = MarketSubscriber
              .fromActor(actorSystem)
              .stub[IO](
                broker,
                rate = 0.millis,
                streamFrom = LocalDate.now,
                streamTo = LocalDate.now
              )
            figiList.parTraverse(subscriber.subscribe).void
          } *> MarketSubscriber //TODO fix gap between historical and realtime data
            .fromActor(actorSystem)
            .using[IO](factory.investApi)
            .subscribe(figiList)
        } { case StubSettings(instruments, streamFrom, streamTo, rate) =>
          val subscriber = MarketSubscriber
            .fromActor(actorSystem)
            .stub[IO](
              broker,
              rate = rate,
              streamFrom = streamFrom,
              streamTo = streamTo
            )
          instruments.values.toList.parTraverse(subscriber.subscribe).void
        } &> moneyTracker.run &> api.run
      } yield exitCode
    }
    program.useEval
  }

}
