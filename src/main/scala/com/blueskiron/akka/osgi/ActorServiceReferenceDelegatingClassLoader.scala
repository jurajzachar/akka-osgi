package com.blueskiron.akka.osgi

import org.osgi.framework.BundleContext
import org.osgi.framework.wiring.BundleWire
import org.osgi.framework.wiring.BundleWiring
import scala.annotation.tailrec
import org.osgi.framework.Bundle
import java.net.URL
import scala.util.Failure
import scala.util.Try
import scala.util.Success
import org.osgi.framework.wiring.BundleRevision
import scala.collection.JavaConverters._
import java.util.Enumeration
import org.slf4j.LoggerFactory
import akka.actor.Actor
import org.osgi.util.tracker.ServiceTracker

/*
 * Companion object to create bundle delegating ClassLoader instances based on registered ServiceReferences of Actor trait.
 */
object ActorServiceReferenceDelegatingClassLoader {

  /*
   * Create a bundle delegating ClassLoader for the bundle context's bundle
   */
  def apply(context: BundleContext): ActorServiceReferenceDelegatingClassLoader = new ActorServiceReferenceDelegatingClassLoader(context, null)

  def apply(context: BundleContext, fallBackCLassLoader: Option[ClassLoader]): ActorServiceReferenceDelegatingClassLoader =
    new ActorServiceReferenceDelegatingClassLoader(context, fallBackCLassLoader.orNull)
}

/**
 * Based on {@link https://github.com/akka/akka/blob/master/akka-osgi/src/main/scala/akka/osgi/BundleDelegatingClassLoader.scala}.
 * 
 * The difference in this approach is that this Classloader considers all bundles 
 * (and their depending transitive bundles) if they register ServiceReference[Actor]
 * 
 * @author juraj.zachar@gmail.com
 */
class ActorServiceReferenceDelegatingClassLoader(bundleContext: BundleContext, fallBackClassLoader: ClassLoader) extends ClassLoader(fallBackClassLoader) {
  
  private val log = LoggerFactory.getLogger(this.getClass)
  
  private val serviceTracker = new ServiceTracker(bundleContext, classOf[Actor], null);
  
  /**
   * find a set of all bundles that have registered ServiceReference[Actor] and include their transitive dependencies
   * include own bundle and transitive bundles for AKKA system stuff.
   */
  private def actorBundles = {
     for {
       actorBundle <- bundleContext.getServiceReferences(classOf[Actor], null).asScala.map(serviceReference => serviceReference.getBundle)
       transitiveBundle <- findTransitiveBundles(actorBundle)
     } yield Set(actorBundle) ++ Set(transitiveBundle)
  }.flatten.toSet ++ findTransitiveBundles(bundleContext.getBundle)

  override def findClass(name: String): Class[_] = {
    @tailrec def find(remaining: Iterable[Bundle]): Class[_] = {
      if (remaining.isEmpty){
        log.error(s"Exhausted search for $name in: $actorBundles!")
        throw new ClassNotFoundException(name)
      }
      else Try { remaining.head.loadClass(name) } match {
        case Success(cls) ⇒ {
          log.debug(s"Loading '$name' from bundle ${remaining.head.getSymbolicName}")
          cls
        }
        case Failure(_)   ⇒ find(remaining.tail)
      }
    }
    find(actorBundles)
  }

  override def findResource(name: String): URL = {
    @tailrec def find(remaining: Iterable[Bundle]): URL = {
      if (remaining.isEmpty) getParent.getResource(name)
      else Option { remaining.head.getResource(name) } match {
        case Some(r) ⇒ r
        case None    ⇒ find(remaining.tail)
      }
    }
    find(actorBundles)
  }

  override def findResources(name: String): Enumeration[URL] = {
    val resources = actorBundles.flatMap {
      bundle ⇒ Option(bundle.getResources(name)).map { _.asScala.toList }.getOrElse(Nil)
    } 
    java.util.Collections.enumeration(resources.toList.asJava)
  }

  private def findTransitiveBundles(bundle: Bundle): Set[Bundle] = {
    @tailrec def process(processed: Set[Bundle], remaining: Set[Bundle]): Set[Bundle] = {
      if (remaining.isEmpty) {
        processed
      } else {
        val (b, rest) = (remaining.head, remaining.tail)
        if (processed contains b) {
          process(processed, rest)
        } else {
          val wiring = b.adapt(classOf[BundleWiring])
          val direct: Set[Bundle] =
            if (wiring == null) Set.empty
            else {
              val requiredWires: List[BundleWire] =
                wiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE).asScala.toList
              requiredWires.flatMap {
                wire ⇒ Option(wire.getProviderWiring) map { _.getBundle }
              }.toSet
            }
          process(processed + b, rest ++ (direct diff processed))
        }
      }
    }
    process(Set.empty, Set(bundle))
  }
}