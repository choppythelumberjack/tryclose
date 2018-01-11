package org.choppy.tryclose

import java.io.IOException

import org.choppy.tryclose

// General questions
// - What is the user expectation for the behavior to occour on a 'map' method? Should a functor case close the inner closeable? The outer closeable?
// - What is the expected user behavior, should the exception be automatically closed at the end of TryClose call (i.e. in 'finally') or should it only be closed
//   only in the cases that an exception has been thrown? Furthermore, if there is a 'map' at the end of the for-comprehension, there will be no close that is done
//   what is the user expectation here? If users don't want these things to be closed? How can they be extracted out of the monad so they can be closed later?
//   -- More to this point, you can't do a TryClose.getAndClose in which all the previous Try-Close clauses have been encapsulated since we've already removed
//      that data by that point and we have lost the pointers to them. However, if we had a free monad, would then entire chain still be available?
//      would this call actually be possible?
// - Generally speaking, the idea is that the last thing in the chain should *not* actually be a closeable but rather a object that represents the results
//   of the computation (e.g. a list). There's currently no way in the API to specify that. Should there be?
// - This thing cannot really be composed as a temporary result unless the connection is closed (i.e. since closure happens at the end of a flatmap)
//   is it possible to get around this issue with free monads?
// - Still want to do exception appending

class TestTryCloseMonad extends TryCloseMonadSpec {

