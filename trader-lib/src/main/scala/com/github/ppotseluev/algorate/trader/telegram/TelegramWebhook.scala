package com.github.ppotseluev.algorate.trader.telegram

import cats.Monad
import cats.effect.kernel.Async
import cats.implicits._
import com.github.ppotseluev.algorate.Ticker
import com.github.ppotseluev.algorate.trader.Request
import com.github.ppotseluev.algorate.trader.RequestHandler
import io.circe.Codec
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.ConfiguredJsonCodec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

object TelegramWebhook {
  implicit private val circeConfig: Configuration = Configuration.default.withSnakeCaseMemberNames

  @ConfiguredJsonCodec
  case class Chat(id: Int)

  object Chat {
    implicit val codec: Codec[Chat] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class User(id: UserId, firstName: String, lastName: Option[String], username: Option[String])

  object User {
    implicit val codec: Codec[User] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class Message(messageId: Int, from: Option[User], chat: Chat, text: Option[String])

  object Message {
    implicit val codec: Codec[Message] = deriveCodec
  }

  @ConfiguredJsonCodec
  case class Update(updateId: Int, message: Option[Message])

  object Update {
    implicit val codec: Codec[Update] = deriveCodec
  }

  private val baseEndpoint = endpoint

  type Error = String

  val webhookEndpointDef: Endpoint[WebhookSecret, Update, Error, Unit, Any] =
    baseEndpoint
      .in("telegram")
      .post
      .in(jsonBody[Update])
      .errorOut(stringBody)
      .securityIn(auth.apiKey(header[WebhookSecret]("X-Telegram-Bot-Api-Secret-Token")))

  sealed trait Command {
    def toRequest: Request
  }

  object Command {
    case class ShowState(ticker: Ticker) extends Command {
      override def toRequest: Request = Request.ShowState(ticker)
    }

    private val show = "show ([0-9a-zA-Z]+)".r

    def parse(input: String): Option[Command] = input match {
      case show(ticker) => ShowState(ticker).some
      case _            => None
    }
  }

  class Handler[F[_]: Monad](
      allowedUsers: Set[UserId],
      trackedChats: Set[String],
      requestHandler: RequestHandler[F]
  ) {
    private val success = ().asRight[Error].pure[F]
    private def skip = success

    def handleTelegramEvent(
        update: Update
    ): F[Either[Error, Unit]] =
      update.message match {
        case Some(Message(_, Some(user), chat, Some(text))) =>
          val shouldReact =
            allowedUsers.contains(user.id) &&
              trackedChats.contains(chat.id.toString)
          if (shouldReact) {
            Command.parse(text).fold(skip) { cmd =>
              requestHandler.handle(cmd.toRequest).map(_.asRight)
            }
          } else {
            skip
          }
        case _ => skip
      }

  }

  def webhookEndpoint[F[_]: Async](
      handler: Handler[F],
      webhookSecret: WebhookSecret
  ) =
    webhookEndpointDef
      .serverSecurityLogicPure { secret =>
        if (secret == webhookSecret) ().asRight
        else "Error".asLeft
      }
      .serverLogic(_ => handler.handleTelegramEvent)
}
