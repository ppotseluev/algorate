package com.github.ppotseluev.algorate.util

trait BiConverter[A, B] {
  def applyA(a: A): B

  def applyB(b: B): A
}

object BiConverter {
  def apply[A, B](ab: A => B, ba: B => A): BiConverter[A, B] = new BiConverter[A, B] {
    override def applyA(a: A): B = ab(a)

    override def applyB(b: B): A = ba(b)
  }
}
