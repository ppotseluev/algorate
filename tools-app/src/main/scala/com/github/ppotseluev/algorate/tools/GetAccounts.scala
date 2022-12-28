package com.github.ppotseluev.algorate.tools

import ru.tinkoff.piapi.core.InvestApi
import scala.jdk.CollectionConverters._


object GetAccounts extends App {

  val api = InvestApi.create(args.head)

  val accounts = api.getUserService.getAccountsSync.asScala

  accounts.foreach { account =>
    println(account)
//    val positions = api.getUserService.gePo.getPortfolioSync(account.getBrokerAccountId)
//    positions.forEach { position =>
//      println(s"  ${position.getTicker} ${position.getLots} ${position.getAveragePositionPrice.value}")
//    }
  }
}
