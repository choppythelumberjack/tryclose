package com.github.choppythelumberjack.tryclose

/**
  * These classes represent the final outcome from a `TryClose.resolve` invocation,
  * and as such, they don't close anything so they don't need evidence it's closeable.
  * The below classes mirror Scala's standard Try/Success/Failure objects in a limited
  * capacity because they represent result entities of the TryClose transformation
  * as opposed to intermediate products. For this reason, method such as `recover`,
  * and `recoverWith`, and `transform` are missing. All these afformentioned transformations
  * should have already been applied to `TryClose` before the `retrieve` was called.
  * The Monad, Functor and other operations available on these result type objects
  * should not actually be used to change their state from success to failure or failure
  * to success, they are merely for the sake of user convenience.
  */
trait TryCloseResult[+T] {
  def isFailure: Boolean
  def isSuccess: Boolean
  def get: T
  def foreach[U](f: T => U): Unit
  def flatMap[U](f: T => TryCloseResult[U]): TryCloseResult[U]
  def map[U](f: T => U): TryCloseResult[U]
  def toOption: Option[T] = if (isSuccess) Some(get) else None
  def flatten[U](implicit ev: T <:< TryCloseResult[U]): TryCloseResult[U]

  def asTry = scala.util.Try[T](get)
}

final case class Success[+T](value:T) extends TryCloseResult[T] {
  def isFailure: Boolean = false
  def isSuccess: Boolean = true
  def get = value
  def flatMap[U](f: T => TryCloseResult[U]): TryCloseResult[U] = f(value)
  def flatten[U](implicit ev: T <:< TryCloseResult[U]): TryCloseResult[U] = value
  def foreach[U](f: T => U): Unit = f(value)
  def map[U](f: T => U): TryCloseResult[U] = Success[U](f(value))

  def asSuccess = scala.util.Success(value)
}

final case class Failure[+T](exception: Throwable) extends TryCloseResult[T] {
  def isFailure: Boolean = true
  def isSuccess: Boolean = false
  def get: T = throw exception
  def flatMap[U](f: T => TryCloseResult[U]): TryCloseResult[U] = this.asInstanceOf[TryCloseResult[U]]
  def flatten[U](implicit ev: T <:< TryCloseResult[U]): TryCloseResult[U] = this.asInstanceOf[TryCloseResult[U]]
  def foreach[U](f: T => U): Unit = ()
  def map[U](f: T => U): TryCloseResult[U] = this.asInstanceOf[TryCloseResult[U]]

  def asFailure = scala.util.Failure(exception)
}

