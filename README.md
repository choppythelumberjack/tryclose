# Choppy's Lazy TryClose Monad

The TryClose Monad is designed to be a lazy alternative to Scala's Try Monad as well as
to be Scala's answer to Java's try-with-resources construct. Where Java uses contrived 
language constructs to manage resources, Scala can answer with for-comprehensions and monadic power.
This library was envisioned to mostly use JDBC related resources (e.g. Connections, ResultSets etc...)
but other scenarios like managing streams are also good use cases.

Using TryClose we can manage database resources like this:
```scala
import com.github.choppythelumberjack.tryclose._
import com.github.choppythelumberjack.tryclose.JavaImplicits._
  
val ds = new JdbcDataSource()
val output = for {
  conn  <- TryClose(ds.getConnection())
  ps  <- TryClose(conn.prepareStatement("select * from MyTable"))
  rs    <- TryClose.wrap(ps.executeQuery())
} yield wrap { rs.next(); rs.getInt(1) }
    
// Note that Nothing will actually be done until 'resolve' is called
output.resolve match {
    case Success(result) => // Do something
    case Failure(e) =>      // Handle Stuff
}
```

The Java analogue using try-with-resources would look like this:
```java
DataSource ds = new JdbcDataSource();
try (
     Connection c = ds.getConnection();
     PreparedStatement ps = c.prepareStatement("select * from MyTable");
     ResultSet rs = ps.executeQuery();
) {
    rs.next();
    return rs.getInt(1);
} catch (SQLException e) {
    // Handle Stuff
}
```

Try-with-resources however has a few flaws:

1. There is no way to get exceptions for each resource individually without traversing e.getSuppressed() 
and doing multi-part conditionals.
2. It only works with objects implementing java.lang.AutoCloseable.
3. It cannot compose with other resource blocks.


Of course the alternative to try-with-resources it is substantially worse:

```java
Datasource ds = new JdbcDataSource()
Connection conn = null;
PreparedStatement stmt = null;
ResultSet rs = null;
try {
     conn = ds.getConnection()
     stmt = conn.prepareStatement("My Sql");
     rs = stmt.executeQuery();
     rs.next();
     return rs.getInt(1);
} catch(Exception e) {
    // Error Handling
} finally {
    try { if (rs != null) rs.close(); } catch (Exception e) {
        // logging
    };
    try { if (stmt != null) stmt.close(); } catch (Exception e) {
        // logging
    };
    try { if (conn != null) conn.close(); } catch (Exception e) {
        // logging
    };
}
```

The TryClose Monad attempts to address the limitations of try-with-resources creating a better developer experience.

## Features

The TryClose Monad is based on the scala.util.Try and shares some of the same good qualities.

1. **Immutable and Thread-Safe** - TryClose has no mutable state and every transformation always returns a new instance.
2. **Associative** - TryClose associates left to right.
3. **Recovery** - The `recover` and `recoverWith` methods are available on TryClose and have the same semantics.

