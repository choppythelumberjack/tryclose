package com.github.choppy.tryclose

import com.github.choppy.tryclose.ImplicitCloseables._

class TestTryCloseMonadOtherFeatures extends TryCloseMonadSpec {

  "using lambda to close" in {
    case class CustomCloseable(name:String)(implicit lineageRecorder: LineageRecorder) {
      lineageRecorder.lineage += Open(name)
      def closeMe:Unit = {
        lineageRecorder.lineage += Close(name)
      }
    }

    val output = for {
      firstClosing <- TryClose(DummyCloseable.apply("Outer", NotThrows), closeHandler("Outer"))
      lambdaClosing <- TryClose.wrapWithCloser(new CustomCloseable("Custom"), closeHandler("Custom"))(_.closeMe)
    } yield (lambdaClosing)

    output.resolve.map(_.get) should equal(Success(CustomCloseable("Custom")(fakeLinageRecorder)))
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

    val output = for {
      firstClosing  <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      lambdaClosing <- TryClose.wrap(new CustomCloseable("Custom"))
    } yield (lambdaClosing)


    output.resolve.map(_.get) should equal(Success(CustomCloseable("Custom")(fakeLinageRecorder)))
    lineage should equal(List(
      Open("Outer"),
      Misc("ConstructFakeCloseable"),
      Close("Outer")
    ))
  }
}
