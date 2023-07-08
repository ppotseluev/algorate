package com.github.ppotseluev.algorate.trader.akkabot

import cats.implicits._
import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import cats.effect.Ref
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.ExitBounds
import com.github.ppotseluev.algorate.broker.tinkoff.BinanceBroker
import com.github.ppotseluev.algorate.strategy.FullStrategy.TradeIdea
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State.{
  WaitingCancelOrdersTicker,
  WaitingExitTicker,
  WaitingFeatureAction,
  WaitingFeatureName,
  WaitingFeatureValue,
  WaitingOrdersTicker,
  WaitingShowTicker,
  WaitingStopLoss,
  WaitingTakeProfit,
  WaitingTradingTicker
}
import com.github.ppotseluev.algorate.trader.feature.FeatureToggles
import com.github.ppotseluev.algorate.trader.telegram.TelegramClient.{
  KeyboardButton,
  Message,
  MessageSource,
  ReplyMarkup
}
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

class RequestHandlerImpl[F[_]: Sync](
    actorSystem: ActorSystem[TradingManager.Event],
    assets: Map[Ticker, InstrumentId],
    eventsSink: EventsSink[F],
    state: Ref[F, State],
    broker: BinanceBroker[F]
)(implicit featureToggles: FeatureToggles)
    extends RequestHandler[F]
    with LazyLogging {

  private implicit class StateOps[T](f: F[T]) {
    def -->(newState: State): F[Unit] = f >> state.set(newState)
  }

  private def notifyTraders(ticker: Ticker, event: InstrumentId => TradingManager.Event) =
    Sync[F].delay {
      assets.get(ticker).foreach { instrumentId =>
        actorSystem ! event(instrumentId)
      }
    } --> State.Empty

  override def handle(request: Request, reply: MessageSource => F[Unit]): F[Unit] = Sync[F]
    .defer {
      def replyT(txt: String, removeKeyboard: Option[Boolean] = true.some) = reply(
        MessageSource(
          txt,
          replyMarkup = ReplyMarkup(removeKeyboard = removeKeyboard).some
        )
      )

      val hideKeyboard = reply(
        MessageSource(
          "Enter command",
          replyMarkup = ReplyMarkup(removeKeyboard = true.some).some
        )
      )

      def requestTicker(newState: State) =
        replyT("Enter ticker") --> newState

      request match {
        case Request.GetBalance =>
          broker
            .getBalance(nonZero = true)
            .map(_.toString)
            .flatMap(replyT(_))
        case Request.CancelOrders  => requestTicker(WaitingCancelOrdersTicker)
        case Request.GetOpenOrders => requestTicker(WaitingOrdersTicker(onlyOpen = true))
        case Request.GetAllOrders  => requestTicker(WaitingOrdersTicker(onlyOpen = false))
        case Request.ShowState     => requestTicker(WaitingShowTicker)
        case Request.Sell          => requestTicker(WaitingTradingTicker(OperationType.Sell))
        case Request.Buy           => requestTicker(WaitingTradingTicker(OperationType.Buy))
        case Request.Exit          => requestTicker(WaitingExitTicker)
        case Request.GeneralInput(input) =>
          val ticker =
            s"${input.toUpperCase.stripSuffix("USDT")}USDT" //TODO can be non-crypto asset
          val unexpectedInputReply = replyT(s"Unexpected input `$input`")
          def parse[T](f: String => T): F[T] =
            Sync[F].catchNonFatal(f(input)).onError { case _ => unexpectedInputReply }
          state.get
            .flatMap {
              case State.Empty => unexpectedInputReply
              case WaitingCancelOrdersTicker =>
                broker.cancelAllOrders(ticker) >> replyT("Done") --> State.Empty
              case WaitingTradingTicker(operation) =>
                replyT("Enter stop-loss") --> WaitingStopLoss(operation, ticker)
              case WaitingStopLoss(op, ticker) =>
                parse(_.toDouble).flatMap { stopLoss =>
                  replyT("Enter take-profit") --> WaitingTakeProfit(op, ticker, stopLoss)
                }
              case WaitingTakeProfit(operationType, ticker, stopLoss) =>
                parse(_.toDouble).flatMap { takeProfit =>
                  val trade = TradeIdea(
                    operationType,
                    ExitBounds(
                      stopLoss = stopLoss,
                      takeProfit = takeProfit
                    )
                  )
                  notifyTraders(ticker, TradingManager.Event.TradeRequested(_, trade))
                }
              case WaitingShowTicker =>
                notifyTraders(ticker, TradingManager.Event.TraderSnapshotRequested)
              case WaitingExitTicker =>
                notifyTraders(ticker, TradingManager.Event.ExitRequested)
              case WaitingOrdersTicker(onlyOpen) =>
                broker
                  .getOrders(ticker, onlyOpen)
                  .map { orders =>
                    if (orders.isEmpty) "No orders"
                    else orders.mkString("\n\n").replaceAll(",", "\n")
                  }
                  .flatMap(replyT(_)) --> State.Empty
              case WaitingFeatureName =>
                val name = input
                featureToggles.find(name) match {
                  case Some(feature) =>
                    val msg = MessageSource(
                      text = s"Current value: ${feature.apply()}",
                      replyMarkup = ReplyMarkup(
                        Seq(
                          Seq(KeyboardButton("Ok"), KeyboardButton("Update"))
                        ).some
                      ).some
                    )
                    reply(msg) --> WaitingFeatureAction(name)
                  case None =>
                    replyT("No such feature", removeKeyboard = none)
                }
              case WaitingFeatureAction(featureName) =>
                input match {
                  case "Ok"     => hideKeyboard --> State.Empty
                  case "Update" => replyT("Enter new value") --> WaitingFeatureValue(featureName)
                  case _        => unexpectedInputReply
                }
              case WaitingFeatureValue(featureName) =>
                val feature = featureToggles.find(featureName).get
                feature.set(input) match {
                  case Left(error) =>
                    replyT(s"Can't update: $error")
                  case Right(()) =>
                    replyT("Updated successfully") --> State.Empty
                }
            }
        case Request.ShowActiveTrades => ???
        case Request.Features =>
          val features = featureToggles.list
          val buttons = features
            .map(_.name)
            .map(KeyboardButton.apply)
            .map(Seq(_))
          val msg = MessageSource(
            text = "Select feature",
            replyMarkup = ReplyMarkup(buttons.some).some
          )
          reply(msg) --> State.WaitingFeatureName
      }
    }
    .recover { case NonFatal(t) =>
      logger.error("Can't handle request", t)
    }
}

object RequestHandlerImpl {
  sealed trait State
  object State {
    case object Empty extends State
    case class WaitingTradingTicker(operation: OperationType) extends State
    case object WaitingExitTicker extends State
    case object WaitingShowTicker extends State
    case object WaitingCancelOrdersTicker extends State
    case class WaitingOrdersTicker(onlyOpen: Boolean) extends State
    case object WaitingFeatureName extends State
    case class WaitingFeatureAction(featureName: String) extends State
    case class WaitingFeatureValue(featureName: String) extends State
    case class WaitingStopLoss(operationType: OperationType, ticker: Ticker) extends State
    case class WaitingTakeProfit(operationType: OperationType, ticker: Ticker, stopLoss: Price)
        extends State
  }
}
