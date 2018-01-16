package com.github.choppythelumberjack.tryclose.examples

import com.github.choppythelumberjack.tryclose.FailureEqualityComparison
import org.h2.jdbcx.JdbcDataSource
import org.scalatest.{FreeSpec, Matchers}

class TestTryCloseExamples extends FreeSpec with Matchers with FailureEqualityComparison {

  def makeDatasource = {
    val datasoure = new JdbcDataSource()
    datasoure.setURL("jdbc:h2:mem:sample;INIT=RUNSCRIPT FROM 'src/test/resources/schema.sql'")
    datasoure.setUser("sa")
    datasoure.setPassword("sa")
    datasoure
  }

  "introductory example" in {
    import com.github.choppythelumberjack.tryclose.JavaImplicits._
    import com.github.choppythelumberjack.tryclose._

    val ds = makeDatasource
    val output = for {
      conn  <- TryClose(ds.getConnection())
      ps    <- TryClose(conn.prepareStatement("select age from Person where lastName='Bloggs'"))
      rs    <- TryClose(ps.executeQuery())
    } yield wrap {rs.next(); rs.getInt(1)}

    // Note that Nothing will actually be done until 'resolve' is called
    output.resolve.map(_.get) should be (Success(22))

    // You can use a shorthald with wrapped results
    output.resolve.unwrap should be (Success(22))

    // or even shorter
    output.unwrap should be (Success(22))
  }

  "composition example" in {
    import com.github.choppythelumberjack.tryclose.JavaImplicits._
    import com.github.choppythelumberjack.tryclose._

    // Create the Data Source and Open a JDBC Connection
    def createConnectionAndStatement = {
      val ds = makeDatasource

      for {
        conn  <- TryClose(ds.getConnection())
        stmt  <- TryClose(conn.prepareStatement("select age from Person where lastName='Bloggs'"))
      } yield (stmt)
    }

    // Now compose the previous with further statements
    val output = for {
      ps <- createConnectionAndStatement
      rs <- TryClose(ps.executeQuery())
    } yield wrap {rs.next(); rs.getInt(1)}

    // Since nothing is done until output.resolve is called, you can continue
    // nesting TryClose statements and re-use createConnection(url) as many
    // times as needed.
    output.resolve.unwrap should be (Success(22))
  }

  "custom closeable CanClose example" in {
    import com.github.choppythelumberjack.tryclose._

    // Assuming you have some kind of custom object with a closing method that needs to be called
    // as cleanup after some operation that could throw and exception.
    class MyCustomCloseable(url:String) {
      if (url.startsWith("foo")) throw new IllegalArgumentException("Cannot start with 'foo'")

      def closeMe():Unit = {/*...*/}
      def getData:List[Int] = {List(1,2,3)}
    }

    // You can use an implicit to prove MyCustomCloseable is a CanClose
    implicit object MyCustomCloseableEvidence extends CanClose[MyCustomCloseable] {
      def close(closeable:MyCustomCloseable):Unit = closeable.closeMe()
    }

    // Then you can use the standard TryClose type-constructor with your custom object.
    def toTest(prefix:String) = {
      for {
        cc <- TryClose(new MyCustomCloseable(prefix))
      } yield (cc)
    }

    // Note that this will return a TryCloseResult[LambdaWrapped[T]], you can extract
    // your item (the List[Int] in this case) via the Wrapped.get command.

    toTest("bar").resolve match {
      case Success(value) => value.getData should be (List(1,2,3))
      case _ => fail
    }

    toTest("foo").resolve match {
      case Failure(e) => e should equal (new IllegalArgumentException("Cannot start with 'foo'"))
      case _ => fail
    }

    toTest("bar").resolve.map(_.getData) should be (Success(List(1,2,3)))
    toTest("foo").resolve.map(_.getData) should equal (Failure(new IllegalArgumentException("Cannot start with 'foo'")))
  }


  "custom closeable wrapWithClose example" in {
    import com.github.choppythelumberjack.tryclose._

    // Assuming you have some kind of custom object with a closing method that needs to be called
    // as cleanup after some operation that could throw and exception.
    class MyCustomCloseable(url:String) {
      if (url.startsWith("foo")) throw new IllegalArgumentException("Cannot start with 'foo'")

      def closeMe():Unit = {/*...*/}
      def getData:List[Int] = {List(1,2,3)}
    }

    // Then you can use the standard TryClose type-constructor with your custom object.
    def toTest(prefix:String) = {
      for {
        cc <- TryClose.wrapWithCloser(new MyCustomCloseable(prefix))(_.closeMe())
      } yield (cc)
    }

    // Note that this will return a TryCloseResult[LambdaWrapped[T]], you can extract
    // your item (the List[Int] in this case) via the Wrapped.get command.

    toTest("bar").resolve match {
      case Success(value) => value.get.getData should be (List(1,2,3))
      case _ => fail
    }

    toTest("foo").resolve match {
      case Failure(e) => e should equal (new IllegalArgumentException("Cannot start with 'foo'"))
      case _ => fail
    }

    toTest("bar").resolve.map(_.get.getData) should be (Success(List(1,2,3)))
    toTest("foo").resolve.map(_.get.getData) should equal (Failure(new IllegalArgumentException("Cannot start with 'foo'")))
  }
}
