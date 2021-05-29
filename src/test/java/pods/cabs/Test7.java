package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import static org.junit.Assert.assertEquals;

import org.junit.ClassRule;
import org.junit.Test;
import java.util.Random;

//This test checks if concurrent Riderequests are satisfied
// cab 101,102,103 sign in then customers 201,202,203 request for ride concurrently
//test PASS if all three get cab


public class Test7 {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();


  @Test
  public void test() {
    TestProbe<Main.Started> startedProbe = testKit.createTestProbe();
    ActorRef<Void> underTest = testKit.spawn(Main.create(startedProbe.getRef()), "Main");

    startedProbe.expectMessageClass(Main.Started.class);

    System.out.println("-- RECEIVED STARTED");



        ActorRef<Cab.Command> cab = Globals.cabs.get("101");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED IN");

        cab = Globals.cabs.get("102");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 102 SIGNED IN");

        cab = Globals.cabs.get("103");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 103 SIGNED IN");


         Random rand=new Random();

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
