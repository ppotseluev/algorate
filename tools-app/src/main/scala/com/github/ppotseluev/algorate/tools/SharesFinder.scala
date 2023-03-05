package com.github.ppotseluev.algorate.tools

import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.server.Factory
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.contract.v1.ShareType

import scala.jdk.CollectionConverters._

object SharesFinder extends App {

  val api = Factory.io.investApi

//  val shareFilter: Share => Boolean = share => {
//    share.getApiTradeAvailableFlag &&
//      share.getLot == 1 &&
//      share.getShortEnabledFlag &&
//      share.getShareType == ShareType.SHARE_TYPE_COMMON
//  }

  val ids = List(
    "BBG000BNJHS8",
    "BBG000BJF1Z8",
    "BBG000R7Z112",
    "BBG000WCFV84",
    "BBG00475K6C3",
    "BBG004PYF2N3",
    "BBG007TJF1N7",
    "BBG000C5Z1S3",
    "BBG000BNFLM9",
    "BBG000BBJQV0",
    "BBG00NNG2ZJ8",
    "BBG000C3J3C9",
    "BBG002583CV8",
    "BBG000BWNFZ9",
    "BBG000CGC1X8",
    "BBG000BKFZM4",
    "BBG000GZQ728",
    "BBG004731354",
    "BBG000K4ND22",
    "BBG000BQQ2S6",
    "BBG000BRJL00",
    "BBG000FDBX90",
    "BBG000C5HS04",
    "BBG000H8TVT2",
    "BBG000D4LWF6",
    "BBG000BNSZP1",
    "BBG000BS0ZF1",
    "BBG000CL9VN6",
    "BBG006L8G4H1",
    "BBG001M8HHB7",
    "BBG000BB6KF5",
    "BBG000BMQPL1",
    "BBG000PSKYX7",
    "BBG006Q0HY77",
    "BBG000BCTLF6",
    "BBG000G0Z878",
    "BBG000Q3JN03",
    "BBG000C17X76",
    "BBG000BBS2Y0",
    "BBG000BGRY34",
    "BBG0025Y4RY4"
  )

  lazy val allShares = api.getInstrumentsService.getAllSharesSync.asScala
//  lazy val shares = allShares.filter(shareFilter)

  def q(str: String): String = s""""$str""""

  val data = allShares.flatMap { share =>
    if (ids.contains(share.getFigi)) {
      (
        q(share.getFigi) -> TradingAsset(q(share.getTicker), q(share.getCurrency))
      ).some
    } else {
      None
    }
  }
  val map = data.toMap

//  val accounts = api.getUserService.getAccountsSync
//
//  val sectors = shares.map(_.getSector).toSet
  println(map)
}
