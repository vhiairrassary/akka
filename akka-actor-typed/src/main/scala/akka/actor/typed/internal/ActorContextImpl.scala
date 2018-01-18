/**
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.actor.typed
package internal

import java.util.ArrayList
import java.util.Optional
import java.util.function
import java.util.function.BiFunction

import scala.concurrent.ExecutionContextExecutor
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.annotation.InternalApi
import akka.util.OptionVal
import akka.util.Timeout

/**
 * INTERNAL API
 */
@InternalApi private[akka] trait ActorContextImpl[T] extends ActorContext[T] with javadsl.ActorContext[T] with scaladsl.ActorContext[T] {

  override def asJava: javadsl.ActorContext[T] = this

  override def asScala: scaladsl.ActorContext[T] = this

  override def getChild(name: String): Optional[ActorRef[Void]] =
    child(name) match {
      case Some(c) ⇒ Optional.of(c.upcast[Void])
      case None    ⇒ Optional.empty()
    }

  override def getChildren: java.util.List[ActorRef[Void]] = {
    val c = children
    val a = new ArrayList[ActorRef[Void]](c.size)
    val i = c.iterator
    while (i.hasNext) a.add(i.next().upcast[Void])
    a
  }

  override def getExecutionContext: ExecutionContextExecutor =
    executionContext

  override def getMailboxCapacity: Int =
    mailboxCapacity

  override def getSelf: akka.actor.typed.ActorRef[T] =
    self

  override def getSystem: akka.actor.typed.ActorSystem[Void] =
    system.asInstanceOf[ActorSystem[Void]]

  override def spawn[U](behavior: akka.actor.typed.Behavior[U], name: String): akka.actor.typed.ActorRef[U] =
    spawn(behavior, name, Props.empty)

  override def spawnAnonymous[U](behavior: akka.actor.typed.Behavior[U]): akka.actor.typed.ActorRef[U] =
    spawnAnonymous(behavior, Props.empty)

  private[akka] override def spawnMessageAdapter[U](f: U ⇒ T, name: String): ActorRef[U] =
    internalSpawnMessageAdapter(f, name)

  // Scala API impl
  override def ask[Req, Res](otherActor: ActorRef[Req])(createRequest: ActorRef[Res] ⇒ Req)(mapResponse: Try[Res] ⇒ T)(implicit responseTimeout: Timeout, classTag: ClassTag[Res]): Unit = {
    import akka.actor.typed.scaladsl.AskPattern._
    (otherActor ? createRequest)(responseTimeout, system.scheduler).onComplete(res ⇒
      self.asInstanceOf[ActorRef[AnyRef]] ! new AskResponse(res, mapResponse)
    )
  }

  // Java API impl
  def ask[Req, Res](resClass: Class[Res], otherActor: ActorRef[Req], responseTimeout: Timeout, createRequest: function.Function[ActorRef[Res], Req], applyToResponse: BiFunction[Res, Throwable, T]): Unit = {
    this.ask(otherActor)(createRequest.apply) {
      case Success(message) ⇒ applyToResponse.apply(message, null)
      case Failure(ex)      ⇒ applyToResponse.apply(null.asInstanceOf[Res], ex)
    }(responseTimeout, ClassTag[Res](resClass))
  }

  /**
   * INTERNAL API: Needed to make Scala 2.12 compiler happy if spawnMessageAdapter is overloaded for scaladsl/javadsl.
   * Otherwise "ambiguous reference to overloaded definition" because Function is lambda.
   */
  @InternalApi private[akka] def internalSpawnMessageAdapter[U](f: U ⇒ T, name: String): ActorRef[U]

  private var transformerRef: OptionVal[ActorRef[Any]] = OptionVal.None
  private var _messageTransformers: List[(Class[_], Any ⇒ T)] = Nil

  override def messageAdapter[U: ClassTag](f: U ⇒ T): ActorRef[U] = {
    val messageClass = implicitly[ClassTag[U]].runtimeClass
    // replace existing transformer for same class, only one per class is supported to avoid unbounded growth
    // in case "same" transformer is added repeatedly
    _messageTransformers = (messageClass, f.asInstanceOf[Any ⇒ T]) ::
      _messageTransformers.filterNot { case (cls, _) ⇒ cls == messageClass }
    val ref = transformerRef match {
      case OptionVal.Some(ref) ⇒ ref.asInstanceOf[ActorRef[U]]
      case OptionVal.None ⇒
        // Transform is not really a T, but that is erased
        val ref = internalSpawnMessageAdapter[Any](msg ⇒ Transform(msg).asInstanceOf[T], "trf")
        transformerRef = OptionVal.Some(ref)
        ref
    }
    ref.asInstanceOf[ActorRef[U]]
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] def messageTransformers: List[(Class[_], Any ⇒ T)] = _messageTransformers
}

