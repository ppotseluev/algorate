package com.github.ppotseluev.algorate.ai

import cats.Id
import com.github.ppotseluev.algorate.core.TradingSignal
import com.github.ppotseluev.algorate.test.TradingSignalTester
import com.github.ppotseluev.eann.evolutional.FitnessFunc
import com.github.ppotseluev.eann.neural.Net

class NeuroTradeFitness(
    signalTester: TradingSignalTester[Id],
    signalConstructor: Net => TradingSignal
) extends FitnessFunc {

  override def value(net: Net): Double = {
    val signal = signalConstructor(net)
    val statistics = signalTester.test(signal)
//    statistics.balanceDelta
    if (statistics.totalTriggerCount == 0) {
      0
    } else {
      statistics.successCount.toDouble / statistics.totalTriggerCount + 0.01 * statistics.totalTriggerCount
    }
  }
}
