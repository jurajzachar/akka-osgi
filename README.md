# AKKA OSGi
Taming AKKA Actors in OSGi container.

# Dependencies

 * Scala Lang 2.12.x
 * Scala Java8 Compat (latest)
 * AKKA 2.5.x
 * Apache Felix Container 4.x

# Caveats
AKKA bundles its 'reference.conf' inside akka-actors jar which is not so ideal for OSGi deployments. According to [AKKA documentation](http://doc.akka.io/docs/akka/current/additional/osgi.html#Intended_Use) only one (and possibly) big bundle should be deployed containing all actors and their corresponding ActorSystem.

This sucks for dynamic and modular deployments where Actors might come and go and where all parts of application might be __not__ be implemented in one go...

# Configuration
Drop into $karaf.home/etc directory config file __akka.options.cfg__.
A sample config might look like this.

	akka.options.actorSystemName=MyAkkaActorSystem
	akka.options.configFilePath=/path/to/my/application.conf

Which will instruct the system about what your ActorSystem shall be called and where to look for AKKA config.

# Good old-fashioned OSGi Service
Write your actors as you'd normally would. A bit of boilerplate code is needed to bring everything together using __Activator__. Use OSGi's ServiceTracker (or other favorite method) to grab this service.

> ActorSystemWithConfig

This service exposes already configured __ActorSystem__ and parsed __Typesafe Config__. All of this happens in __akka-osgi__ bundle. Deploy your actors using _Props_ and optionally give them names or just just consume the _Config_ which is meant for this purpose.

__Important__[!]. Create at least one instance of

> ServiceRegistration[Actor]

in the bundle where your actors are packaged. This instructs __ActorSystemWithCofig__ ClassLoader to consider you bundle in the search when looking for Actor implementation class.

# Example
In the provided example two types of actors come from two separate bundles which might be deployed into the container dynamically:

	bunle[akka-actor-osgi-example-a] --> ActorOsgiExampleA
	bundle[akka-actor-osgi-example-b] --> ActorOsgiExampleB

## Output of example run

	2017-05-07 22:29:10.477 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-b - BundleEvent UNRESOLVED - com.blueskiron.akka-actor-osgi-example-b
	2017-05-07 22:29:10.477 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-b - BundleEvent UPDATED - com.blueskiron.akka-actor-osgi-example-b
	2017-05-07 22:29:10.479 org.apache.felix.framework:[FelixDispatchQueue] DEBUG org.apache.felix.framework - FrameworkEvent PACKAGES REFRESHED - org.apache.felix.framework
	2017-05-07 22:29:15.504 com.blueskiron.akka-actor-osgi-example-a:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-a - BundleEvent RESOLVED - com.blueskiron.akka-actor-osgi-example-a
	2017-05-07 22:29:15.506 com.blueskiron.akka-actor-osgi-example-a:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-a - BundleEvent STARTING - com.blueskiron.akka-actor-osgi-example-a
	2017-05-07 22:29:15.508 com.blueskiron.akka-actor-osgi-example-a:[pipe-update 60] INFO  com.blueskiron.akka.actor.osgi.example.a.Activator - Starting...
	2017-05-07 22:29:15.525 com.blueskiron.akka-actor-osgi-example-a:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-a - BundleEvent STARTED - com.blueskiron.akka-actor-osgi-example-a
	2017-05-07 22:29:15.527 com.blueskiron.akka-actor-osgi-example-a:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] WARN  com.blueskiron.akka.actor.osgi.example.a.Activator - Failed to resolve the other actor=ActorOsgiExampleB within Timeout(5000 milliseconds)!
	2017-05-07 22:29:22.150 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-b - BundleEvent RESOLVED - com.blueskiron.akka-actor-osgi-example-b
	2017-05-07 22:29:22.152 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-b - BundleEvent STARTING - com.blueskiron.akka-actor-osgi-example-b
	2017-05-07 22:29:22.154 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] INFO  com.blueskiron.akka.actor.osgi.example.b.Activator - Starting...
	2017-05-07 22:29:22.157 com.blueskiron.akka-actor-osgi-example-b:[pipe-update 60] DEBUG com.blueskiron.akka-actor-osgi-example-b - BundleEvent STARTED - com.blueskiron.akka-actor-osgi-example-b
	2017-05-07 22:29:22.158 com.blueskiron.akka-actor-osgi-example-a:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA - Received: Hi there!
	2017-05-07 22:29:22.426 com.blueskiron.akka-actor-osgi-example-b:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA
	2017-05-07 22:29:23.445 com.blueskiron.akka-actor-osgi-example-a:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB
	2017-05-07 22:29:23.717 com.blueskiron.akka-actor-osgi-example-b:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA
	2017-05-07 22:29:24.737 com.blueskiron.akka-actor-osgi-example-a:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB
	2017-05-07 22:29:25.007 com.blueskiron.akka-actor-osgi-example-b:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA
	2017-05-07 22:29:26.026 com.blueskiron.akka-actor-osgi-example-a:[bundle-58-ActorSystem-akka.actor.default-dispatcher-2] DEBUG com.blueskiron.akka.actor.osgi.example.a.api.ActorOsgiExampleA - Received: Hello from: class com.blueskiron.akka.actor.osgi.example.b.api.ActorOsgiExampleB
