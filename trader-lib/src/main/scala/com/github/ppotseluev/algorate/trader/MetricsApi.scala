//package com.github.ppotseluev.algorate.trader
//
//import cats.Applicative
//import cats.implicits._
//import io.prometheus.client.CollectorRegistry
//import sttp.monad.MonadError
//import sttp.tapir._
//import sttp.tapir.server.ServerEndpoint
//import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
//import sttp.tapir.server.metrics.prometheus.PrometheusMetrics._
//
//object MetricsApi {
//
//  implicit class PrometheusMetricsOps(val metrics: PrometheusMetrics[F])
//      extends AnyVal {
//
//    def secureMetricsEndpoint(token: String): ServerEndpoint[Any, F] = {
//      val endp = endpoint.get
//        .in(metrics.endpointPrefix)
//        .out(plainBody[CollectorRegistry])
//        .securityIn(auth.apiKey(header[String]("X-Authorization-Token")))
//      ServerEndpoint(
//        endpoint = endp,
//        securityLogic = _ => {
//          receivedToken: String =>
//            val result = if (token == receivedToken) ().asRight else ().asLeft
//            result.pure[F]
//        },
//        logic = (monad: MonadError[F]) =>
//          (_: Unit) =>
//            (_: Unit) => monad.eval(Right(metrics.registry): Either[Unit, CollectorRegistry])
//      )
//    }
//  }
//}
