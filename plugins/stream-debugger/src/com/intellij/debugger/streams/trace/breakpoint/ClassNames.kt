// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

const val ATOMIC_INTEGER_CLASS_NAME = "java.util.concurrent.atomic.AtomicInteger"

//region Optional class names
const val JAVA_UTIL_OPTIONAL = "java.util.Optional"
const val JAVA_UTIL_OPTIONAL_INT = "java.util.OptionalInt"
const val JAVA_UTIL_OPTIONAL_LONG = "java.util.OptionalLong"
const val JAVA_UTIL_OPTIONAL_DOUBLE = "java.util.OptionalDouble"
//endregion

//region Consumer class names
const val JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer"
const val JAVA_UTIL_FUNCTION_INT_CONSUMER = "java.util.function.IntConsumer"
const val JAVA_UTIL_FUNCTION_LONG_CONSUMER = "java.util.function.LongConsumer"
const val JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER = "java.util.function.DoubleConsumer"
//endregion

//region Consumer signatures
const val OBJECT_CONSUMER_SIGNATURE = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;"
const val INT_CONSUMER_SIGNATURE = "(Ljava/util/function/IntConsumer;)Ljava/util/stream/IntStream;"
const val LONG_CONSUMER_SIGNATURE = "(Ljava/util/function/LongConsumer;)Ljava/util/stream/LongStream;"
const val DOUBLE_CONSUMER_SIGNATURE = "(Ljava/util/function/DoubleConsumer;)Ljava/util/stream/DoubleStream;"
//endregion

//region Universal collector
const val UNIVERSAL_COLLECTOR_CLASS_NAME = "com.intellij.debugger.stream.rt.java.collectors.UniversalCollector"
const val UNIVERSAL_COLLECTOR_CLASS_FILE = "com/intellij/debugger/stream/rt/java/collectors/UniversalCollector.class"
const val UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Z)V"
//endregion

//region Predicate class names
const val JAVA_UTIL_FUNCTION_PREDICATE = "java.util.function.Predicate"
const val JAVA_UTIL_FUNCTION_INT_PREDICATE = "java.util.function.IntPredicate"
const val JAVA_UTIL_FUNCTION_LONG_PREDICATE = "java.util.function.LongPredicate"
const val JAVA_UTIL_FUNCTION_DOUBLE_PREDICATE = "java.util.function.DoublePredicate"
//endregion

//region Runtime matchers
const val INT_MATCHER_CLASS_NAME = "com.intellij.debugger.stream.rt.java.matchers.IntMatcher"
const val LONG_MATCHER_CLASS_NAME = "com.intellij.debugger.stream.rt.java.matchers.LongMatcher"
const val DOUBLE_MATCHER_CLASS_NAME = "com.intellij.debugger.stream.rt.java.matchers.DoubleMatcher"
const val OBJECT_MATCHER_CLASS_NAME = "com.intellij.debugger.stream.rt.java.matchers.ObjectMatcher"

const val INT_MATCHER_CLASS_FILE = "com/intellij/debugger/stream/rt/java/matchers/IntMatcher.class"
const val LONG_MATCHER_CLASS_FILE = "com/intellij/debugger/stream/rt/java/matchers/LongMatcher.class"
const val DOUBLE_MATCHER_CLASS_FILE = "com/intellij/debugger/stream/rt/java/matchers/DoubleMatcher.class"
const val OBJECT_MATCHER_CLASS_FILE = "com/intellij/debugger/stream/rt/java/matchers/ObjectMatcher.class"

const val INT_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/IntPredicate;)V"
const val LONG_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/LongPredicate;)V"
const val DOUBLE_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/DoublePredicate;)V"
const val OBJECT_MATCHER_CONSTRUCTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/Predicate;)V"
//endregion