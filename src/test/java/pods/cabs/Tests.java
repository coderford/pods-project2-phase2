package pods.cabs;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.persistence.typed.PersistenceId;
import pods.cabs.RideService.Command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Random;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;



public class Tests {
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

    // Cab 101 signs in, customer 201 requests ride
    // test PASSED if customer 201 is assigned ride
    @Test
    public void test1() {
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            Cab.NumRidesResponse resp = cabResetProbe.receiveMessage(Duration.ofSeconds(5));
        }

        printMessage("Cabs reset successful");

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "102");
        cab.tell(new Cab.SignIn(10));
        printMessage("Cab 102 signed in");

        Random rand = new Random();
        String rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        printMessage("Sending ride request to " + rsid);
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert (resp.rideId != -1);
        printMessage("Ride for customer 201 started");

        cab.tell(new Cab.RideEnded(resp.rideId));
        printMessageBig("TEST 1 PASSED");
    }

    // Cab 101 signs in, customers  201 requests for ride
    // then customer 202 requests for ride
    // test PASSED if customer 201 is assigned ride and customer 202 is rejected
    @Test
    public void test2() {
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            Cab.NumRidesResponse resp = cabResetProbe.receiveMessage(Duration.ofSeconds(5));
        }

        printMessage("Cabs reset successful");

        Random rand=new Random();

        EntityRef<Cab.Command> cab101 = sharding.entityRefFor(Cab.TypeKey, "101");
        cab101.tell(new Cab.SignIn(10));
        printMessage("Cab 101 signed in");

        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        String rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert(resp.rideId != -1);
        printMessage("Ride for customer 201 started");


         rsid = "ride-actor-" + (rand.nextInt(12) + 1);
         rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        rideService.tell(new RideService.RequestRide("202", 20, 100, probe.getRef()));
        RideService.RideResponse resp2 = probe.receiveMessage();
        assert(resp2.rideId == -1);
        printMessage("Ride request for customer 202 failed");

        cab101.tell(new Cab.RideEnded(resp.rideId));
        printMessageBig("TEST 2 PASSED");
    }

    // This test checks if concurrent Riderequests are satisfied
    // cab 101,102,103 sign in then customers 201,202,203 request for ride concurrently
    // test PASS if all three get cab
    @Test
    public void test3()
    {
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
        }

        printMessage("Cabs reset successful");

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "101");
        cab.tell(new Cab.SignIn(10));
        printMessage("Cab 101 signed in");

        cab = sharding.entityRefFor(Cab.TypeKey, "102");;
        cab.tell(new Cab.SignIn(10));
        printMessage("Cab 102 signed in");

        cab = sharding.entityRefFor(Cab.TypeKey, "103");
        cab.tell(new Cab.SignIn(10));
        printMessage("Cab 103 signed in");

        TestProbe<RideService.RideResponse> probe1 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> probe2 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> probe3 = testKit.createTestProbe();

        Demo R1 = new Demo(probe1, "201", sharding);
        R1.start();

        Demo R2 = new Demo(probe2, "202", sharding);
        R2.start();

        Demo R3 = new Demo(probe3, "203", sharding);
        R3.start();

        try {
            Thread.sleep(3000);
        }
        catch(Exception e) {
            System.err.println("[ERROR] Some error occured while joining threads");
        }

        assertEquals(3, count1ForTest3 + count2ForTest3 + count3ForTest3);
        printMessageBig("TEST 3 PASSED");
    }

    // This one checks the interest mechanism of cabs
    @Test
    public void test4() {
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            Cab.NumRidesResponse resp = cabResetProbe.receiveMessage(Duration.ofSeconds(5));
        }

        printMessage("Cabs reset successful");

        Random rand=new Random();

        EntityRef<Cab.Command> cab101 = sharding.entityRefFor(Cab.TypeKey, "101");
        cab101.tell(new Cab.SignIn(10));
        printMessage("Cab 101 signed in");

        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        String rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        // Customer 201 requests a ride
        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert(resp.rideId != -1);
        printMessage("Ride for customer 201 started");

        cab101.tell(new Cab.RideEnded(resp.rideId));

        // Customer 202 requests a ride, but cab is not interested
        rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        rideService.tell(new RideService.RequestRide("202", 10, 100, probe.getRef()));
        resp = probe.receiveMessage();
        assert(resp.rideId == -1);
        printMessage("Ride for customer 202 failed, since cab is not interested");

        // Customer 202 requests a ride again, and this time it should be accepted
        rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        rideService.tell(new RideService.RequestRide("202", 10, 100, probe.getRef()));
        resp = probe.receiveMessage();
        assert(resp.rideId != -1);
        printMessage("Ride for customer 202 succeeded on second try");

        printMessageBig("TEST 4 PASSED");
    }
}

class Demo extends Thread {
    private Thread t;

    private String threadid;
    private TestProbe<RideService.RideResponse> threadprobe;
    private ClusterSharding threadsharding;

    Demo(TestProbe<RideService.RideResponse> probe, String id, ClusterSharding sharding) {
        threadprobe = probe;
        threadid = id;
        threadsharding = sharding;
    }

    public void run() {

        Random rand = new Random();
        String rsid = "ride-actor-" + (rand.nextInt(12) + 1);
        EntityRef<Command> rideService = threadsharding.entityRefFor(RideService.TypeKey, rsid);

        rideService.tell(new RideService.RequestRide(threadid, 10, 100, threadprobe.getRef()));
        RideService.RideResponse resp = threadprobe.receiveMessage();
        assert (resp.rideId != -1);

        if (threadid.equals("201"))
            Tests.count1ForTest3 = 1;
        if (threadid.equals("202"))
            Tests.count2ForTest3 = 1;
        if (threadid.equals("203"))
            Tests.count3ForTest3 = 1;

        Tests.printMessage(
                "[Thread " + threadid + "] Ride for customer " + threadid + " started with cab " + resp.cabId);
    }

    public void start() {

        if (t == null) {
            t = new Thread(this, threadid);
            t.start();
        }
    }
}