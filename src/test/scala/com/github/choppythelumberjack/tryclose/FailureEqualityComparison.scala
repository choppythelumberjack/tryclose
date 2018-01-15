package com.github.choppythelumberjack.tryclose

import org.scalactic.Equality

trait FailureEqualityComparison {
  implicit def failureEquality[T]:Equality[TryCloseResult[T]] = new Equality[TryCloseResult[T]] {
    override def areEqual(a: TryCloseResult[T], b: Any): Boolean =
      (a, b) match {
        case (Failure(fa), Failure(fb)) => fa.getClass == fb.getClass && fa.getMessage == fb.getMessage
        case other @ _ => Equality.default.areEqual(a, b)
      }
  }

  implicit def throwableEquality[T <: Throwable]:Equality[T] = new Equality[T] {
    override def areEqual(fa: T, fb: Any): Boolean = {
      fa.getClass == fb.getClass && fa.getMessage == fb.asInstanceOf[Throwable].getMessage
    }
  }
}
