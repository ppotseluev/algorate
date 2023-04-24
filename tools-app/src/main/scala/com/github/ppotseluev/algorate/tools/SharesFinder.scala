package com.github.ppotseluev.algorate.tools

import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.server.Factory
import com.google.protobuf.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.contract.v1.ShareType
import scala.jdk.CollectionConverters._

object SharesFinder extends App {

  val api = Factory.io.investApi

  def date(ts: Timestamp): LocalDate =
    LocalDate.from(
      Instant.ofEpochSecond(ts.getSeconds, ts.getNanos).atZone(ZoneOffset.UTC)
    )

  val shareFilter: Share => Boolean = share =>
    share.getApiTradeAvailableFlag &&
      share.getLot == 1 &&
      !share.getBlockedTcaFlag &&
      (share.getCurrency == "usd" || share.getCurrency == "rub") &&
      share.getShareType == ShareType.SHARE_TYPE_COMMON &&
      share.getBuyAvailableFlag &&
      share.getSellAvailableFlag &&
      date(share.getFirst1MinCandleDate).getYear <= 2020

  lazy val allShares = api.getInstrumentsService.getAllSharesSync.asScala.toList
  val shares =
    allShares
      .filter(shareFilter)
      .groupBy(_.getTicker)
      .filter(_._2.size == 1)
      .flatMap(_._2)

  def q(str: String): String = s""""$str""""

  val data = shares.flatMap { share =>
//    if (ids.contains(share.getFigi)) {
    (
      q(share.getFigi) -> TradingAsset(
        q(share.getFigi),
        q(share.getTicker),
        q(share.getCurrency),
        TradingAsset.Type.Share,
        q(share.getSector)
      )
    ).some
//    } else {
//      None
//    }
  }
  val map = data.toMap

//  val accounts = api.getUserService.getAccountsSync
//
//  val sectors = shares.map(_.getSector).toSet
  println(map.mkString("\n"))
  println(map.size)
}
