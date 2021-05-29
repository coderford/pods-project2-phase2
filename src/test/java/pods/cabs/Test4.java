package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import org.junit.ClassRule;
import org.junit.Test;
import java.util.Random;

//#This test checks whether ride cancellation works correctly
//cab 101 signs in , customers requests for ride 
//fair is more than balance
//test PASS if ride is cancelled

public class Test4 {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void test2() {
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

        ActorRef<Cab.Command> cab = Globals.cabs.get("101");
        cab.tell(new Cab.SignIn(10));
        System.out.println("CAB 101 SIGNED IN");

        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        ActorRef<RideService.Command> rideService = Globals.rideService.get(rand.nextInt(10));
        rideService.tell(new RideService.RequestRide("201", 10, -100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assert(resp.rideId == -1);

        System.out.println("RIDE FOR CUSTOMER 201 CANCELLED");

        cab.tell(new Cab.RideEnded(resp.rideId));
        System.out.println("TEST 4 PASSED");
    }
}

