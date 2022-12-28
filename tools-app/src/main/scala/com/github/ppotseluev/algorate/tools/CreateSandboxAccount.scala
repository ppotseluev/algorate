package com.github.ppotseluev.algorate.tools

import ru.tinkoff.piapi.contract.v1.MoneyValue
import ru.tinkoff.piapi.core.InvestApi

object CreateSandboxAccount extends App {

  val api = InvestApi.create(args.head)

  val sandbox = api.getSandboxService
  val accountId = "b24ea732-9ad6-478b-a224-8eefee2e380c"
//    sandbox.openAccountSync()
  println(s"Sandbox account: $accountId")

  val money = MoneyValue
    .newBuilder()
//    .setUnits(1_000_000)
    .setUnits(0)
    .setCurrency("RUB")
    .build()

  val balance = sandbox.payInSync(accountId, money)
  println(s"Balance: $balance")

  println(
    sandbox.getPositionsSync(accountId)
  )
}
