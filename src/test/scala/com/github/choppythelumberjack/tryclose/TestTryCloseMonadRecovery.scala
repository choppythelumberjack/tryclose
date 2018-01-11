package com.github.choppythelumberjack.tryclose

import java.io.IOException

class TestTryCloseMonadRecovery extends TryCloseMonadSpec {

  // TODO Do we need a CanClose for TryClose in case TryCloses are nested in one another? Do we need a test for that?

  // simple one level recovery case
  // case with map?
  // case with regular recovery (i.e. not RecoverWith)

  "Simple Cases Recover" - {
    "Should recover from exception" in {
      val output =
        TryClose(somethingThatThrowsException("Throwing"))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner")
        ))
    }

    "Should recover from mapped exception" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .map(dc => somethingThatThrowsException("Throwing"))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from mapped exception - Fail Afterward" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .map(dc => somethingThatThrowsException("Throwing"))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))
          .flatMap(c => TryClose(somethingThatThrowsException("SecondThrowing")))

      output.resolve should equal(Failure(new IOException("Closing Method SecondThrowing")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          GoingToThrow("SecondThrowing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from flat-mapped exception" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(dc => TryClose(somethingThatThrowsException("Throwing")))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from flat-mapped exception - Fail Afterward" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(dc => TryClose(somethingThatThrowsException("Throwing")))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))
          .flatMap(c => TryClose(somethingThatThrowsException("SecondThrowing")))

      output.resolve should equal(Failure(new IOException("Closing Method SecondThrowing")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          GoingToThrow("SecondThrowing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from exception in first clause" in {
      // NOTE: When debugging this the test may fail with a linage
      // that repeats [Open[Inner], Close[Inner], CloseException[Inner]] twice because
      // a debugger breakpoint may run the continuation twice hence double recording these events
      // into the linage
      val output =
        TryClose(somethingThatThrowsException("Throwing"))
          .flatMap(dc => TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer")))
          .recoverWithHandler({case e:IOException => DummyCloseable("Inner", Throws)}, closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner")
        ))
    }
  }

  "Simple Cases RecoverWith" - {
    "Should recover from exception" in {
      val output =
        TryClose(somethingThatThrowsException("Throwing"))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner")
        ))
    }

    "Should recover from mapped exception" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .map(dc => somethingThatThrowsException("Throwing"))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from mapped exception - Fail Afterward" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .map(dc => somethingThatThrowsException("Throwing"))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})
          .flatMap(c => TryClose(somethingThatThrowsException("SecondThrowing")))

      output.resolve should equal(Failure(new IOException("Closing Method SecondThrowing")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          GoingToThrow("SecondThrowing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from flat-mapped exception" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(dc => TryClose(somethingThatThrowsException("Throwing")))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from flat-mapped exception - Fail Afterward" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(dc => TryClose(somethingThatThrowsException("Throwing")))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})
          .flatMap(c => TryClose(somethingThatThrowsException("SecondThrowing")))

      output.resolve should equal(Failure(new IOException("Closing Method SecondThrowing")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("Throwing"),
          Open("Inner"),
          GoingToThrow("SecondThrowing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Should recover from exception in first clause" in {
      // NOTE: When debugging this the test may fail with a linage
      // that repeats [Open[Inner], Close[Inner], CloseException[Inner]] twice because
      // a debugger breakpoint may run the continuation twice hence double recording these events
      // into the linage
      val output =
        TryClose(somethingThatThrowsException("Throwing"))
          .flatMap(dc => TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer")))
          .recoverWith({case e:IOException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))})

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          GoingToThrow("Throwing"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner")
        ))
    }
  }

  "Three Level Cases" - {
    "Three Level Recorvery success should propagate to the third nesting" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          case i: IOException => TryClose(DummyCloseable("Recovery", Throws), closeHandler("Recovery"))
        })
        mc3 <- TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (mc3)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Recovery"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Recovery"),
          CloseException("Recovery"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Three Level Recorvery success should propagate to the third nesting (some don't throw)" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          case i: IOException => TryClose(DummyCloseable("Recovery", NotThrows), closeHandler("Recovery"))
        })
        mc3 <- TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (mc3)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Recovery"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Recovery"),
          Close("Outer")
        ))
    }
  }

  "Two/Three Level Cases with Non/Recovery" - {
    "No Recovery Needed" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        mc2 <- TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
          .recoverWith({
            case i: IOException => TryClose(DummyCloseable("Alternative", Throws), closeHandler("Alternative"))
          })
      } yield (mc2)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"))
      )
    }

    "No Recovery Needed - Should run third nesting layer" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        mc2 <- TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
          .recoverWith({
            case i: IOException => TryClose(DummyCloseable("Alternative", Throws), closeHandler("Alternative"))
          })
        mc3 <- TryClose(DummyCloseable("Core", Throws), closeHandler("Core"))
      } yield (mc3)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Core", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          Open("Inner"),
          Open("Core"),
          Close("Core"),
          CloseException("Core"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"))
      )
    }

    "Recovery throws Exception" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          // Should not catch this exception
          case i: IOException => TryClose(somethingThatThrowsException("ThrowSomethingElse"), closeHandler("Inner"))
        })
      } yield (mc2)

      output.resolve should equal(Failure(new IOException("Closing Method ThrowSomethingElse")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          GoingToThrow("ThrowSomethingElse"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Recovery throws Exception - Should not run third nesting layer" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          // Should not catch this exception
          case i: IOException => TryClose(somethingThatThrowsException("ThrowSomethingElse"), closeHandler("Inner"))
        })
        mc3 <- TryClose(DummyCloseable("Inner", Throws))
      } yield (mc3)

      output.resolve should equal(Failure(new IOException("Closing Method ThrowSomethingElse")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          GoingToThrow("ThrowSomethingElse"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }


    "Recovery Does Not Catch Exception" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          // Should not catch this exception
          case i: IllegalArgumentException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
        })
      } yield (mc2)

      output.resolve should equal(Failure(new IOException("Closing Method ThrowSomething")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }

    "Recovery Does Not Catch Exception - Should not run third nesting layer" in {
      val output = for {
        mc1 <- TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
          // Should not catch this exception
          case i: IllegalArgumentException => TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
        })
        mc3 <- TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (mc3)

      output.resolve should equal(Failure(new IOException("Closing Method ThrowSomething")))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }
  }

  "Two Level Success Cases" - {
    def twoLevelRecoveryCase(
      innerThrowingBehavior: ThrowingBehavior,
      outerThrowingBehavior: ThrowingBehavior
    ) = for {
      mc1 <- TryClose(DummyCloseable("Outer", outerThrowingBehavior), closeHandler("Outer"))
      mc2 <- TryClose(somethingThatThrowsException("ThrowSomething")).recoverWith({
        case i: IOException => TryClose(DummyCloseable("Inner", innerThrowingBehavior), closeHandler("Inner"))
      })
    } yield (mc2)

    "Recovery - Inner Throws, Outer Does Not" in {
      val output = twoLevelRecoveryCase(Throws, NotThrows)
      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"))
      )
    }

    "Recovery - Outer Throws, Inner Does Not" in {
      val output = twoLevelRecoveryCase(NotThrows, Throws)
      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", NotThrows)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Inner"),
          Close("Inner"),
          Close("Outer"),
          CloseException("Outer"))
      )
    }

    "Recovery - Both Throw" in {
      val output = twoLevelRecoveryCase(Throws, Throws)
      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Inner"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer"))
      )
    }

    "Recovery - Neither Throw" in {
      val output = twoLevelRecoveryCase(NotThrows, NotThrows)
      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", NotThrows)))
      lineage should equal(
        List(
          Open("Outer"),
          GoingToThrow("ThrowSomething"),
          Open("Inner"),
          Close("Inner"),
          Close("Outer"))
      )
    }
  }
}
