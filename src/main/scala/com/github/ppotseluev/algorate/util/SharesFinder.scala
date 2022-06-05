package com.github.ppotseluev.algorate.util

import ru.tinkoff.piapi.contract.v1.{RealExchange, Share}
import ru.tinkoff.piapi.core.InvestApi

import scala.jdk.CollectionConverters._

object SharesFinder extends App {

  val api = InvestApi.create(args.head)

  val shareFilter: Share => Boolean = share => {
    share.getRealExchange == RealExchange.REAL_EXCHANGE_MOEX
    share.getApiTradeAvailableFlag  }

  val shares = api.getInstrumentsService.getAllSharesSync.asScala
    .filter(shareFilter)
}
