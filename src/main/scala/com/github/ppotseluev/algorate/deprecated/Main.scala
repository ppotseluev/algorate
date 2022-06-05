//package com.github.ppotseluev.algorate
//
//import cats.effect.unsafe.implicits._
//import cats.effect.{ExitCode, IO, IOApp}
//
//object Main extends IOApp {
//  def run(args: List[String]): IO[ExitCode] =
//    new Algorate[IO](args.head).run.as(ExitCode.Success)
//}
