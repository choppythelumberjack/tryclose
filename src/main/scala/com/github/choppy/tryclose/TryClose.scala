package com.github.choppy.tryclose

import java.io.IOException

import com.github.choppy.tryclose.TryClose.Implicits.CloseableUnit
import com.github.choppy.tryclose.TryClose.{CloseHandler, Continuation, IdentityContinuation}

import scala.util.control.NonFatal

trait CanClose[T] {
  @throws[IOException]
  def close(closeable: T): Unit
}

trait TryCloseImplicits {

  private[tryclose] def implicitlyCloseAndHandle[T](
    v: Option[T],
    closeHandler: CloseHandler
  )(implicit evidence: CanClose[T]): Unit =
  {
    val unitEvidence = CloseableUnit
    val output =
      v.map(
        closeable =>
          try {
            Success(implicitly[CanClose[T]](evidence).close(closeable))
          } catch {
            case NonFatal(e) => Failure(e)
          }
      )
    output match {
      case Some(fa @ Failure(_)) => closeHandler(fa)
      case _ =>
    }
  }
}

// Success an failure don't close anything so they don't need evidence it's closeable
trait TryCloseResult[+T]
final case class Success[+T](value:T) extends TryCloseResult[T] {
  def get = value
}
final case class Failure[+T](exception: Throwable) extends TryCloseResult[T]



abstract class TryClose[+T](implicit evidence:CanClose[T]) extends TryCloseImplicits {
  def handler:CloseHandler = tc=>Unit
  def resolve:TryCloseResult[T] = {
    retrieve(new IdentityContinuation)
  }
  def retrieve[U](continuation: Continuation[T, U]):TryCloseResult[U]

  // Now T=>TryClose[U] actually *can't* throw an exception since TryClose never
  // throws an exception. You can't throw an exception directly in this clause anyway,
  // if you do T=>TryClose(throw Exception) the exception will be caught.
  // Therefore all that is needed is retrieving the parent value and checking it,
  // depending on that we know which direction to go (i.e. whether it's a success and we
  // need to pass a continuation, or whether it's a failure and we just return
  // the Failure(exception) object).
  def flatMap[U](f: T => TryClose[U])(implicit innerEvidence:CanClose[U]): TryClose[U] = {
    val parent = this
    new TryClose[U] {
      override def retrieve[V](continuation: Continuation[U, V]): TryCloseResult[V] = {
        parent.retrieve({
          case Success(value) => f(value).retrieve(continuation)
          case fa @ Failure(_) => continuation(fa.asInstanceOf[TryCloseResult[U]])
        })
      }
    }
  }

  def map[U](f: T => U)(implicit evidence:CanClose[U]): TryClose[U] = mapWithHandler(f)(evidence)
  def mapWithHandler[U](f: T => U, closeHandler: CloseHandler = (tc=>Unit))(implicit evidence:CanClose[U]): TryClose[U] = {
    val parent = this
    new TryClose[U] {
      override def retrieve[V](continuation: Continuation[U, V]): TryCloseResult[V] = {
        parent.retrieve({
          case Success(value) => {
            val ret = f(value)
            // If the returned value is exactly the same one as the one created this means
            // we are mapping the same closeable to itself (literally map(myCloseable => myCloseable)
            // since we definitely *don't* want to close the same closeable multiple times, check if this
            // is the case and just invoke the continuation. Otherwise wrap the result into a new TryClose.
            // Note that this is impossible to do in the Monad/flatMap case (i.e. we can't double check if two TryClose
            // invocations are acting on the same variable since f(value) gives us another monad
            // as opposed to a value.
            if (ret == value) continuation(Success(ret))
            else TryClose.liftCloseable(() => ret, closeHandler).retrieve(continuation)
          }
          case fa @ Failure(_) => {
            continuation(fa.asInstanceOf[TryCloseResult[U]])
          }
        })
      }
    }
  }

  def filter(predicate: T => Boolean): TryClose[T] =
    map(t => if (predicate(t)) t else throw new NoSuchElementException("Predicate does not hold for " + t))

  /**
    * Applies the given function `f` if this fails when `resolve` happens. This is like `map` for the exception.
    */
  def recover[U >: T](rescueException: PartialFunction[Throwable, U])(implicit evidence: CanClose[U]): TryClose[U] =
    recoverWithHandler(rescueException)(evidence)

  /**
    * Applies the given function `f` if this fails when `resolve` happens. This is like `map` for the exception.
    * Additionally, it allows the user to pass a handler to be called if the closing throws and exception.
    */
  def recoverWithHandler[U >: T](rescueException: PartialFunction[Throwable, U], closeHandler: CloseHandler = (tc=>Unit))(implicit evidence: CanClose[U]): TryClose[U] =
    recoverWith(rescueException.andThen(u => TryClose(u, closeHandler)))(evidence)

  /**
    * Applies the given function `f` if this fails when `resolve` happens. This is like `flatMap` for the exception.
    */
  def recoverWith[U >: T](rescueException: PartialFunction[Throwable, TryClose[U]])(implicit evidence: CanClose[U]): TryClose[U] = {
    val parent = this
    new TryClose[U] {
      override def retrieve[W](continuation: Continuation[U, W]): TryCloseResult[W] = {
        parent.retrieve({
          case su @ Success(value) => {
            continuation(su)
          }
          case fa @ Failure(_) => {
            if (rescueException isDefinedAt fa.exception) {
              // should be able to continue from here (i.e. pass continuation onward into the success)
              rescueException(fa.exception).retrieve(continuation)
            } else {
              // in failure case can just case since we just need to return it anyway
              fa.asInstanceOf[TryCloseResult[W]]
            }
          }
        })
      }
    }
  }

  // TODO Transform
  // TODO filterWith
  // TODO Flatten
  // TODO Foreach
}






object TryClose {

  type CloseHandler = (TryCloseResult[Unit]) => Unit
  type Continuation[T, U] = (TryCloseResult[T]) => TryCloseResult[U]

  class IdentityContinuation[T] extends Continuation[T, T] {
    override def apply(s: TryCloseResult[T]): TryCloseResult[T] = s
  }

  object Implicits {
    object Structural {
      implicit class StructuralCloseable[T <: {def close():Unit}](structuralCloseable:T) extends CanClose[T] {
        override def close(t: T): Unit = structuralCloseable.close()
      }
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
  }

  class FakeCloseable[T] extends CanClose[T] {
    override def close(t: T): Unit = Unit
  }
  class LambdaCloseable[T](closer:T => Unit) extends CanClose[T] {
    override def close(t: T): Unit = closer(t)
  }

  private[tryclose] def liftCloseable[T](
    value: () => T,
    closeHandler: CloseHandler = (tc=>{println(s"Unit Close Handler ${tc}"); Unit}))(implicit evidence:CanClose[T]
  ) =
    new TryClose[T] {
      override def retrieve[U](continuation:Continuation[T, U]): TryCloseResult[U] = {
        var v:Option[T] = None
        val ret = try {
          v = Some(value())
          continuation(Success(v.get))
        } catch {
          case NonFatal(e) => {
            continuation(Failure(e))
          }
        } finally {
          implicitlyCloseAndHandle(v, handler)(evidence)
        }
        ret
      }
      override def handler: CloseHandler = closeHandler
    }

  def apply[T](value: => T, closeHandler: CloseHandler = (tc=>Unit))(implicit evidence:CanClose[T]) =
    TryClose.liftCloseable(() => value, closeHandler)(evidence)

}
