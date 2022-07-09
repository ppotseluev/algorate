//package com.github.ppotseluev.algorate.deprecated.ai
//
//import cats.Id
//import com.github.ppotseluev.algorate.core.TradingSignal
//import com.github.ppotseluev.algorate.deprecated.test.TradingSignalTester
//import com.github.ppotseluev.eann.evolutional.FitnessFunc
//import com.github.ppotseluev.eann.neural.Net
//
//class NeuroTradeFitness(
//    signalTester: TradingSignalTester[Id],
//    signalConstructor: Net => TradingSignal
//) extends FitnessFunc {
//
//  override def value(net: Net): Double = {
//    val signal = signalConstructor(net)
//    val statistics = signalTester.test(signal)
////    statistics.balanceDelta
//    val totalClosed = statistics.successCount + statistics.failureCount
//    if (totalClosed == 0) {
//      0
//    } else {
//      statistics.successCount.toDouble / totalClosed // + 0.01 * totalClosed
//    }
//  }
//}
