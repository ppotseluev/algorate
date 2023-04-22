package com.github.ppotseluev.algorate.tools.strategy

import com.github.ppotseluev.algorate.Money
import com.github.ppotseluev.algorate.broker.Broker.CandleResolution.OneMinute
import com.github.ppotseluev.algorate.broker.Broker.CandlesInterval
import com.github.ppotseluev.algorate.broker.Broker.DaysInterval
import com.github.ppotseluev.algorate.strategy.Strategies
import com.github.ppotseluev.algorate.trader.policy.MoneyManagementPolicy
import com.github.ppotseluev.algorate.trader.policy.Policy
import java.time.LocalDate

private[strategy] object TestSetup { //TODO remove this shared setup

  val interval = CandlesInterval(
    interval = DaysInterval(
      LocalDate.of(2023, 1, 1),
      LocalDate.of(2023, 4, 11)
    ),
    resolution = OneMinute
  )

  val strategy = Strategies.default

  def fixedTradeCostPolicy(
      usdTrade: Int = 100,
      rubTrade: Int = 10_000,
      allowFractionalLots: Boolean
  ): Policy = {
    val money: Money = Map("usd" -> Int.MaxValue, "rub" -> Int.MaxValue, "usdt" -> Int.MaxValue)
    new MoneyManagementPolicy(() => Some(money))(
      maxPercentage = 1,
      maxAbsolute = Map(
        "usd" -> usdTrade,
        "usdt" -> usdTrade,
        "rub" -> rubTrade
      ),
      allowFractionalLots = allowFractionalLots
    )
  }

  val ids = List(
    "BBG000BNJHS8",
    "BBG000BRJL00",
    "BBG000BBJQV0",
    "BBG000BBS2Y0",
    "BBG006Q0HY77",
    "BBG00NNG2ZJ8",
    "BBG000BNSZP1",
    "BBG000K4ND22",
    "BBG000BGRY34",
    "BBG006L8G4H1",
    "BBG007TJF1N7",
    "BBG004PYF2N3",
    "BBG0025Y4RY4",
    "BBG000C17X76",
    "BBG000CL9VN6",
    "BBG001M8HHB7",
    "BBG000H8TVT2",
    "BBG000PSKYX7",
    "BBG000BB6KF5",
    "BBG000C3J3C9",
    "BBG000BKFZM4",
    "BBG000D4LWF6",
    "BBG004731354",
    "BBG000GZQ728",
    "BBG000CGC1X8",
    "BBG000R7Z112",
    "BBG000BQQ2S6",
    "BBG000WCFV84",
    "BBG000C5Z1S3",
    "BBG000BCTLF6",
    "BBG000BJF1Z8",
    "BBG000Q3JN03",
    "BBG000BNFLM9",
    "BBG000BWNFZ9",
    "BBG000FDBX90",
    "BBG000BS0ZF1",
    "BBG00475K6C3",
    "BBG002583CV8",
    "BBG000C5HS04",
    "BBG000G0Z878",
    "BBG000BMQPL1"
  )
}
