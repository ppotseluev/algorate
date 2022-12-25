package com.github.ppotseluev.algorate.math

import cats.data.NonEmptyList
import Approximator.Approximation
import java.util
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.fitting.AbstractCurveFitter
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoint
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.util.FastMath
import scala.jdk.CollectionConverters._

class Approximator(
    fitter: AbstractCurveFitter,
    funcBuilder: Array[Double] => UnivariateFunction
) {

  private val optimizer = new LevenbergMarquardtOptimizer()

  private def asObservedPoint(point: WeightedPoint): WeightedObservedPoint =
    new WeightedObservedPoint(point.weight, point.x, point.y)

  def approximate(points: NonEmptyList[WeightedPoint]): Approximation = {
    val fittingResult = optimizer.optimize(getProblem(points.toList.map(asObservedPoint).asJava))
    val coefs = fittingResult.getPoint.toArray
    val func = funcBuilder(coefs)
    val res = Approximation(
      func = func,
      cost = cost(func, points),
      points = points
    )
    res
  }

  def cost(approximation: Approximation, additionalPoint: WeightedPoint): Double =
    cost(approximation.func, approximation.points :+ additionalPoint)

  private def cost(func: UnivariateFunction, points: NonEmptyList[WeightedPoint]): Double = {
    val diffs = points.toList.map { point =>
      val y = func.value(point.x)
      (y - point.y) / math.max(y, point.y)
    }
    val r = new ArrayRealVector(diffs.toArray)
    FastMath.sqrt(r.dotProduct(r) / points.size)
  }

  private def getProblem(points: util.Collection[WeightedObservedPoint]): LeastSquaresProblem = {
    require(!points.isEmpty, "Empty points")
    val m = fitter.getClass.getDeclaredMethod(
      "getProblem",
      classOf[util.Collection[WeightedObservedPoint]]
    )
    m.setAccessible(true)
    try {
      m.invoke(fitter, points).asInstanceOf[LeastSquaresProblem]
    } finally {
      m.setAccessible(false)
    }
  }
}

object Approximator {
  case class Approximation(
      points: NonEmptyList[WeightedPoint],
      func: UnivariateFunction,
      cost: Double
  )

  def polynomial(fitter: PolynomialCurveFitter): Approximator =
    new Approximator(fitter, new PolynomialFunction(_))

  val Linear: Approximator =
    polynomial(PolynomialCurveFitter.create(1))
}
