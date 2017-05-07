package com.blueskiron.akka.osgi.api

import akka.actor.ActorSystem
import com.typesafe.config.Config

/**
 * A simple trait that exposes Akka's {@link ActorSystem} and Typesafe's {@link Config} as a service.
 * 
 * @author juraj.zachar@gmail.com
 *
 */
trait ActorSystemWithConfig {
  
  /**
   * ActorSystem.
   */
  def getActorSystem: ActorSystem
  
  /**
   * Config.
   */
  def getConfig: Config
}
