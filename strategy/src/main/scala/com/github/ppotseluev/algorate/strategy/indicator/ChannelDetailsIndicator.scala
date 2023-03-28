//package com.github.ppotseluev.algorate.strategy.indicator
//
//import cats.Monoid
//import cats.derived.semiauto
//import cats.implicits._
//import com.github.ppotseluev.algorate.math.Approximator.Approximation
//import com.github.ppotseluev.algorate.strategy.indicator.ChannelDetailsIndicator.{
//  BoundData,
//  ChannelDetails
//}
//import com.github.ppotseluev.algorate.strategy.indicator.ChannelIndicator.Channel
//import org.ta4j.core.indicators.{AbstractIndicator, RecursiveCachedIndicator}
//import org.ta4j.core.num.{NaN, Num}
//
//class ChannelDetailsIndicator(
//    channelIndicator: AbstractIndicator[Option[Channel]],
//    baseIndicator: AbstractIndicator[Num],
//    mergeWithin: Int
//) extends RecursiveCachedIndicator[Option[ChannelDetails]](channelIndicator.getBarSeries) {
//
//  private def getBaseValue(index: Int): Num =
//    baseIndicator.getValue(index)
//
//  private def baseValueDouble(index: Int): Double =
//    getBaseValue(index).doubleValue
//
//  override protected def calculate(index: Int): Option[ChannelDetails] =
//    channelIndicator.getValue(index).map { actualChannel =>
//      getValue(index - 1) match {
//        case Some(lastDetails) if lastDetails.channel.startIndex == actualChannel.startIndex =>
//          actualize(index, lastDetails, actualChannel)
//        case _ => analyze(index, actualChannel)
//      }
//    }
//
//  private def forIndex(channel: Channel)(index: Int): Bounds[BoundData] = {
//    val points = (index - mergeWithin + 1).to(index).toSet
//    channel.bounds
//      .map(cross(points))
//      .map(_.map(i => Point(i, getBaseValue(i))))
//      .map {
//        _.map { p =>
//          BoundData(Map(p.index -> p), toRemove = points - p.index)
//        }.orEmpty
//      }
//  }
//
//  private def analyze(currentIndex: Int, channel: Channel): ChannelDetails = {
//    val boundsDetails =
//      (channel.startIndex + 1 to currentIndex).toList
//        .map(forIndex(channel))
//        .combineAll
//        .map(_.actual)
//    ChannelDetails(channel, boundsDetails)
//  }
//
//  private def cross(points: Set[Int])(appr: Approximation): Option[Int] =
//    points
//      .flatMap { index =>
//        val f = appr.func
//        val priceCrossDown =
//          f.value(index) >= baseValueDouble(index) && f.value(index - 1) <= baseValueDouble(
//            index - 1
//          )
//        val priceCrossUp =
//          f.value(index) <= baseValueDouble(index) && f.value(index - 1) >= baseValueDouble(
//            index - 1
//          )
//        Option.when(priceCrossUp || priceCrossDown)(index)
//      }
//      .minByOption(i => math.abs(appr.func.value(i) - baseValueDouble(i)))
//  //todo remove points that were candidates but now are not the best......
//
//  private def actualize(
//      currentIndex: Int,
//      lastDetails: ChannelDetails,
//      actualChannel: Channel
//  ): ChannelDetails = {
//    val details = lastDetails.boundsDetails |+| forIndex(actualChannel)(currentIndex)
//    ChannelDetails(
//      channel = actualChannel,
//      boundsDetails = details.map(_.actual)
//    )
//  }
//
//  private def as(f: ((Int, ChannelDetails)) => Num): AbstractIndicator[Num] =
//    new AbstractIndicator[Num](getBarSeries) {
//      override def getValue(index: Int): Num =
//        ChannelDetailsIndicator.this
//          .getValue(index)
//          .fold(NaN.NaN)(value => f(index -> value))
//    }
//
//  private def asOpt(f: ((Int, ChannelDetails)) => Option[Num]): AbstractIndicator[Num] =
//    as(f andThen (_.getOrElse(NaN.NaN)))
//
//  def crossPoints(bound: Bounds[BoundData] => BoundData): AbstractIndicator[Num] =
//    asOpt { case (index, details) =>
//      bound(details.boundsDetails).crossPointsMap.get(index).map(_.value)
//    }
//}
//
//object ChannelDetailsIndicator {
//  case class BoundData(
//      crossPointsMap: Map[Int, Point],
//      toRemove: Set[Int]
//  ) {
//    def actual = BoundData(crossPointsMap.removedAll(toRemove), Set.empty)
//  }
//
//  object BoundData {
//    private implicit val crossPointsMonoid: Monoid[Map[Int, Point]] =
//      new Monoid[Map[Int, Point]] {
//        override def empty: Map[Int, Point] = Map.empty
//
//        override def combine(x: Map[Int, Point], y: Map[Int, Point]): Map[Int, Point] =
//          x.alignMergeWith(y) { (p1, p2) =>
//            if (p1 == p2) p1
//            else throw new RuntimeException(s"Can not merge points $p1, $p2")
//          }
//      }
//    implicit val monoid: Monoid[BoundData] = semiauto.monoid
//  }
//
//  case class ChannelDetails(
//      channel: Channel,
//      boundsDetails: Bounds[BoundData]
//  )
//}