Some key differences are:
1. **Lazy** - Whereas Try internally executes eagerly during the Try.apply type-constructor, TryClose is lazy and will not execute
until the `TryClose.resolve` method is called. As a consequence of this, `Success` and `Failure` in TryClose are instances of 
instances of `TryCloseResult` as opposed to of `TryClose`.  
2. **Use of TypeClasses** - In order to guarentee that the object passed into TryClose is indeed closeable, 
the Type Class `CanClose[T]` is used. This means that in order to use TryClose with an any object, a implicit conversion
to `CanClose[YourObject]` must be defined. Note however that implicits for AutoCloseable (which includes
Connection, Statement, ResultSet, InputStream, and many other objects in the Java API)
are already defined as well as some others. In order to define `CanClose` with other custom objects, 
see the [Lifting and TypeClasses](#Lifting-and-TypeClasses) section. 



### Lazyness and Composeability
Since the TryClose Monad does not do anything until the `resolve` method is called, it can be composed and
passed around in arbitrary ways without the fear of unintended execution. Here is a simple scenario.

```scala
// Create the Data Source and Open a JDBC Connection
def createConnectionAndStatement(url) = {
    val ds = new JdbcDataSource()
    datasoure.setURL(url)
    
    for {
      conn  <- TryClose(ds.getConnection())
      stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
    } yield (stmt)
}

// Now compose the previous with further statements
val output = for {
  ps <- createConnectionAndStatement("jdbc:...")
  rs <- TryClose(ps.executeQuery())
} yield wrap { rs.next(); rs.getInt(1) }
    
// Since nothing is done until output.resolve is called, you can continue
// nesting TryClose statements and re-use createConnection(url) as many
// times as needed.
output.resolve
```
It is important to note however that each invocation of TryClose should have exactly
one closeable statement returned. If multiple statements are specified inside the TryClose,
only the last one will be closed when `resolve` is called. For example:

```scala
// Create the Data Source and Open a JDBC Connection
def createConnection(url) = {
    val ds = new JdbcDataSource()
    datasoure.setURL(url)
    
    for {
      conn  <- TryClose(ds.getConnection())
      rs    <- TryClose({
                   // Do not do this! Only rs (i.e. result of ps.executeQuery()) will be closed 
                   conn.prepareStatement("select * from MyTable")
                   ps.executeQuery()
               })
    } yield wrap {
        // It's find to do this since nothing here needs to be closed 
        rs.next(); 
        rs.getInt(1) 
    }
}
```

### Lifting and TypeClasses
The TryClose Monad uses the `CanClose[T]` TypeClass in order to be able accomodate a wide veriety of use cases.
Implicit conversions for `CanClose[AutoCloseable]` exist by default in the `ImplicitCloseables` class
as well as for Unit, Throwable and others.

If one wishes to use a custom closeable object e.g. `MyCustomCloseable`, there are several options.

#### 1. Create a new Implicit `CanClose` Object/Method
```scala
// Assuming you have some kind of custom object with a closing method that needs to be called
// as cleanup after some operation that could throw and exception.  
class MyCustomCloseable(url:String) {
   someOperationThatCanThrow(url)
    
   def closeMe():Unit = {/*...*/}
   def getData:List[Int] = {List(/*...*/)}
}
       
// You can use an implicit to prove MyCustomCloseable is a CanClose
implicit object MyCustomCloseableEvidence extends CanClose[MyCustomCloseable] {
  def close(closeable:MyCustomCloseable):Unit = closeable.closeMe()
}
    
// Then you can use the standard TryClose type-constructor with your custom object.
val output = for {
  cc  <- TryClose(new MyCustomCloseable(url))
} yield (cc)
    
// Note that this will return a TryCloseResult[LambdaWrapped[T]], you can extract
// your item (the List[Int] in this case) via the Wrapped.get command.
output.resolve match {
  case Success(cc) => cc.getData
  case Failure(e) =>
} 
```

#### 2. Use TryMonad.wrapWithCloser
```scala
// Assuming you have some kind of custom object with a closing method that needs to be called
// as cleanup after some operation that could throw and exception.  
class MyCustomCloseable(url:String) {
   someOperationThatCanThrow(url)
    
   def closeMe():Unit = {/*...*/}
   def getData:List[Int] = {List(/*...*/)}
}
       
// Use the wrapWithCloser method to specify a custom lambda to close your custom object.
val output = for {
  cc  <- TryClose.wrapWithCloser(new MyCustomCloseable(url))(_.closeMe)
} yield (cc)
    
// Note that this will return a TryCloseResult[LambdaWrapped[T]], you can extract
// your item (the List[Int] in this case) via the Wrapped.get command.
output.resolve.map(_.get) match {
  case Success(cc) => cc.getData
  case Failure(e) =>
} 
```

### Non-Closeables

A convenience method called `wrap' is provided in order to be able to accommodate non closeables. It works like this:
```scala
// Assuming the ResultSet's first column is an Int, this will walk through the ResultSet and pull out
// the value of the first column in each row and add it to a list. 
@tailrec
def extractResult(rs:ResultSet, acc:List[Int] = List()):List[Int] = 
    if (rs.next) extractResult(rs, rs.getInt(1) :: acc) else (acc.reverse)
       
// Now let's invoke extractResult within the TryClose invocations.
// The extractResults method will return a List[Int] which is not closeable so it cannot go directly inside
// of a TryClose. In order to remedy this issue, we can wrap the List[Int] in a Wrapped object like so:  
// TryClose(Wrapped(extractResult(rs))). Alternatively, we can use the TryClose.wrap which is a convenience 
// method that does this for us.
 
val output = for {
  conn  <- TryClose(ds.getConnection())
  stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
  rs    <- TryClose(ps.executeQuery())
  list  <- TryClose.wrap(extractResult(rs))
} yield (list)
    
// Note that this will return a TryCloseResult[Wrapped[T]], you can extract
// your item (the List[Int] in this case) via the Wrapped.get command.
output.resolve.map(_.get) match {
  case Success(list) =>
  case Failure(e) =>
} 
```

### Recovery
TryClose has a Recovery api that works roughly the same way as in Try. The available methods are
`TryClose.recover`, `TryClose.recoverWith`, and `TryClose.transform`.

```scala
// recover
TryClose(someOperation)
  .recover {
    case e: IOException => alternativeOperation
  }

// recoverWith
TryClose(someOperation)
  .recoverWith {
    case e: IOException => TryClose(alternativeOperation)
  }
    
TryClose(someOperation)
  .transform {
    case e: IOException => TryClose(alternativeOperation)
  }
```

### Result Types
TODO