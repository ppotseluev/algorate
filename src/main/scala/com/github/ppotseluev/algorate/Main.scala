package com.github.ppotseluev.algorate

import cats.effect.{ExitCode, IO, IOApp}

object Main extends Algorate[IO] with IOApp {
  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- run(args.head)
    } yield ExitCode.Success
}
