package com.github.ppotseluev.algorate.trader.akkabot

import cats.implicits._
import akka.actor.typed.ActorSystem
import cats.effect.kernel.Sync
import cats.effect.Ref
import com.github.ppotseluev.algorate._
import com.github.ppotseluev.algorate.broker.Broker
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State
import com.github.ppotseluev.algorate.trader.akkabot.RequestHandlerImpl.State.{
  WaitingExitTicker,
  WaitingFeatureAction,
  WaitingFeatureName,
  WaitingFeatureValue,
  WaitingShowTicker,
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

class RequestHandlerImpl[F[_]: Sync](
    actorSystem: ActorSystem[TradingManager.Event],
    assets: Map[Ticker, InstrumentId],
    eventsSink: EventsSink[F],
    state: Ref[F, State],
    broker: Broker[F]
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

  override def handle(request: Request, reply: MessageSource => F[Unit]): F[Unit] = Sync[F].defer {
    def replyT(txt: String) = reply(
      MessageSource(
        txt,
        replyMarkup = ReplyMarkup(removeKeyboard = true.some).some
      )
    )

    def requestTicker(newState: State) =
      replyT("Enter ticker") --> newState

    request match {
      case Request.GetBalance =>
        broker.getBalance.flatMap { balance =>
          replyT(balance.toString)
        }
      case Request.ShowState => requestTicker(WaitingShowTicker)
      case Request.Sell      => requestTicker(WaitingTradingTicker(OperationType.Sell))
      case Request.Buy       => requestTicker(WaitingTradingTicker(OperationType.Buy))
      case Request.Exit      => requestTicker(WaitingExitTicker)
      case Request.GeneralInput(input) =>
        val ticker = s"${input.toUpperCase.stripSuffix("USDT")}USDT" //TODO can be non-crypto asset
        val unexpectedInputReply = replyT(s"Unexpected input `$input`")
        state.get
          .flatMap {
            case State.Empty => unexpectedInputReply
            case WaitingTradingTicker(operation) =>
              notifyTraders(ticker, TradingManager.Event.TradeRequested(_, operation))
            case WaitingShowTicker =>
              notifyTraders(ticker, TradingManager.Event.TraderSnapshotRequested)
            case WaitingExitTicker =>
              notifyTraders(ticker, TradingManager.Event.ExitRequested)
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
                  replyT("No such feature")
              }
            case WaitingFeatureAction(featureName) =>
              input match {
                case "Ok"     => state.set(State.Empty)
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
        val buttons = features.map(_.name).map(KeyboardButton.apply).map(Seq(_))
        val msg = MessageSource(
          text = "Select feature",
          replyMarkup = ReplyMarkup(buttons.some).some
        )
        reply(msg) --> State.WaitingFeatureName
    }
  }
}

object RequestHandlerImpl {
  sealed trait State
  object State {
    case object Empty extends State
    case class WaitingTradingTicker(operation: OperationType) extends State
    case object WaitingExitTicker extends State
    case object WaitingShowTicker extends State
    case object WaitingFeatureName extends State
    case class WaitingFeatureAction(featureName: String) extends State
    case class WaitingFeatureValue(featureName: String) extends State
  }
}
