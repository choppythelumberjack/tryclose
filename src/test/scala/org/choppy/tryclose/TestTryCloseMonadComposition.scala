package org.choppy.tryclose

import java.io.IOException

import org.choppy.tryclose

class TestTryCloseMonadComposition extends TryCloseMonadSpec {
  "Compose For Comprehensions" in {
    val firstOutput = for {
      mc1 <- TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
    } yield (mc2)

    val output = for {
      mc1 <- firstOutput
      mc2 <- tryclose.TryClose(DummyCloseable("Core", Throws), closeHandler("Core"))
    } yield (mc2)

    output.resolve should equal (Success(DummyCloseable.OffRecord("Core", Throws)))
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

  "Compose For Comprehensions With Throw" in {
    val firstOutput = for {
      mc1 <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
    } yield (mc2)

    val output = for {
      mc1 <- firstOutput
      mc2 <- tryclose.TryClose(DummyCloseable("Core", Throws), closeHandler("Core"))
      mc3 <- TryClose(somethingThatThrowsException("ThrowSomething"))
    } yield (mc2)

    output.resolve should equal (Failure(new IOException("Closing Method ThrowSomething")))
    lineage should equal(
      List(
        Open("Outer"),
        Open("Inner"),
        Open("Core"),
        GoingToThrow("ThrowSomething"),
        Close("Core"),
        CloseException("Core"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"))
    )
  }

  "Compose For Comprehensions - With Filter Succeed" in {
    val firstOutput = for {
      mc1 <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
    } yield (mc2)

    val output = for {
      mc1 <- firstOutput.filter(_.label == "Inner")
      mc2 <- tryclose.TryClose(DummyCloseable("Core", Throws), closeHandler("Core"))
    } yield (mc2)

    output.resolve should equal (Success(DummyCloseable.OffRecord("Core", Throws)))
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


  "Compose For Comprehensions - With Filter Fail" in {
    val firstOutput = for {
      mc1 <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
      mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
    } yield (mc2)

    val output = for {
      mc1 <- firstOutput.filter(_.label == "Blah")
      mc2 <- tryclose.TryClose(DummyCloseable("Core", Throws), closeHandler("Core"))
    } yield (mc2)

    output.resolve should equal (Failure(new java.util.NoSuchElementException("Predicate does not hold for DummyCloseable(Inner,Throws)")))
    lineage should equal(
      List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"))
    )
  }
}
