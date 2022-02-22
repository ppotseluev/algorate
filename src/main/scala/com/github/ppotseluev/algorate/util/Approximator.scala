package com.github.ppotseluev.algorate.util

import cats.data.NonEmptyList
import com.github.ppotseluev.algorate.util.Approximator.Approximation
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.fitting.leastsquares.{LeastSquaresProblem, LevenbergMarquardtOptimizer}
import org.apache.commons.math3.fitting.{AbstractCurveFitter, PolynomialCurveFitter, WeightedObservedPoint}
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.util.FastMath

import java.util
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
    val res = Approximation(
      func = funcBuilder(coefs),
      cost = fittingResult.getRMS,
      points = points
    )
    res
  }

  def cost(approximation: Approximation, additionalPoint: WeightedPoint): Double = {
    val diffs = (approximation.points.toList :+ additionalPoint).map { point =>
      approximation.func.value(point.x) - point.y
    }
    val r = new ArrayRealVector(diffs.toArray)
    val cost = FastMath.sqrt(r.dotProduct(r))
    val observationsSize = approximation.points.size + 1
    FastMath.sqrt(cost * cost / observationsSize)
  }

  private def getProblem(points: util.Collection[WeightedObservedPoint]): LeastSquaresProblem = {
    require(!points.isEmpty, "Empty points")
    val m = fitter.getClass.getDeclaredMethod("getProblem", classOf[util.Collection[WeightedObservedPoint]])
    m.setAccessible(true)
    m.invoke(fitter, points).asInstanceOf[LeastSquaresProblem]
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
