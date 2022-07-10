package com.github.ppotseluev.algorate.util.redis

import java.nio.ByteBuffer

trait RedisEncoder[T] {
  def encode(obj: T): ByteBuffer
}
