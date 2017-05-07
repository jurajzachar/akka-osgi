package com.blueskiron.akka.osgi

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import akka.actor.ActorSystem
import org.osgi.framework.ServiceRegistration
import org.osgi.framework.ServiceReference
import org.osgi.service.cm.ManagedService
import java.util.Dictionary
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import scala.util.Try
import scala.util.Failure
import com.typesafe.config.ConfigFactory
import java.nio.file.Files
import java.nio.file.Paths
import scala.util.Success
import scala.concurrent.Await
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.framework.Constants
import com.typesafe.config.ConfigRenderOptions
import com.blueskiron.akka.osgi.api.ActorSystemWithConfig

/**
 * OSGi Bundle activator implementation to bootstrap and configure AKKA actor system in an
 * OSGi environment.
 *
 * Exposes {@link ActorSystem} and {@link Config} via {@link ActoSystemWithConfig} service.
 * This service can be consumed by other bundles providing they have registered ServiceReference[Actor].
 *
 * See {@link ActorServiceReferenceDelegatingClassLoader} for more details on the class loading logic.
 *
 * @author juraj.zachar@gmail.com
 *
 */
class AkkaActivator extends BundleActivator {

  private var managedAkka: Option[ManagedAkkaService] = None
  private var serviceReg: Option[ServiceRegistration[ManagedService]] = None

  def start(context: BundleContext): Unit = {
    managedAkka = Some(new ManagedAkkaService(context))
    val props = new java.util.Hashtable[String, Object]()
    props.put(Constants.SERVICE_PID, AkkaActivator.CONFIG_PID)
    serviceReg = Some(context.registerService(classOf[ManagedService], managedAkka.get, props))
  }

  def stop(context: BundleContext): Unit = {
    managedAkka.map(instance => instance.destroy)
  }

}

/**
 * Companion object.
 * 
 * @author juraj.zachar@gmail.com
 *
 */
object AkkaActivator {
   val CONFIG_PID = "akka.options"
}

/**
 * OSGi boiler plate code to bind service registration to configuration. Placeholder implementation for {@link ActorSystemWithConfig}
 *
 * @author juri
 *
 */
private class ManagedAkkaService(private val bundleContext: BundleContext) extends ActorSystemWithConfig with ManagedService {
  
  val ACTOR_SYSTEM_NAME = AkkaActivator.CONFIG_PID + ".actorSystemName"
  val CONFIG_FILE_PATH = AkkaActivator.CONFIG_PID +".configFilePath"
  
  private val log = LoggerFactory.getLogger(this.getClass)
  private var serviceRegistration: Option[ServiceRegistration[ActorSystemWithConfig]] = None
  private var config: Option[Config] = None
  private var actorSystem: Option[ActorSystem] = None

  def updated(configuration: Dictionary[_, _]): Unit = {

    if (configuration != null) {
      loadConfig(configuration.get(CONFIG_FILE_PATH).asInstanceOf[String]) match {
        case Success(config) => bootstrapWith(getName(configuration), config)
        case Failure(t) => log.error("Failed to load AKKA config!", t)
      }
    }
  }

  private def getName(configuration: Dictionary[_, _]): Option[String] = {
    configuration.get(ACTOR_SYSTEM_NAME) match {
      case null => None
      case name => Some(name.asInstanceOf[String])
    }
  }
  private def bootstrapWith(name: Option[String], config: Config) {
    log.info(s"Configuration updated! Setting Config to '{}'", config.root().render(ConfigRenderOptions.concise().setJson(true)))
    log.info("Resolving Config...")
    this.config = Some(config.resolve());
    if (!serviceRegistration.isDefined) {
      log.info("Starting up...")
      val classLoader = ActorServiceReferenceDelegatingClassLoader.apply(bundleContext, Some(Thread.currentThread().getContextClassLoader))
      actorSystem = Some(ActorSystem.apply(name.getOrElse("bundle-%s-ActorSystem".format(bundleContext.getBundle.getBundleId)), this.config.get, classLoader))
      this.serviceRegistration = Some(bundleContext.registerService(classOf[ActorSystemWithConfig], this, null))
    }
  }

  private def loadConfig(filePath: String): Try[Config] = {
    //load AKKA reference config that is bundled in...
    val referenceConfig = ConfigFactory.defaultReference(this.getClass.getClassLoader)

    if (filePath == null) {
      log.warn(s"Cannot find configuration value for '$CONFIG_FILE_PATH'! Using system defaults...")
      Success(referenceConfig)
    } else {
      //try parsing user config
      Try {
        val file = Paths.get(filePath).toFile()
        log.debug(s"Loading: $file")
        ConfigFactory.parseFile(file).withFallback(referenceConfig)
      }
    }
  }

  def getActorSystem: ActorSystem = actorSystem.get

  def getConfig: Config = config.get

  def destroy = {
    if (serviceRegistration.isDefined) {
      log.info(s"Unregistering: '$classOf[ActorSystemWithConfig]'")
      serviceRegistration.get.unregister()
    }
    if (actorSystem.isDefined) {
      log.info("Shutting down AKKA...")
      import scala.concurrent.duration._
      Await.result(actorSystem.get.terminate(), 5 second)
    }
  }

}