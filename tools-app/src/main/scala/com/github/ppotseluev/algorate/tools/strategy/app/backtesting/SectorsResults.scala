package com.github.ppotseluev.algorate.tools.strategy.app.backtesting

import cats.derived.semiauto
import cats.{Monoid, Show}
import com.github.ppotseluev.algorate.{TradingAsset, TradingStats}

import scala.collection.immutable.ListMap
import cats.implicits._

case class SectorsResults(
    sectorsStats: Map[String, Map[TradingAsset, TradingStats]]
) {
  def flatten: Map[TradingAsset, TradingStats] = sectorsStats.flatMap(_._2)

  def assets: Set[TradingAsset] = flatten.keySet
}

object SectorsResults {
  private implicit val tickersShow: Show[Map[TradingAsset, TradingStats]] =
    (stats: Map[TradingAsset, TradingStats]) =>
      stats
        .map { case (asset, stats) =>
          s"${asset.ticker}: ${stats.show}"
        }
        .mkString("\n")

  implicit val show: Show[SectorsResults] = res =>
    res.sectorsStats.toList
      .sortBy(_._2.values.toList.map(_.totalPositions).max)
      .map { case (sector, value) =>
        val sorted: Map[TradingAsset, TradingStats] =
          ListMap.from(value.toList.sortBy(_._1.instrumentId))
        s"""
           |Sector: $sector
           |${sorted.show}
           |""".stripMargin
      }
      .mkString

  implicit val monoid: Monoid[SectorsResults] = semiauto.monoid

  def apply(asset: TradingAsset, stats: TradingStats): SectorsResults = SectorsResults(
    Map(
      asset.sector -> Map(asset -> stats)
    )
  )
}
