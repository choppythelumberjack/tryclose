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

  implicit class CloseableThrowable[T <: Throwable](t: T) extends CanClose[T] {
    override def close(t: T): Unit = Unit
  }

  implicit object CloseableAutoCloseable extends CanClose[AutoCloseable] {
    override def close(t: AutoCloseable): Unit = t.close()
  }

  implicit object CloseableUnit extends CanClose[Unit] {
    override def close(t: Unit): Unit = Unit
  }

  object StructuralImplicit {
    implicit class StructuralCloseable[T <: {def close():Unit}](structuralCloseable:T) extends CanClose[T] {
      override def close(t: T): Unit = structuralCloseable.close()
    }
  }
}
