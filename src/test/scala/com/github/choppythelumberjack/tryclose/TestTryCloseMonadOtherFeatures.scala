package com.github.choppythelumberjack.tryclose

import java.io.IOException

class TestTryCloseMonadOtherFeatures extends TryCloseMonadSpec {

  "flatten tests" - {
    "flatten simple" in {
      val output =
        TryClose(
          TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer")),
          closeHandler("OutsideScope")).flatten

      output.resolve should equal(Success(DummyCloseable("Outer", Throws)(fakeLinageRecorder)))
      lineage should equal(List(
        Open("Outer"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "flatten exception" in {
      val output =
        TryClose(
          TryClose(somethingThatThrowsException("OuterException"), closeHandler("Outer")),
          closeHandler("OutsideScope")).flatten

      output.resolve should equal(Failure(new IOException("Closing Method OuterException")))
      lineage should equal(List(
        GoingToThrow("OuterException")
      ))
    }

    "flatten exception inside" in {
      val output =
        TryClose(
          {
            somethingThatThrowsException("OuterException")
            TryClose(DummyCloseable("Internal"), closeHandler("Internal"))
          },
          closeHandler("OutsideScope")).flatten

      output.resolve should equal(Failure(new IOException("Closing Method OuterException")))
      lineage should equal(List(
        GoingToThrow("OuterException")
      ))
    }
  }

  "transform tests" - {
    "test transform simple - success case" in {
      val output = TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .transform(
            tc => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")),
            ex => TryClose(DummyCloseable("InnerRecovery", Throws), closeHandler("InnerRecovery"))
          )

      output.resolve should equal(Success(DummyCloseable("Inner", Throws)(fakeLinageRecorder)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "test transform simple - failure case" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        .flatMap(tc =>
          TryClose(somethingThatThrowsException("ExceptionThrown"), closeHandler("ExceptionCloseHandler"))
          .transform(
            tc => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")),
            ex => TryClose(DummyCloseable("InnerRecovery", Throws), closeHandler("InnerRecovery"))
          )
        )

      output.resolve should equal(Success(DummyCloseable("InnerRecovery", Throws)(fakeLinageRecorder)))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("ExceptionThrown"),
        Open("InnerRecovery"),
        Close("InnerRecovery"),
        CloseException("InnerRecovery"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "test transform simple - failure case - not nested" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(tc => TryClose(somethingThatThrowsException("ExceptionThrown"), closeHandler("ExceptionCloseHandler")))
          .transform(
            tc => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")),
            ex => TryClose(DummyCloseable("InnerRecovery", Throws), closeHandler("InnerRecovery"))
          )

      output.resolve should equal(Success(DummyCloseable("InnerRecovery", Throws)(fakeLinageRecorder)))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("ExceptionThrown"),
        Open("InnerRecovery"),
        Close("InnerRecovery"),
        CloseException("InnerRecovery"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
  }

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
