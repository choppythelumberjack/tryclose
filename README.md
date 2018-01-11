# Choppy's Lazy TryClose Monad

The TryClose Monad is designed to be a lazy alternative to Scala'a Try Monad as well as
to be Scala's answer to Java's try-with-resources construct. Where Java uses contrived 
language constructs to manage resources, Scala can answer with for-comprehensions and monadic power.
This library was envisioned to mostly use JDBC related resources (e.g. Connections, ResultSets etc...)
but other uses like managing streams are also good use cases.

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

TODO Note how each closeable-returning statement should be separate TryClose block.

### Lazyness
TODO

###Composeability
TODO

###Recovery
TODO

###Result Types
TODO