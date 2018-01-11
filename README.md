# Choppy's Lazy TryClose Monad

The TryClose Monad is designed to be a lazy alternative to Scala's Try Monad as well as
to be Scala's answer to Java's try-with-resources construct. Where Java uses contrived 
language constructs to manage resources, Scala can answer with for-comprehensions and monadic power.
This library was envisioned to mostly use JDBC related resources (e.g. Connections, ResultSets etc...)
but other scenarios like managing streams are also good use cases.

Using TryClose we can manage database resources like this:
```scala
val ds = new JdbcDataSource()
val output = for {
  conn  <- TryClose(ds.getConnection())
  stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
  rs    <- TryClose(ps.executeQuery())
} yield (rs.getInt(1))
// Note that Nothing will actually be done until 'resolve' is called
output.resolve
```

The Java analogue using try-with-resources would look like this:
```java
DataSource ds = new JdbcDataSource();
try (Connection c = ds.getConnection();
     PreparedStatement ps = c.prepareStatement("select * from MyTable");
     ResultSet rs = ps.executeQuery();
) {
    return rs.getInt(1);
} catch (SQLException e) {
    // Handle Stuff
}
```

## Features

### Lazyness and Composeability
Since the TryClose Monad does not do anything until the `resolve` method is called, it can be composed and
passed around in arbitrary ways without the fear of unintended execution. Here is a simple scenario.

```scala
// Create the Data Source and Open a JDBC Connection
def createConnection(url) = {
    val ds = new JdbcDataSource()
    datasoure.setURL(url)
    
    for {
      conn  <- TryClose(ds.getConnection())
      stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
    } yield (stmt)
}

// Now compose the previous with further statements
val output = for {
  conn  <- createConnection("jdbc:...")
  stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
  rs    <- TryClose(ps.executeQuery())
} yield (rs.getInt(1))

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
      rs  <- TryClose({
                   // Do not do this! Only `rs` (i.e. result of `ps.executeQuery()`) will be closed 
                   conn.prepareStatement("select * from MyTable")
                   ps.executeQuery()
               })
    } yield (rs.getInt(1))
}
```

###Lifting and TypeClasses
The TryClose Monad uses the `CanClose[T]` TypeClass in order to be able accomodate a wide veriety of use cases.
Implicit conversions for `CanClose[AutoCloseable]` exist by default in the `ImplicitCloseables` class
as well as for Unit, Throwable and others.

If one wishes to use a custom closeable object e.g. `MyCustomCloseable`, there are several options.

#### 1. Create a new Implicit `CanClose` Object/Method
TODO

#### 2. Use TryMonad.wrapWithCloser
TODO


###Non-Closeables

A convenience method called `wrap' is provided in order to be able to accommodate non closeables. It works like this:
```
// Assuming the ResultSet's first column is an Int, this will walk through the ResultSet and pull out
// the value of the first column in each row and add it to a list. 
@tailrec
def extractResult(rs:ResultSet, acc:List[Int] = List()):List[Int] = 
    if (rs.next) 
        extractResult(rs, rs.getInt(1) :: acc)
    else
       acc.reverse
// Here is how to invoke extractResult on a result set in a TryClose Monad.
val output = for {
  conn  <- createConnection("jdbc:...")
  stmt  <- TryClose(conn.prepareStatement("select * from MyTable"))
  rs    <- TryClose(ps.executeQuery())
  list  <- TryClose.wrap(extractResult(rs)
} yield (list)
```

###Recovery
TODO

###Result Types
TODO