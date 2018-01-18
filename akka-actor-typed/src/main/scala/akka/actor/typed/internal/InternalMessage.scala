/**
 * Copyright (C) 2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.actor.typed.internal

import akka.annotation.InternalApi

/**
 * INTERNAL API: Wrapping of messages that should be transformed
 */
@InternalApi private[akka] final case class Transform(msg: Any)

// FIXME move AskResponse in other PR
