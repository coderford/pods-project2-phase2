package pods.cabs;

import akka.actor.typed.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.HashMap;
import java.util.Map;

public class App {
  public static void main(String[] args) {
    boolean firstTime = false;
    int port = 0;

    if(args.length == 1) {
      port = Integer.parseInt((args[0]));
    } else if(args.length == 2) {
      if(args[0].equals("-firstTime")) {
        firstTime = true;
        port = Integer.parseInt(args[1]);
      } else if(args[1].equals("-firstTime")) {
        firstTime = true;
        port = Integer.parseInt(args[0]);
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

    Config config = ConfigFactory.parseMap(overrides)
        .withFallback(ConfigFactory.load());

    // Create an Akka system
    int nextRideService = 1;
    if(port == 25252) {
      nextRideService = 4;
    } else if(port == 25253) {
      nextRideService = 7;
    } else if(port == 25254) {
      nextRideService = 10;
    }

    ActorSystem<Void> system = ActorSystem.create(Main.create(nextRideService), "cabs");
  }
}