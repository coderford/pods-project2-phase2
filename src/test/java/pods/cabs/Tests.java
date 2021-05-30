package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import akka.persistence.typed.PersistenceId;
import pods.cabs.RideService.Command;
import static org.junit.Assert.assertTrue;
import java.util.Random;

import com.typesafe.config.ConfigFactory;

import org.junit.ClassRule;
import org.junit.Test;



public class Tests {
    private static ClusterSharding sharding;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource(ConfigFactory.parseString("akka {\n"
            + "  loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" + "  loglevel = \"DEBUG\"\n"
            + "  logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" + "  actor.provider=\"cluster\"\n"
            + "  actor.allow-java-serialization = on\n" + "  remote.artery.canonical.hostname = \"127.0.0.1\"\n"
            + "  remote.artery.canonical.port = 0\n"
            + "  cluster.seed-nodes = [\"akka://ClusterSystem@127.0.0.1:25251\", \"akka://ClusterSystem@127.0.0.1:25252\"]\n"
            + "  cluster.downing-provider-class= \"akka.cluster.sbr.SplitBrainResolverProvider\"\n"
            + "  persistence.journal.plugin=\"akka.persistence.journal.proxy\"\n"
            + "  persistence.journal.proxy.target-journal-plugin=\"akka.persistence.journal.leveldb\"\n"
            + "  persistence.journal.proxy.target-journal-address = \"akka://ClusterSystem@127.0.0.1:25251\"\n"
            + "  persistence.journal.proxy.start-target-journal = \"off\"\n" + "}"));

    public static void init() {
        Cluster cluster = Cluster.get(testKit.system());
        cluster.manager().tell(Join.create(cluster.selfMember().address()));

        sharding = ClusterSharding.get(testKit.system());
        sharding.init(Entity.of(Cab.TypeKey, entityContext -> Cab.create(entityContext.getEntityId(),
                PersistenceId.of(entityContext.getEntityTypeKey().name(), entityContext.getEntityId()))));

        sharding.init(Entity.of(RideService.TypeKey, entityContext -> RideService.create(entityContext.getEntityId())));
    }

    //can 101 signs in , customer 201 requests ride
// test PASSED if customer 201 is assigned ride
    @Test
    public void test1() {
        init();

        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
        }

        System.out.println("-- CABS RESET SUCCESSFUL");

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "101");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED IN");

        Random rand = new Random();
        String rsid = "ride-actor-" + rand.nextInt(12) + 1;
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert (resp.rideId != -1);
        System.out.println("RIDE FOR CUSTOMER 201 STARTED");

        cab.tell(new Cab.RideEnded(resp.rideId));
        System.out.println("---- TEST 1 PASSED \n \n \n \n \n");
    }


    //cab 101 signs in , customers  201 requests for ride
//then customer 202 requests for ride
//test PASSED if customer 201 is assigned ride and customer 202 is rejected
    @Test
    public void test2() {
        init();

        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
        }

        System.out.println("-- CABS RESET SUCCESSFUL");

        Random rand=new Random();

        EntityRef<Cab.Command> cab101 = sharding.entityRefFor(Cab.TypeKey, "101");
        cab101.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED IN");

        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        String rsid = "ride-actor-" + rand.nextInt(12) + 1;
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert(resp.rideId != -1);
        System.out.println("RIDE FOR CUSTOMER 201 STARTED");


         rsid = "ride-actor-" + rand.nextInt(12) + 1;
         rideService = sharding.entityRefFor(RideService.TypeKey, rsid);

        rideService.tell(new RideService.RequestRide("202", 20, 100, probe.getRef()));
        RideService.RideResponse resp2 = probe.receiveMessage();
        assert(resp2.rideId == -1);
        System.out.println("RIDE REQUEST FOR CUSTOMER 202 FAILED");


        cab101.tell(new Cab.RideEnded(resp.rideId));
        System.out.println("TEST 2 PASSED \n \n\n");

    }

//In this test we check if nearest cab is assigned or not
    @Test
    public void test3()
    {
        init();
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
        }

        System.out.println("-- CABS RESET SUCCESSFUL");

        Random rand=new Random();

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "101");
        cab.tell(new Cab.SignIn(20));
        System.out.println("CAB 101 SIGNED IN AT LOCATION 20");

         cab = sharding.entityRefFor(Cab.TypeKey, "102");
        cab.tell(new Cab.SignIn(30));
        System.out.println("CAB 102 SIGNED IN AT LOCATION 30");

         cab = sharding.entityRefFor(Cab.TypeKey, "103");
        cab.tell(new Cab.SignIn(40));
        System.out.println("CAB 103 SIGNED IN AT LOCATION 40");


        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();
        String rsid = "ride-actor-" + rand.nextInt(12) + 1;
        EntityRef<Command> rideService = sharding.entityRefFor(RideService.TypeKey, rsid);
        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assertTrue("Wrong cab assigned",resp.cabId.equals("101"));
        
        System.out.println("RIDE FOR CUSTOMER 201 STARTED WITH CAB "+resp.cabId);

        System.out.println("TEST 3 PASSED \n \n \n \n");

    }

    //This test checks if concurrent Riderequests are satisfied
// cab 101,102,103 sign in then customers 201,202,203 request for ride concurrently
//test PASS if all three get cab
    @Test
    public void test4()
    {
        init();
        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        for(int i=101;i<=104;i++) 
        {
            EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, Integer.toString(i));
            cab.tell(new Cab.Reset(cabResetProbe.getRef()));
            cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
        }

        System.out.println("-- CABS RESET SUCCESSFUL");

        Random rand=new Random();

        EntityRef<Cab.Command> cab = sharding.entityRefFor(Cab.TypeKey, "101");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED IN");

         cab = sharding.entityRefFor(Cab.TypeKey, "102");;
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 102 SIGNED IN");

         cab = sharding.entityRefFor(Cab.TypeKey, "103");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 103 SIGNED IN");


        TestProbe<RideService.RideResponse> probe1 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> probe2 = testKit.createTestProbe();
        TestProbe<RideService.RideResponse> probe3 = testKit.createTestProbe();


    Demo R1 = new  Demo(probe1, "201");
    R1.start();

    Demo R2 = new  Demo(probe2, "202");
    R2.start();

    Demo R3 = new Demo(probe3, "203");
    R3.start();


    }


}

class Demo extends Thread {
    private Thread t;
    
    private String threadid;
    private TestProbe<RideService.RideResponse> threadprobe;
  
    Demo(TestProbe<RideService.RideResponse> probe, String id) {
      threadprobe = probe;
      threadid = id;
    
  
    }
  
    public void run() {
  
        Random rand=new Random();
          ActorRef<RideService.Command> rideService = Globals.rideService.get(rand.nextInt(10));
  
          rideService.tell(new RideService.RequestRide(threadid, 10, 100, threadprobe.getRef()));
          RideService.RideResponse resp = threadprobe.receiveMessage();
          assert(resp.rideId != -1);
      
          System.out.println("RIDE FOR CUSTOMER "+threadid+" STARTED WITH CAB "+resp.cabId);
    }
  
    public void start() {
   
      if (t == null) {
        t = new Thread(this, threadid);
        t.start();
      }
    }
  }
  
