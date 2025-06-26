package org.ifit;

public @interface BadExample {
    String value() default "This is a bad example of a test case. It does not follow best practices and should be improved.";
}
