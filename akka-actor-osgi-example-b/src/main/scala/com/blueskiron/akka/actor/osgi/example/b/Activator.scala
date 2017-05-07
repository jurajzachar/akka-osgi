package com.blueskiron.akka.actor.osgi.example.b

import org.apache.felix.dm.DependencyActivatorBase
import org.osgi.framework.BundleContext
import akka.actor.Actor
import com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB
import org.osgi.framework.BundleActivator
import org.osgi.util.tracker.ServiceTracker
import com.blueskiron.akka.osgi.api.ActorSystemWithConfig
import akka.actor.Props
import akka.actor.ActorSelection
import java.util.concurrent.TimeUnit
import akka.util.Timeout
import scala.concurrent.duration.FiniteDuration
import scala.util.Success
import scala.util.Failure
import org.slf4j.LoggerFactory


class Activator extends BundleActivator {
  
  val log = LoggerFactory.getLogger(this.getClass)
  val timeoutMillis = 5000
  implicit val timeout = Timeout(FiniteDuration(timeoutMillis, TimeUnit.MILLISECONDS))
  
  def start(context: BundleContext): Unit = {
    log.info("Starting...")
    val serviceTracker = new ServiceTracker[ActorSystemWithConfig, ActorSystemWithConfig](context, classOf[ActorSystemWithConfig], null)
    serviceTracker.open()
    val actorSystemWithConfig = serviceTracker.waitForService(timeoutMillis)
    serviceTracker.close()
    val actorSystem  = actorSystemWithConfig.getActorSystem
    val actorName = "ActorOsgiExampleB" // can be shared via akka config, set in app config, etc.
    val actorB = actorSystem.actorOf(Props[ActorOsgiExampleB], actorName)
    //try look up actorB and initiate communication
    implicit val exc = actorSystem.dispatcher
    val otherActorName = "ActorOsgiExampleA"
    actorSystem.actorSelection("user/" +  otherActorName).resolveOne().onComplete {
      case Success(actorRef) => actorB ! actorRef
      case Failure(t) => log.warn(s"Failed to resolve the other actor=$otherActorName within timeout!")
    }
  }

  def stop(context: BundleContext): Unit = {
    //Should grab the system and clean up --> stop and destroy actors
    //nothing to do here.
  }
}