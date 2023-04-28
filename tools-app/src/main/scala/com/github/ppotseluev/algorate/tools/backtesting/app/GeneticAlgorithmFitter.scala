//package com.github.ppotseluev.algorate.tools.backtesting.app
//
//import org.apache.commons.math3.genetics._
//import org.ta4j.core._
//import org.ta4j.core.indicators._
//import org.ta4j.core.indicators.helpers.ClosePriceIndicator
//import org.ta4j.core.rules._
//import org.apache.commons.math3.genetics.RandomKeyMutation
//
//object GeneticAlgorithmFitter extends App {
//  case class StrategyParam(value: Double) extends AnyVal
//  case class ParamDescriptor(range: (Double, Double))
//  implicit class ParamsOps[T](param: StrategyParam) extends AnyVal {
//    def normalized: Double
//  }
//
//  case class StrategyChromosome()
//      extends RandomKey[StrategyParam]
//      //      AbstractListChromosome[Integer](List(shortPeriod, longPeriod))
//      {
//    //    def this() = this(
//    //      scala.util.Random.nextInt(100) + 1,
//    //      scala.util.Random.nextInt(100) + 1
//    //    )
//    //
//    //    override def fitness(): Double = fitnessFunction(getShortPeriod, getLongPeriod)
//    //
//    //    override def newFixedLengthChromosome(
//    //                                           representation: java.util.List[Integer]
//    //                                         ): AbstractListChromosome[Integer] = {
//    //      new StrategyChromosome(representation.get(0), representation.get(1))
//    //    }
//    //
//    //    def getShortPeriod: Int = getRepresentation.get(0)
//    //
//    //    def getLongPeriod: Int = getRepresentation.get(1)
//  }
//
//  // Load your time series data
//  val series: BarSeries = ???
//
//  //  // Create a fitness function to evaluate the performance of a strategy
//  //  def fitnessFunction(shortPeriod: Int, longPeriod: Int): Double = {
//  //    val shortSma = new SMAIndicator(new ClosePriceIndicator(series), shortPeriod)
//  //    val longSma = new SMAIndicator(new ClosePriceIndicator(series), longPeriod)
//  //
//  //    val entryRule = new CrossedUpIndicatorRule(shortSma, longSma)
//  //    val exitRule = new CrossedDownIndicatorRule(shortSma, longSma)
//  //
//  //    val strategy = new BaseStrategy(entryRule, exitRule)
//  //    val tradingRecord = series.run(strategy)
//  //
//  //    // Use the total profit as the fitness score
//  //    new TotalProfitCriterion().calculate(series, tradingRecord)
//  //  }
//
//  // Set up the genetic algorithm
//  val populationSize = 50
//  val generations = 100
//  val tournamentArity = 3
//  val crossoverRate = 0.8
//  val mutationRate = 0.3
//
//  val initialPopulation = new ElitisticListPopulation(populationSize, 0.1)
//  for (_ <- 1 to populationSize) {
//    initialPopulation.addChromosome(new StrategyChromosome())
//  }
//
//  val selectionPolicy = new TournamentSelection(tournamentArity)
//  val crossoverPolicy = new OnePointCrossover[Integer]()
//  val mutationPolicy = new RandomKeyMutation()
//
//  val geneticAlgorithm = new GeneticAlgorithm(
//    crossoverPolicy,
//    crossoverRate,
//    mutationPolicy,
//    mutationRate,
//    selectionPolicy
//  )
//
//  // Run the optimization
//  val finalPopulation =
//    geneticAlgorithm.evolve(initialPopulation, new FixedGenerationCount(generations))
//  val bestChromosome = finalPopulation.getFittestChromosome.asInstanceOf[StrategyChromosome]
//
//  // Display the results
//  //  println(s"Best parameters: Short period = ${bestChromosome.getShortPeriod}, Long period = ${bestCh
//}
