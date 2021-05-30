package pods.cabs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;

public class Main {

    public static class Started {

    }

    public static Behavior<Void> create(int nextRideService) {
        return Behaviors.setup(context -> {
            /*
             * Initialize CabData HashMap
             */
            ActorRef<Cab.Command> cab;
            HashMap<String, CabData> cabDataMap = new HashMap<>();

            int initBalance = 0;
            ArrayList<String> cabIds = new ArrayList<>();
            ArrayList<String> walletIds = new ArrayList<>();

            try {
                File inputFile = new File("IDs.txt");
                Scanner in = new Scanner(inputFile);

                int section = 0;
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    if (line.compareTo("****") == 0) {
                        section++;
                    } else if (section == 1) {
                        cabIds.add(line);
                    } else if (section == 2) {
                        walletIds.add(line);
                    } else if (section == 3) {
                        initBalance = Integer.parseInt(line);
                        Globals.initBalance=Integer.parseInt(line);
                    }
                }

                in.close();
            } catch (Exception e) {
                System.out.println("ERROR: Could not read input file!");
            }
            
            // If nextRideService is positive, create 3 more RideService entities
            if(nextRideService > 0) {
                final ClusterSharding sharding = ClusterSharding.get(context.getSystem());

                for (int i = nextRideService; i < nextRideService + 3; i++) {
                    String name = "ride-actor-" + Integer.toString(i);

                    // hopefully this will spawn the rideservice on the same node
                    EntityRef<RideService.Command> ref = sharding.entityRefFor(RideService.TypeKey, name);
                }
            }

            // Will accept no more messages
            return Behaviors.empty();
        });
    }
}
