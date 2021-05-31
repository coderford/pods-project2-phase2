package pods.cabs;

import akka.actor.typed.ActorSystem;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.persistence.typed.PersistenceId;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.HashMap;
import java.util.Map;

public class App {
  public static void main(String[] args) {
    boolean firstTime = false;
    int port = 0;

    if(args.length == 1) {
      System.out.println("Got the port number: "+ args[0]);
      port = Integer.parseInt((args[0]));
    } else if(args.length == 2) {
      if(args[0].equals("-firstTime")) {
        firstTime = true;
        port = Integer.parseInt(args[1]);
      } else if(args[1].equals("-firstTime")) {
        firstTime = true;
        port = Integer.parseInt(args[0]);
        System.out.println("Got port number and firstTime");
      } else {
        System.err.println("Invalid arguments!");
        return;
      }
    }

    startup(port, firstTime);
  }

  private static void startup(int port, boolean firstTime) {
    // Override the configuration of the port
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("akka.remote.artery.canonical.port", port);
    if (port == 25251) {
    	overrides.put("akka.persistence.journal.plugin", "akka.persistence.journal.leveldb");
    	overrides.put("akka.persistence.journal.proxy.start-target-journal", "on");
    }
    else  {
    	overrides.put("akka.persistence.journal.plugin", "akka.persistence.journal.proxy");
    }

    Config config = ConfigFactory.parseMap(overrides)
        .withFallback(ConfigFactory.load());

    // Create an Akka system
    int nextRideService = -1;
    if(port == 25251) {
      nextRideService = 1;
    } else if(port == 25252) {
      nextRideService = 4;
    } else if(port == 25253) {
      nextRideService = 7;
    } else if(port == 25254) {
      nextRideService = 10;
    }
    
    if(!firstTime) {
      nextRideService = -1;
    }

    ActorSystem<Void> system = ActorSystem.create(Main.create(nextRideService), "cabs", config);
    ClusterSharding sharding = ClusterSharding.get(system);

    // Initialize sharding for both RideService and Cab
    // - RideService
    sharding.init(
      Entity.of(
        RideService.TypeKey, 
        entityContext -> RideService.create(entityContext.getEntityId())
      )
    );

    // - Cab
    sharding.init(
      Entity.of(
        Cab.TypeKey, 
        entityContext -> Cab.create(
            entityContext.getEntityId(), 
            PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId())
        )
      )
    );
  }
}
