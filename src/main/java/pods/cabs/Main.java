package pods.cabs;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;

public class Main {

    public static class Started {

    }

    public static Behavior<Void> create(int nextRideService) {
        return Behaviors.setup(context -> {
            ArrayList<String> cabIds = new ArrayList<>();

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
                    } 
                }

                in.close();
            } catch (Exception e) {
                System.out.println("ERROR: Could not read input file!");
            }
            
            // If nextRideService is positive, create 3 more RideService entities
            if(nextRideService > 0) {
                final ClusterSharding sharding = ClusterSharding.get(context.getSystem());
                sharding.init(
                    Entity.of(
                        RideService.TypeKey, 
                        entityContext -> RideService.create(entityContext.getEntityId())
                    )
                );

                for (int i = nextRideService; i < nextRideService + 3; i++) {
                    String name = "ride-actor-" + Integer.toString(i);

                    // hopefully this will spawn the rideservice on the same node
                    sharding.entityRefFor(RideService.TypeKey, name);
                }
            }

            // Will accept no more messages
            return Behaviors.empty();
        });
    }
}
