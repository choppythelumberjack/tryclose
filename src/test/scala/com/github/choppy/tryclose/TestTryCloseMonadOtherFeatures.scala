package com.github.choppy.tryclose

import com.github.choppy
import com.github.choppy.tryclose.TryClose.{FakeCloseable, LambdaCloseable}

class TestTryCloseMonadOtherFeatures extends TryCloseMonadSpec {
  "using lambda to close" in {
    case class CustomCloseable(name:String)(implicit lineageRecorder: LineageRecorder) {
      lineageRecorder.lineage += Open(name)
      def closeMe:Unit = lineageRecorder.lineage += Close(name)
    }
    implicit val lambdaCloseable = new LambdaCloseable[CustomCloseable](_.closeMe)

    val output = for {
      firstClosing  <- choppy.tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      lambdaClosing <- TryClose(new CustomCloseable("Custom"))
    } yield (lambdaClosing)


    output.resolve should equal(Success(CustomCloseable("Custom")(fakeLinageRecorder)))
    lineage should equal(List(
      Open("Outer"),
      Open("Custom"),
      Close("Custom"),
      Close("Outer")
    ))
  }

  "using fake closeable" in {
    case class CustomCloseable(name:String)(implicit lineageRecorder: LineageRecorder) {
      lineageRecorder.lineage += Misc("ConstructFakeCloseable")
    }
    implicit val lambdaCloseable = new FakeCloseable[CustomCloseable]

    val output = for {
      firstClosing  <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      lambdaClosing <- TryClose(new CustomCloseable("Custom"))
    } yield (lambdaClosing)


    output.resolve should equal(Success(CustomCloseable("Custom")(fakeLinageRecorder)))
    lineage should equal(List(
      Open("Outer"),
      Misc("ConstructFakeCloseable"),
      Close("Outer")
    ))
  }
}
