package com.github.choppythelumberjack.tryclose

trait ImplicitCloseables {

  implicit def wrapperCloseable[T]: CanClose[Wrapper[T]] =
    new CanClose[Wrapper[T]] {
      override def close(closeable: Wrapper[T]): Unit = Unit
    }

  implicit def lambdaWrapperCloseable[T]: CanClose[LambdaWrapper[T]] =
    new CanClose[LambdaWrapper[T]] {
      override def close(closeable: LambdaWrapper[T]): Unit = closeable.close(closeable.get)
    }

  implicit class CloseableThrowableEvidence[T <: Throwable](t: T) extends CanClose[T] {
    override def close(t: T): Unit = Unit
  }

  implicit object CloseableTryCloseEvidence extends CanClose[TryClose[Any]] {
    override def close(t: TryClose[Any]): Unit = Unit
  }

  implicit object CloseableUnitEvidence extends CanClose[Unit] {
    override def close(t: Unit): Unit = Unit
  }

  object JavaImplicits {
    implicit object AutoCloseableEvidence extends CanClose[AutoCloseable] {
      override def close(t: AutoCloseable): Unit = t.close()
    }
  }

  object StructuralImplicits {
    implicit class StructuralCloseableEvidence[T <: {def close():Unit}](structuralCloseable:T) extends CanClose[T] {
      override def close(t: T): Unit = structuralCloseable.close()
    }
  }
}