  "Using Map" - {
    "should close both outer layers if two outer layers fail" in {
      val output =
        TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", Throws), closeHandler("Inner"))
          .flatMap(secondClosing => TryClose(DummyCloseable("Core", NotThrows)))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Core", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Open("Core"),
        Close("Core"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should close both layers if first fails to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", NotThrows), closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
    "should close both layers if second fails to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", Throws), closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer")
      ))
    }
    "should close both layers if both fail to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", Throws), closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should not process to second statement if first throws exception" in {
      val output =
        tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", NotThrows), closeHandler("Inner"))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "should not process to second statement if first throws exception - ignore second, even it it throws" in {
      val output =
        tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
          .mapWithHandler(firstClosing => DummyCloseable("Inner", Throws), closeHandler("Inner"))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "process to second statement if second throws exception" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .mapWithHandler(firstClosing => somethingThatThrowsException("Inner"), closeHandler("Inner"))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer")
      ))
    }

    "process to second statement if second throws exception - with first throwing exception on close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .mapWithHandler(firstClosing => somethingThatThrowsException("Inner"), closeHandler("Inner"))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
  }


  "Using FlatMap" - {
    "should close both outer layers if two outer layers fail" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")))
          .flatMap(secondClosing => TryClose(DummyCloseable("Core", NotThrows)))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Core", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Open("Core"),
        Close("Core"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should close both layers if first fails to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", NotThrows), closeHandler("Inner")))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
    "should close both layers if second fails to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer")
      ))
    }
    "should close both layers if both fail to close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should not process to second statement if first throws exception" in {
      val output =
        tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", NotThrows), closeHandler("Inner")))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "should not process to second statement if first throws exception - ignore second, even it it throws" in {
      val output =
        tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner")))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "process to second statement if second throws exception" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(somethingThatThrowsException("Inner"), closeHandler("Inner")))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer")
      ))
    }

    "process to second statement if second throws exception - with first throwing exception on close" in {
      val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(somethingThatThrowsException("Inner"), closeHandler("Inner")))

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
  }


  "Using For Comprehension" - {
    "should close both outer layers if two outer layers fail" in {
      val output = for {
        firstClosing  <- tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        secondClosing <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
        ye <- TryClose(DummyCloseable("Core", NotThrows))
      } yield (ye)


      output.resolve should equal(Success(DummyCloseable.OffRecord("Core", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Open("Core"),
        Close("Core"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should close both layers if first fails to close" in {
      val output = for {
        firstClosing <- tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        ye <- tryclose.TryClose(DummyCloseable("Inner", NotThrows), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
    "should close both layers if second fails to close" in {
      val output = for {
        firstClosing <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        ye <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer")
      ))
    }
    "should close both layers if both fail to close" in {
      val output = for {
        firstClosing <- tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        ye <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal(Success(DummyCloseable.OffRecord("Inner", Throws)))
      lineage should equal(List(
        Open("Outer"),
        Open("Inner"),
        Close("Inner"),
        CloseException("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }

    "should not process to second statement if first throws exception" in {
      val output = for {
        firstClosing <- tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
        ye <- tryclose.TryClose(DummyCloseable("Inner", NotThrows), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "should not process to second statement if first throws exception - ignore second, even it it throws" in {
      val output = for {
        firstClosing <- tryclose.TryClose(somethingThatThrowsException("Outer"), closeHandler("Outer"))
        ye <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Outer")))
      lineage should equal(List(
        GoingToThrow("Outer")
      ))
    }

    "process to second statement if second throws exception" in {
      val output = for {
        firstClosing <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        ye <- tryclose.TryClose(somethingThatThrowsException("Inner"), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer")
      ))
    }

    "process to second statement if second throws exception - with first throwing exception on close" in {
      val output = for {
        firstClosing <- tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        ye <- tryclose.TryClose(somethingThatThrowsException("Inner"), closeHandler("Inner"))
      } yield (ye)

      output.resolve should equal (Failure[DummyCloseable](new IOException(s"Closing Method Inner")))
      lineage should equal(List(
        Open("Outer"),
        GoingToThrow("Inner"),
        Close("Outer"),
        CloseException("Outer")
      ))
    }
  }


  "When transformed to self" - {
    "with map - should process transformation but not wrap with same closeable twice" in {

      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .mapWithHandler(firstClosing => firstClosing, closeHandler("Inner"))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Outer", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Close("Outer")
      ))
    }

    // It's impossible to know that the second same member is being passed to multiple TryClose
    // monads
    "with flatMap - can only process transformation to self twice since can't do static analysis with monads" in {

      val output =
        tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
          .flatMap(firstClosing => tryclose.TryClose(firstClosing, closeHandler("Inner")))

      output.resolve should equal(Success(DummyCloseable.OffRecord("Outer", NotThrows)))
      lineage should equal(List(
        Open("Outer"),
        Close("Outer"),
        Close("Outer")
      ))
    }
  }

  "Using For Comprehensions - Try Monad should Handle Two Layers of Closeables and Exception" - {

    "Where both inner and outer layer closes fail" in {

      val output = for {
        mc1 <- tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
        mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
        res <- tryclose.TryClose(somethingThatThrowsException("Throwing"), closeHandler("Core")) //, wrapCloseFailure = Throws
      } yield (res)

      output.resolve should equal (Failure(new IOException("Closing Method Throwing")))
      lineage should equal(
          List(Open("Outer"),
          Open("Inner"),
          GoingToThrow("Throwing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer"))
      )
    }

    "Where only inner layer fails" in {

      val output = for {
        mc1 <- tryclose.TryClose(DummyCloseable("Outer", NotThrows), closeHandler("Outer"))
        mc2 <- tryclose.TryClose(DummyCloseable("Inner", Throws), closeHandler("Inner"))
        res <- tryclose.TryClose(somethingThatThrowsException("Throwing"), closeHandler("Core")) //, wrapCloseFailure = true
      } yield (res)

      output.resolve should equal (Failure(new IOException("Closing Method Throwing")))
      lineage should equal(
        List(Open("Outer"),
          Open("Inner"),
          GoingToThrow("Throwing"),
          Close("Inner"),
          CloseException("Inner"),
          Close("Outer"))
      )
    }
  }

  /**
    * In the situation where there are multiple statements inside of a TryClose
    * (e.g. in this 'mapped' instance), if later statements throw an exception
    * before former ones can properly return a value to the parent
    * (i.e. somethingThatThrowsException) is thrown before DummyCloseable
    * can be returned to the parent TryClose in order for the session to be closed
    * there is no way for TryClose to close the affected object.
    * Be sure to separate statements that open connections to statements
    * that throw other kinds of exceptions otherwise the former will not be closed.
    */
  "Testing Other Behaviors" - {
    "Shuold Create/Close Properly when multiple statements used" in {
        val output =
        tryclose.TryClose(DummyCloseable("Outer", Throws), closeHandler("Outer"))
          .mapWithHandler(dc => {
            DummyCloseable("Inner", Throws)
            somethingThatThrowsException("Throwing")
          }, closeHandler("Inner"))

      output.resolve should equal(Failure(new IOException("Closing Method Throwing")))
      lineage should equal(
        List(
          Open("Outer"),
          Open("Inner"),
          GoingToThrow("Throwing"),
          //Close("Inner"),
          //CloseException("Inner"),
          Close("Outer"),
          CloseException("Outer")
        ))
    }
  }
}
