package com.github.choppythelumberjack.tryclose

import java.io.{Closeable, IOException}

import com.github.choppythelumberjack.tryclose.TryClose.CloseHandler
import org.scalactic.Equality
import org.scalatest.{BeforeAndAfterEach, FreeSpec, Matchers}

import scala.collection.mutable

class TryCloseMonadSpec extends FreeSpec with Matchers with BeforeAndAfterEach {
  implicit def failureEquality[T]:Equality[TryCloseResult[T]] = new Equality[TryCloseResult[T]] {
    override def areEqual(a: TryCloseResult[T], b: Any): Boolean =
      (a, b) match {
        case (Failure(fa), Failure(fb)) => fa.getClass == fb.getClass && fa.getMessage == fb.getMessage
        case other @ _ => Equality.default.areEqual(a, b)
      }
  }

  sealed trait Marker
  case class Open(str:String) extends Marker
  case class Close(str:String) extends Marker
  case class GoingToThrow(str:String) extends Marker
  case class CloseException(str:String) extends Marker
  case class Misc(str:String) extends Marker

  implicit object DummyCloseableEvidence extends CanClose[DummyCloseable] {
    override def close(closeable: DummyCloseable): Unit = closeable.close()
  }

  case class LineageRecorder(lineage:mutable.ArrayBuffer[Marker])

  trait ThrowingBehavior
  case object Throws extends ThrowingBehavior
  case object NotThrows extends ThrowingBehavior

  case class DummyCloseable(
    val label:String,
    val closeThrowingBehavior: ThrowingBehavior = NotThrows
  )(implicit val lineageRecorder: LineageRecorder) extends Closeable {
    if (lineageRecorder == null)
      throw new IllegalArgumentException("Lineage Recorder Instance has not been initialized! BeforeEach is " +
        "probably not running. Has this been called from inside a test suite (i.e. using 'in')???")

    lineageRecorder.lineage += (Open(label))
    override def close(): Unit = {
      lineageRecorder.lineage += (Close(label))
      if (closeThrowingBehavior == Throws) throw new IOException(s"Cannot Close ${label}")
    }
  }

  object DummyCloseable {
    def OffRecord(label:String, closeThrowingBehavior: ThrowingBehavior = NotThrows) =
      new DummyCloseable(label, closeThrowingBehavior)(fakeLinageRecorder)
  }

  def somethingThatThrowsException(label:String)(implicit lineageRecorder: LineageRecorder):DummyCloseable = {
    lineageRecorder.lineage += (GoingToThrow(label))
    // right now both layers throw exceptions, this is another cast that must be tested
    throw new IOException(s"Closing Method ${label}")
  }

  def closeHandler(label:String)(implicit lineageRecorder:LineageRecorder):CloseHandler =
    tc => {lineageRecorder.lineage += CloseException(label); Unit}

  def fakeLinageRecorder = new LineageRecorder(new mutable.ArrayBuffer[Marker]())

  implicit var lineageRecorder:LineageRecorder = _
  def lineage = lineageRecorder.lineage

  override def beforeEach(): Unit = {
    lineageRecorder = new LineageRecorder(new mutable.ArrayBuffer[Marker]())
  }

  override def afterEach(): Unit = {
    println(lineage)
  }
}
