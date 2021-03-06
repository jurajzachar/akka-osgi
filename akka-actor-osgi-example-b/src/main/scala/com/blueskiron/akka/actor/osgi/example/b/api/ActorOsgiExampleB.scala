package com.blueskiron.akka.actor.osgi.example.b.api

import akka.actor.Actor
import scala.concurrent.duration._
import akka.actor.ActorRef
import org.slf4j.LoggerFactory

/**
 * Example Actor implementation.
 * 
 * @author juraj.zachar@gmail.com
 *
 */
class ActorOsgiExampleB extends Actor {
  
  private val log = LoggerFactory.getLogger(this.getClass)
  
  implicit val exc = context.dispatcher
  def receive: Actor.Receive = {
    case actorRef: ActorRef => sayHello(actorRef)
    case msg => {
      log.debug(s"Received: $msg")
         context.system.scheduler.scheduleOnce(1000 milliseconds, sender, "Hello from: " + this.getClass)
    }
  }
  
  private def sayHello(actorRef: ActorRef) {
    actorRef ! "Hi there!"
  }
  
}