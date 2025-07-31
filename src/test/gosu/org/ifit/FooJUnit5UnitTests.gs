package org.ifit

uses Assert#assertEquals
uses Mockito#mock
uses Mockito#when
uses org.junit.Assert
uses org.junit.jupiter.api.Test
uses org.mockito.Mockito

class FooJUnit5UnitTests {

  @Test
  function shouldMakeAFooClassAccessible() : void {
    var foo : Foo = new Foo()

    assertEquals("Hello, World!", foo.bar())
    assertEquals("Goodbye, World!", foo.baz())
    assertEquals("Hello again!", foo.qux())
    assertEquals("Goodbye again!", foo.quux())
  }

  @Test
  function shouldMockTheGosuFooClass() : void {
    var mockFoo : Foo = mock(Foo)

    when(mockFoo.bar()).thenReturn("Mocked Hello, World!")
    when(mockFoo.baz()).thenReturn("Mocked Goodbye, World!")
    when(mockFoo.qux()).thenReturn("Mocked Hello again!")
    when(mockFoo.quux()).thenReturn("Mocked Goodbye again!")

    assertEquals("Mocked Hello, World!", mockFoo.bar())
    assertEquals("Mocked Goodbye, World!", mockFoo.baz())
    assertEquals("Mocked Hello again!", mockFoo.qux())
    assertEquals("Mocked Goodbye again!", mockFoo.quux())
  }
}