package fs2

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executor, Executors, ThreadFactory}
import scala.concurrent.ExecutionContext

import fs2.util.NonFatal

/** Provides a function for evaluating thunks, possibly asynchronously. */
trait Strategy {
  def apply(thunk: => Unit): Unit

  override def toString = "Strategy"
}

object Strategy {

  /** A `ThreadFactory` which creates daemon threads, using the given name. */
  def daemonThreadFactory(threadName: String, exitJvmOnFatalError: Boolean = true): ThreadFactory = new ThreadFactory {
    val defaultThreadFactory = Executors.defaultThreadFactory()
    val idx = new AtomicInteger(0)
    def newThread(r: Runnable) = {
      val t = defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(s"$threadName-${idx.incrementAndGet()}")
      t.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
        def uncaughtException(t: Thread, e: Throwable): Unit = {
          System.err.println(s"------------ UNHANDLED EXCEPTION ---------- (${t.getName})")
          e.printStackTrace(System.err)
          if (exitJvmOnFatalError) {
            e match {
              case NonFatal(_) => ()
              case fatal => System.exit(-1)
            }
          }
        }
      })
      t
    }
  }

  /** Create a `Strategy` from a fixed-size pool of daemon threads. */
  def fromFixedDaemonPool(maxThreads: Int, threadName: String = "Strategy.fromFixedDaemonPool"): Strategy =
    fromExecutor(Executors.newFixedThreadPool(maxThreads, daemonThreadFactory(threadName)))

  /** Create a `Strategy` from a growable pool of daemon threads. */
  def fromCachedDaemonPool(threadName: String = "Strategy.fromCachedDaemonPool"): Strategy =
    fromExecutor(Executors.newCachedThreadPool(daemonThreadFactory(threadName)))

  /** Create a `Strategy` from an `ExecutionContext`. */
  def fromExecutionContext(es: ExecutionContext): Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      es.execute { new Runnable { def run = thunk }}
  }

  /** Create a `Strategy` from an `ExecutionContext`. */
  def fromExecutor(es: Executor): Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      es.execute { new Runnable { def run = thunk } }
  }

  /**
   * A `Strategy` which executes its argument immediately in the calling thread,
   * blocking until it is finished evaluating.
   */
  def sequential: Strategy = new Strategy {
    def apply(thunk: => Unit): Unit =
      thunk
  }
}
