package com.github.ppotseluev.algorate.trader

case class HttpCodeException(code: Int, message: String)
    extends RuntimeException(s"Bad status code: $code, $message")
