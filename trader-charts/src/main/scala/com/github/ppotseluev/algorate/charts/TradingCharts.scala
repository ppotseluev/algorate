package com.github.ppotseluev.algorate.charts

import com.github.ppotseluev.algorate.Ta4jUtils.BarSeriesOps
import com.github.ppotseluev.algorate.TradingStats
import com.github.ppotseluev.algorate.strategy.FullStrategy
import com.github.ppotseluev.algorate.strategy.FullStrategy.IndicatorInfo
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation
import com.github.ppotseluev.algorate.strategy.FullStrategy.Representation.Line
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import org.jfree.chart._
import org.jfree.chart.axis._
import org.jfree.chart.panel.CrosshairOverlay
import org.jfree.chart.plot._
import org.jfree.chart.renderer.xy._
import org.jfree.data.time._
import org.jfree.ui.RectangleEdge
import org.jfree.ui.RefineryUtilities
import org.ta4j.core.BarSeries
import org.ta4j.core.Indicator
import org.ta4j.core.Position
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.Num

object TradingCharts {

  /**
   * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
   *
   * @param barSeries the ta4j bar series
   * @param indicator the indicator
   * @param name      the name of the chart time series
   * @return the JFreeChart time series
   */
  private def buildChartTimeSeries(
      barSeries: BarSeries,
      indicator: Indicator[Num],
      name: String
  ): TimeSeries = {
    val chartTimeSeries = new TimeSeries(name)
    for (i <- barSeries.getStartIndex to barSeries.getEndIndex) {
      val bar = barSeries.getBar(i)
      chartTimeSeries.addOrUpdate(
        new Minute(Date.from(bar.getEndTime.toInstant)),
        indicator.getValue(i).doubleValue
      )
    }
    chartTimeSeries
  }

  /**
   * Runs a strategy over a bar series and adds the value markers corresponding to
   * buy/sell signals to the plot.
   *
   * @param series   the bar series
   * @param strategy the trading strategy
   * @param plot     the plot
   */
  private def addBuySellSignals(
      series: BarSeries,
      positions: Seq[Position],
      plots: XYPlot*
  ): Unit = {
    // Adding markers to plot
    for ((position, idx) <- positions.zipWithIndex) {
      // Buy signal
      val buySignalBarTime: Double = new Minute(
        Date.from(
          series
            .getBar(position.getEntry.getIndex)
            .getEndTime
            .toInstant
        )
      ).getFirstMillisecond.toDouble
      val buyMarker: Marker = new ValueMarker(buySignalBarTime)
      buyMarker.setPaint(Color.GREEN)
      val entryLabel = position.getEntry.getType match {
        case TradeType.BUY  => s"LONG_$idx"
        case TradeType.SELL => s"SHORT_$idx"
      }
      buyMarker.setLabel(entryLabel)
      // Sell signal
      val sellSignalBarTime: Double = new Minute(
        Date.from(
          series
            .getBar(position.getExit.getIndex)
            .getEndTime
            .toInstant
        )
      ).getFirstMillisecond.toDouble
      val sellMarker: Marker = new ValueMarker(sellSignalBarTime)
      sellMarker.setPaint(Color.RED)
      val closeLabel = position.getEntry.getType match {
        case TradeType.BUY  => s"EXIT_LONG_$idx"
        case TradeType.SELL => s"EXIT_SHORT_$idx"
      }
      sellMarker.setLabel(closeLabel)
      plots.foreach { plot =>
        plot.addDomainMarker(buyMarker)
        plot.addDomainMarker(sellMarker)
      }
    }
  }

  private def buildPanel(chart: JFreeChart): ChartPanel = {
    // Chart panel
    val panel = new ChartPanel(chart)
    val xCrosshair = new Crosshair(Double.NaN, Color.GRAY, new BasicStroke(0f))
    xCrosshair.setLabelVisible(true)
    val yCrosshair = new Crosshair(Double.NaN, Color.GRAY, new BasicStroke(0f))
    yCrosshair.setLabelVisible(true)
    panel.addChartMouseListener(new ChartMouseListener {
      override def chartMouseClicked(event: ChartMouseEvent): Unit = ()

      override def chartMouseMoved(event: ChartMouseEvent): Unit = {
        val dataArea = panel.getScreenDataArea
        val chart = event.getChart
        val plot = chart.getPlot.asInstanceOf[XYPlot]
        val xAxis = plot.getDomainAxis
        val x = xAxis.java2DToValue(event.getTrigger.getX, dataArea, RectangleEdge.BOTTOM)
        val y = plot.getRangeAxis.java2DToValue(event.getTrigger.getY, dataArea, RectangleEdge.LEFT)
        xCrosshair.setValue(x)
        yCrosshair.setValue(y)
      }
    })
    val crosshairOverlay = new CrosshairOverlay
    crosshairOverlay.addDomainCrosshair(xCrosshair)
    crosshairOverlay.addRangeCrosshair(yCrosshair)
    panel.addOverlay(crosshairOverlay)
    panel.setFillZoomRectangle(true)
    panel.setMouseWheelEnabled(true)
    panel.setPreferredSize(new Dimension(1024, 400))
    panel
  }

