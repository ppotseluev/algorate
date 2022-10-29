package com.github.ppotseluev.algorate.util

import ru.tinkoff.piapi.contract.v1.Share
import ru.tinkoff.piapi.contract.v1.ShareType
import ru.tinkoff.piapi.core.InvestApi
import scala.jdk.CollectionConverters._

object SharesFinder extends App {

  val api = InvestApi.create(args.head)

  val shareFilter: Share => Boolean = share => {
    share.getApiTradeAvailableFlag &&
      share.getLot == 1 &&
      share.getShortEnabledFlag &&
      share.getShareType == ShareType.SHARE_TYPE_COMMON
  }

  lazy val allShares = api.getInstrumentsService.getAllSharesSync.asScala
  lazy val shares = allShares.filter(shareFilter)

  val accounts = api.getUserService.getAccountsSync

  val sectors = shares.map(_.getSector).toSet
  ???
}
