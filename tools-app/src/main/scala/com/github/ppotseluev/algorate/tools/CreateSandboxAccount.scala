package com.github.ppotseluev.algorate.tools

import com.github.ppotseluev.algorate.server.Factory

import scala.jdk.CollectionConverters._
//import ru.tinkoff.piapi.contract.v1.MoneyValue

object CreateSandboxAccount extends App {

  val api = Factory.io.investApi

//  val sandbox = api.getSandboxService
//  val accountId = "b24ea732-9ad6-478b-a224-8eefee2e380c"
//    sandbox.openAccountSync()
//  println(s"Sandbox account: $accountId")

//  val money = MoneyValue
//    .newBuilder()
//    .setUnits(5_000)
//    .setCurrency("USD")
//    .build()
//
//  val balance = sandbox.payInSync(accountId, money)
//  println(s"Balance: $balance")
//
//  println(
//    sandbox.getPositionsSync(accountId)
//  )
//
//  val inst = api.getInstrumentsService.getInstrumentByFigiSync("BBG0013HGFT4")
//  println(inst)

//  val portfolio = sandbox.getPortfolioSync(accountId)
//  portfolio.get

//  val portfolio = api.getOperationsService.getPortfolioSync(accountId)
//  val money = portfolio.getTotalAmountCurrencies

//  val accounts = api.getUserService.getAccountsSync.asScala
//  val accId = "2016644698"
  val accId = "b24ea732-9ad6-478b-a224-8eefee2e380c"
  val positions = api.getOperationsService.getPositionsSync(accId)
  val portfolio = api.getOperationsService.getPortfolioSync(accId)

  ???
}
