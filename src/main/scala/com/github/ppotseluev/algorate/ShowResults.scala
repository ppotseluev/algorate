package com.github.ppotseluev.algorate

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.ppotseluev.algorate.model.ClosePositionOrder.Type
import com.github.ppotseluev.algorate.util.Interval
import com.github.ppotseluev.algorate.util.javafx.Zoom
import com.github.ppotseluev.eann.neural.Net
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.chart.{Axis, LineChart, NumberAxis, XYChart}
import javafx.scene.layout.StackPane
import javafx.stage.Stage

import java.time.OffsetDateTime
import scala.jdk.CollectionConverters._

class ShowResults extends Application {
  private def netShow(stage: Stage, net: Net)(implicit algorate: Algorate[IO]): Unit = {
    val xAxis: Axis[Double] = new NumberAxis().asInstanceOf[Axis[Double]]
    val yAxis: Axis[Double] = new NumberAxis().asInstanceOf[Axis[Double]]
    val lineChart = new LineChart[Double, Double](xAxis, yAxis)
    val pricePlot = new XYChart.Series[Double, Double]()
    val firstPoint = algorate.priceList.head
    val timeNormalizer: OffsetDateTime => Double = time =>
      (time.toEpochSecond - firstPoint.timestamp.toEpochSecond) / 3600d
    val ordersPlot = new XYChart.Series[Double, Double]()
    val stopLossPlot = new XYChart.Series[Double, Double]()
    val takeProfitPlot = new XYChart.Series[Double, Double]()
    val signalPlot = new XYChart.Series[Double, Double]()

    val neuroSignal = algorate.signalConstructor(net)

    algorate.priceList //.take(100)
      .foreach { point =>
        neuroSignal.push(point)
        val decision = neuroSignal(point)
        val price = algorate.normalizer(point.value)
        val ts = timeNormalizer(point.timestamp)
        pricePlot.getData.add(new XYChart.Data(ts, price))
        signalPlot.getData.add(new XYChart.Data(ts, decision.rawSignal))
      }
    val newNet = new Net(net)
    pricePlot.setName("price")
    ordersPlot.setName("orders")
    takeProfitPlot.setName("take")
    stopLossPlot.setName("stop")
    signalPlot.setName("signal")

    val stats = algorate.syncTester.test(algorate.signalConstructor(newNet))
    println(stats.summary)
    stats.ordersHistory
      .foreach { order =>
        val point = order.info.point
        val price = algorate.normalizer(point.value)
        val ts = timeNormalizer(point.timestamp)
        order.info.closingOrderType match {
          case Some(value) =>
            value match {
              case Type.StopLoss   => stopLossPlot.getData.add(new XYChart.Data(ts, price))
              case Type.TakeProfit => takeProfitPlot.getData.add(new XYChart.Data(ts, price))
            }
          case None => ordersPlot.getData.add(new XYChart.Data(ts, price))
        }
      }
    lineChart.getData.addAll(
      pricePlot,
      ordersPlot,
      takeProfitPlot,
      stopLossPlot,
//      signalPlot
    )
    lineChart.setAnimated(false)
    lineChart.setCreateSymbols(true)

    val chartContainer = new StackPane
    chartContainer.getChildren.add(lineChart);
    new Zoom(lineChart.asInstanceOf[XYChart[Number, Number]], chartContainer)

    val scene = new Scene(chartContainer, 800, 600)
    scene.getStylesheets.add(getClass.getResource("/root.css").toExternalForm)
    stage.setScene(scene)
    stage.show()
  }

  private def showBestNet(stage: Stage)(implicit algorate: Algorate[IO]): Unit = {
    val path = "Results/bestNet.txt"
    val net = new Net
    net.load(path)
    net.draw()
    netShow(stage, net)
  }

  override def start(stage: Stage): Unit = {
    val token = getParameters.getRaw.asScala.head
    implicit val algorate: Algorate[IO] = new Algorate[IO](
      token = token,
//      interval = Interval(
//        OffsetDateTime.parse("2021-11-09T10:15:30+03:00"),
//        OffsetDateTime.parse("2021-11-09T22:15:30+03:00")
//      )
//      interval = Interval(
//        OffsetDateTime.parse("2020-12-18T10:15:30+03:00"),
//        OffsetDateTime.parse("2020-12-18T21:30:30+03:00")
//      )
    )
    stage.setMaximized(true)
    showBestNet(stage)
  }
}