  private def display(panel: Container, title: String): Unit = {
    // Application frame
    val frame = new NonClosingApplicationFrame(title)
    frame.setContentPane(panel)
    frame.pack()
    RefineryUtilities.centerFrameOnScreen(frame)
    frame.setVisible(true)
  }

  private def createChart(
      strategyBuilder: BarSeries => FullStrategy,
      series: BarSeries,
      tradingStats: Option[TradingStats],
      title: String
  ): JFreeChart = {
    def addIndicators(
        series: BarSeries,
        dataset: TimeSeriesCollection,
        indicators: Map[String, IndicatorInfo]
    ): Unit = {
      indicators.foreach { case (name, IndicatorInfo(indicator, _)) =>
        dataset.addSeries(
          buildChartTimeSeries(series, indicator, name)
        )
      }
    }

    val mainDataset = new TimeSeriesCollection
    val mainPointsDataset = new TimeSeriesCollection
    val indicatorsDataset = new TimeSeriesCollection
    val strategy = strategyBuilder(series)
    addIndicators(
      series,
      mainDataset,
      strategy.priceIndicators.filter(_._2.representation.isInstanceOf[Line.type])
    )
    addIndicators(
      series,
      mainPointsDataset,
      strategy.priceIndicators.filter(_._2.representation.isInstanceOf[Representation.Points.type])
    )
    addIndicators(series, indicatorsDataset, strategy.oscillators)
    val xAxis = new DateAxis("Time")
    xAxis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"))
    val pricePlot: XYPlot =
      new XYPlot(mainDataset, xAxis, new NumberAxis("Price"), new StandardXYItemRenderer())
    pricePlot.setDataset(1, mainPointsDataset)
    pricePlot.setRenderer(1, new XYShapeRenderer)
    pricePlot.getRangeAxis.setAutoRange(true)
    pricePlot.getRangeAxis.asInstanceOf[NumberAxis].setAutoRangeIncludesZero(false)
    val indicatorPlot: XYPlot =
      new XYPlot(
        indicatorsDataset,
        xAxis,
        new NumberAxis("Indicators"),
        new StandardXYItemRenderer()
      )
    indicatorPlot.getRangeAxis.setAutoRange(true)
    indicatorPlot.getRangeAxis.asInstanceOf[NumberAxis].setAutoRangeIncludesZero(false)
    val combinedPlot = new CombinedDomainXYPlot(xAxis) // DateAxis
    combinedPlot.setGap(10.0)
    combinedPlot.setDomainAxis(xAxis)
    val yAxis = new NumberAxis("y")
    combinedPlot.setRangeAxis(yAxis)
    combinedPlot.setBackgroundPaint(Color.LIGHT_GRAY)
    combinedPlot.setDomainGridlinePaint(Color.GRAY)
    combinedPlot.setRangeGridlinePaint(Color.GRAY)
    combinedPlot.setOrientation(PlotOrientation.VERTICAL)
    combinedPlot.add(pricePlot, 70)
    combinedPlot.add(indicatorPlot, 30)

    tradingStats.foreach { stats =>
      TradingCharts.addBuySellSignals(series, stats.long.positions, pricePlot, indicatorPlot)
      TradingCharts.addBuySellSignals(series, stats.short.positions, pricePlot, indicatorPlot)
    }
    new JFreeChart(title, null, combinedPlot, true)
  }

  def display(
      strategyBuilder: BarSeries => FullStrategy,
      series: BarSeries,
      tradingStats: Option[TradingStats],
      title: String
  ): Unit = {
    val chart = createChart(strategyBuilder, series, tradingStats, title)
    val panel = buildPanel(chart)
    display(panel, "Algorate")
  }

  def buildImage(
      strategyBuilder: BarSeries => FullStrategy,
      series: BarSeries,
      tradingStats: Option[TradingStats],
      title: String
  ): Array[Byte] = {
    val chart = createChart(strategyBuilder, series, tradingStats, title)
    val outputStream = new ByteArrayOutputStream()
    ChartUtilities.writeChartAsPNG(outputStream, chart, 1024, 400)
    outputStream.toByteArray
  }

}
