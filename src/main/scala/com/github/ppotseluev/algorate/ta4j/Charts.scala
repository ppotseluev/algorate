package com.github.ppotseluev.algorate.ta4j

import com.github.ppotseluev.algorate.ta4j.StrategyTester.TradingStats
import org.jfree.chart.axis.{DateAxis, NumberAxis}
import org.jfree.chart.panel.CrosshairOverlay
import org.jfree.chart.plot._
import org.jfree.chart.renderer.xy.StandardXYItemRenderer
import org.jfree.chart.{ChartMouseEvent, ChartMouseListener, ChartPanel, JFreeChart}
import org.jfree.data.time.{Minute, TimeSeries, TimeSeriesCollection}
import org.jfree.ui.{ApplicationFrame, RectangleEdge, RefineryUtilities}
import org.ta4j.core.Trade.TradeType
import org.ta4j.core.num.Num
import org.ta4j.core.{BarSeries, Indicator, Position}

import java.awt.{BasicStroke, Color, Dimension}
import java.text.SimpleDateFormat
import java.util.Date

object Charts {

  /**
   * Builds a JFreeChart time series from a Ta4j bar series and an indicator.
   *
   * @param barSeries the ta4j bar series
   * @param indicator the indicator
   * @param name      the name of the chart time series
   * @return the JFreeChart time series
   */
  def buildChartTimeSeries(
      barSeries: BarSeries,
      indicator: Indicator[Num],
      name: String
  ): TimeSeries = {
    val chartTimeSeries = new TimeSeries(name)
    for (i <- 0 until barSeries.getBarCount) {
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
  def addBuySellSignals(
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

  /**
   * Displays a chart in a frame.
   *
   * @param chart the chart to be displayed
   */
  def displayChart(chart: JFreeChart, title: String): Unit = {
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
    // Application frame
    val frame = new ApplicationFrame(title)
    frame.setContentPane(panel)
    frame.pack()
    RefineryUtilities.centerFrameOnScreen(frame)
    frame.setVisible(true)
  }

  def display(
               strategyBuilder: BarSeries => FullStrategy,
               series: BarSeries,
               tradingStats: TradingStats,
               title: String
  ): Unit = {
    def addIndicators(
        series: BarSeries,
        dataset: TimeSeriesCollection,
        indicators: Map[String, Indicator[Num]]
    ): Unit = {
      indicators.foreach { case (name, indicator) =>
        dataset.addSeries(
          buildChartTimeSeries(series, indicator, name)
        )
      }
    }
    val mainDataset = new TimeSeriesCollection
    val indicatorsDataset = new TimeSeriesCollection
    val strategy = strategyBuilder(series)
    addIndicators(series, mainDataset, strategy.priceIndicators)
    addIndicators(series, indicatorsDataset, strategy.oscillators)
    val xAxis = new DateAxis("Time")
    xAxis.setDateFormatOverride(new SimpleDateFormat("MM-dd HH:mm"))
    val pricePlot: XYPlot =
      new XYPlot(mainDataset, xAxis, new NumberAxis("Price"), new StandardXYItemRenderer())
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

    Charts.addBuySellSignals(series, tradingStats.long.positions, pricePlot, indicatorPlot)
    Charts.addBuySellSignals(series, tradingStats.short.positions, pricePlot, indicatorPlot)

    /*
     * Creating the chart
     */
    val chart = new JFreeChart(title, null, combinedPlot, true)

    /*
     * Displaying the chart
     */
    Charts.displayChart(chart, "Algorate")
  }

}
