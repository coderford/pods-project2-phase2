package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import java.util.Random;

import org.junit.ClassRule;
import org.junit.Test;


//This Testcase checks whether 
//multiple rides are assigned to multiple customers 
//cab 101,102 sign in , customer 201 requests ride then customer 202 requests ride 
//test PASS if both rides are assigned


public class Test3 {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void test3() {
        TestProbe<Main.Started> startedProbe = testKit.createTestProbe();
        ActorRef<Void> underTest = testKit.spawn(Main.create(startedProbe.getRef()), "Main");

        startedProbe.expectMessageClass(Main.Started.class);

        System.out.println("-- RECEIVED STARTED");

        TestProbe<Cab.NumRidesResponse> cabResetProbe = testKit.createTestProbe();
        Globals.cabs.values().forEach(
            cab -> {
                cab.tell(new Cab.Reset(cabResetProbe.getRef()));
                cabResetProbe.expectMessageClass(Cab.NumRidesResponse.class);
            }
        );

        System.out.println("-- CABS RESET SUCCESSFUL");

        TestProbe<Wallet.ResponseBalance> walletTestProbe = testKit.createTestProbe();
        Globals.wallets.values().forEach(
            wallet -> {
                wallet.tell(new Wallet.Reset(walletTestProbe.getRef()));
                walletTestProbe.expectMessageClass(Wallet.ResponseBalance.class);
            }
        );

        System.out.println("-- WALLETS RESET SUCCESSFUL");


        Random rand=new Random();

        ActorRef<Cab.Command> cab101 = Globals.cabs.get("101");
        cab101.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED In");

        ActorRef<Cab.Command> cab102 = Globals.cabs.get("102");
        cab102.tell(new Cab.SignIn(20));
        System.out.println("CAB 102 SIGNED In");

        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        ActorRef<RideService.Command> rideService = Globals.rideService.get(rand.nextInt(10));
        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp1 = probe.receiveMessage();
        assert(resp1.rideId != -1);
        System.out.println("RIDE FOR CUSTOMER 201 STARTED");

        rideService = Globals.rideService.get(rand.nextInt(10));
        rideService.tell(new RideService.RequestRide("202", 20, 100, probe.getRef()));
        RideService.RideResponse resp2 = probe.receiveMessage();
        assert(resp2.rideId != -1);
        System.out.println("RIDE FOR CUSTOMER 202 STARTED"); 
        
        ActorRef<Cab.Command> cab = Globals.cabs.get(resp1.cabId);
        cab.tell(new Cab.RideEnded(resp1.rideId));

        cab = Globals.cabs.get(resp2.cabId);
        cab.tell(new Cab.RideEnded(resp2.rideId));

        System.out.println("TEST 3  PASSED");
    }
}

