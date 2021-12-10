package com.github.ppotseluev.algorate.util

import scala.collection.mutable

class LimitedQueue[A](maxSize: Int) extends mutable.Queue[A] {
  override def addOne(elem: A): this.type = {
    if (length >= maxSize) {
      dequeue()
    }
    super.addOne(elem)
    this
  }
}
