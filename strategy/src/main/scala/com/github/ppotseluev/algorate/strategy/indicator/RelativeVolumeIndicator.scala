package com.github.ppotseluev.algorate.strategy.indicator

import org.ta4j.core.{Bar, BarSeries, BaseBarSeries}
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.{CachedIndicator, SMAIndicator}
import org.ta4j.core.indicators.helpers._
import org.ta4j.core.num.Num

class RelativeVolumeIndicator(series: BarSeries, lookbackPeriod: Int) extends CachedIndicator[Num](series) {

  private val volumeIndicator = new VolumeIndicator(series)
  private val vma = new SMAIndicator(volumeIndicator, lookbackPeriod)
  private val minVolumeIndicator = new LowestValueIndicator(volumeIndicator, lookbackPeriod)
  private val maxVolumeIndicator = new HighestValueIndicator(volumeIndicator, lookbackPeriod)

  override def calculate(index: Int): Num = {
    val currentVolume = volumeIndicator.getValue(index)
    val vmaValue = vma.getValue(index)
    val minVolume = minVolumeIndicator.getValue(index)
    val maxVolume = maxVolumeIndicator.getValue(index)

    val volumeRange = maxVolume.minus(minVolume)
    val difference = currentVolume.minus(vmaValue)

    if (vmaValue.isZero || volumeRange.isZero) {
      series.numOf(100) // If the VMA or volume range is zero, return 100% as the relative volume indicator
    } else {
      val relativeVolume = difference.dividedBy(vmaValue).multipliedBy(series.numOf(100))
      // Normalize the percentage difference using the average range
      val normalizedRelativeVolume = relativeVolume.dividedBy(volumeRange).multipliedBy(series.numOf(100))
      normalizedRelativeVolume
    }
  }
}
