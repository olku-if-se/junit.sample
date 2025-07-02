package org.ifit

uses junit.framework.TestCase
uses org.junit.Assert


class FooUnitTests extends TestCase {

  function testMakeAFooClassAccessible() : void {
    var foo : Foo = new Foo()

    Assert.assertEquals("Hello, World!", foo.bar())
    Assert.assertEquals("Goodbye, World!", foo.baz())
    Assert.assertEquals("Hello again!", foo.qux())
    Assert.assertEquals("Goodbye again!", foo.quux())
  }
}