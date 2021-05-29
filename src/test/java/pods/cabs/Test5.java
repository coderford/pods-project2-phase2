package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import static org.junit.Assert.assertTrue;

import org.junit.ClassRule;
import org.junit.Test;
import java.util.*;

//In this test we check if nearest cab is assigned or not

public class Test5 {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Test
    public void test() {
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
        cab.tell(new Cab.SignIn(20));
        System.out.println("CAB 101 SIGNED IN AT LOCATION 20");

        cab = Globals.cabs.get("102");
        cab.tell(new Cab.SignIn(30));
        System.out.println("CAB 102 SIGNED IN AT LOCATION 30");

        cab = Globals.cabs.get("103");
        cab.tell(new Cab.SignIn(40));
        System.out.println("CAB 103 SIGNED IN AT LOCATION 40");


        
        TestProbe<RideService.RideResponse> probe = testKit.createTestProbe();

        ActorRef<RideService.Command> rideService = Globals.rideService.get(rand.nextInt(10));
        rideService.tell(new RideService.RequestRide("201", 10, 100, probe.getRef()));
        RideService.RideResponse resp = probe.receiveMessage();
        assertTrue("Wrong cab assigned",resp.cabId.equals("101"));
        
        System.out.println("RIDE FOR CUSTOMER 201 STARTED WITH CAB "+resp.cabId);

        System.out.println("TEST 5 PASSED");
    }
}

