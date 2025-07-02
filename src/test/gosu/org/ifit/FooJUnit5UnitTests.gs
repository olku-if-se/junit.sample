package org.ifit

uses org.junit.Assert
uses org.junit.jupiter.api.Test

class FooJUnit5UnitTests {

  @Test
  function shouldMakeAFooClassAccessible() : void {
    var foo : Foo = new Foo()

    Assert.assertEquals("Hello, World!", foo.bar())
    Assert.assertEquals("Goodbye, World!", foo.baz())
    Assert.assertEquals("Hello again!", foo.qux())
    Assert.assertEquals("Goodbye again!", foo.quux())
  }
}