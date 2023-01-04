package com.github.ppotseluev.algorate.tools

import com.github.ppotseluev.algorate.server.Factory
import scala.jdk.CollectionConverters._

object GetAccounts extends App {

  val api = Factory.io.investApi

  val accounts = api.getUserService.getAccountsSync.asScala

  accounts.foreach { account =>
    println(account)
//    val positions = api.getUserService.gePo.getPortfolioSync(account.getBrokerAccountId)
//    positions.forEach { position =>
//      println(s"  ${position.getTicker} ${position.getLots} ${position.getAveragePositionPrice.value}")
//    }
  }
}
