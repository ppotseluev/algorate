package com.github.ppotseluev.algorate.tools.backtesting

import cats.Monoid
import cats.Show
import cats.derived.semiauto
import cats.implicits._
import com.github.ppotseluev.algorate.TradingAsset
import com.github.ppotseluev.algorate.TradingStats
import scala.collection.immutable.ListMap

case class SectorsResults(
    sectorsStats: Map[String, Map[TradingAsset, TradingStats]]
) {
  def keepOnly(assets: Set[TradingAsset]): SectorsResults = SectorsResults(
    sectorsStats.map { case (sector, res) =>
      sector -> res.filter { case (asset, _) => assets.contains(asset) }
    }
  )

  def exclude(excludeAssets: Set[TradingAsset]): SectorsResults =
    keepOnly(assets -- excludeAssets)

  def flatten: Map[TradingAsset, TradingStats] = sectorsStats.flatMap(_._2)

  def assets: Set[TradingAsset] = flatten.keySet

  def aggregatedStats: TradingStats = sectorsStats.values.flatMap(_.values).toList.combineAll
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
          ListMap.from(value.toList.sortBy(_._2.profit(fee = true).values.sum))
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
