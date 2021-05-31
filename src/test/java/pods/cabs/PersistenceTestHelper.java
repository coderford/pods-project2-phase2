/* This class provides methods (in form of tests) to do some rides on cabs
 * and for getting and printing the numRides for each cab.
 * Useful for checking whether persistence is actually working.
 */ 
package pods.cabs;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import pods.cabs.RideService.Command;

import java.time.Duration;
import java.util.Random;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;



public class PersistenceTestHelper {
    private static ClusterSharding sharding;
    public static int count1ForTest3 = 0;
    public static int count2ForTest3 = 0;
    public static int count3ForTest3 = 0;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ActorTestKit.create("cabs"));

    @Before
    public void init() {
        sharding = ClusterSharding.get(testKit.system());
        sharding.init(
            Entity.of(
                Cab.TypeKey, entityContext -> 
                    Cab.create(
                        entityContext.getEntityId(), 
                        PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId())
                    )
            )
        );

        sharding.init(
            Entity.of(
                RideService.TypeKey, 
                entityContext -> RideService.create(entityContext.getEntityId())
            )
        );
        printMessage("Sharding initialization successful");
    }

    public static void printMessage(String message) {
        System.out.println("\n=== "+message+" ===\n");
    }
    
    public static void printMessageBig(String message) {
        String border = "";
        for(int i = 0; i < message.length(); i++) border += "*";
        border += "********";

        System.out.println("\n" + border);
        System.out.println("*** "+message+" ***");
        System.out.println(border + "\n");
    }

    @Test
    public void printNumRides() {
        String cabid = "101";
        TestProbe<Cab.NumRidesResponse> testProbe = testKit.createTestProbe();
        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, cabid);

        cab.tell(new Cab.NumRides(testProbe.getRef()));
        Cab.NumRidesResponse resp = testProbe.receiveMessage(Duration.ofSeconds(10));

        printMessage("Number of rides for cab " + cabid + ": " + resp.numRides);
    }

    @Test
    public void randomRides() {
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "101");
        cab.tell(new Cab.Reset(cabResetProbe.getRef()));
        cabResetProbe.receiveMessage(Duration.ofSeconds(5));
        printMessage("Cab 101 reset successful");
        
        Random rand = new Random();
        cab.tell(new Cab.SignIn(0));
        printMessage("Cab 101 signed in");

        try{
            Thread.sleep(1000);
        }
        catch(Exception e) {
            System.out.println("ERROR, cannot sleep");
        }

        int numRidesToDo = rand.nextInt(10)+1;

        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();
        String rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        for(int j = 0; j < numRidesToDo; j++) {
            rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
            RideService.RideResponse resp = probe.receiveMessage();
            assert(resp.rideId != -1);
            cab.tell(new Cab.RideEnded(resp.rideId));

            // Since cab is not interested now, we'll send another ride request just to reset
            rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
            resp = probe.receiveMessage();
            assert(resp.rideId == -1);
        }

        printMessage("Executed "+numRidesToDo+" rides for cab 101");
    }
}
