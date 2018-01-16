package com.github.choppythelumberjack

package object tryclose extends ImplicitCloseables {

  def wrap[T](t:T) = Wrapper(t)

  implicit class WrappedResultExtensions[T](result:TryCloseResult[Wrapper[T]]) {
    def unwrap = result.map(_.get)
  }

  implicit class LambdaWrappedResultExtensions[T](result:TryCloseResult[LambdaWrapper[T]]) {
    def unwrap = result.map(_.get)
  }

  implicit class WrappedTryCloseExtensions[T](tc:TryClose[Wrapper[T]]) {
    def unwrap = tc.resolve.unwrap
  }

  implicit class LambdaTryCloseResultExtensions[T](tc:TryClose[LambdaWrapper[T]]) {
    def unwrap = tc.resolve.unwrap
  }
}
