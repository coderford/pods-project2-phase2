package pods.cabs;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;

import static org.junit.Assert.assertEquals;

import org.junit.ClassRule;
import org.junit.Test;

//This test checks for consistency of wallet balance
//for customer 201 equal balance is added and deducted concurrently multiple times 
//test PASSED if final balance is consistent
public class Test6 {
  @ClassRule
  public static final TestKitJunitResource testKit = new TestKitJunitResource();

  @Test
  public void test() {
    TestProbe<Main.Started> startedProbe = testKit.createTestProbe();
    ActorRef<Void> underTest = testKit.spawn(Main.create(startedProbe.getRef()), "Main");

    startedProbe.expectMessageClass(Main.Started.class);

    System.out.println("-- RECEIVED STARTED");

    TestProbe<Wallet.ResponseBalance> probe = testKit.createTestProbe();

    ThreadDemo R1 = new ThreadDemo(probe, 1);
    R1.start();

    ThreadDemo R2 = new ThreadDemo(probe, 2);
    R2.start();

    ThreadDemo R3 = new ThreadDemo(probe, 3);
    R3.start();

    ThreadDemo R4 = new ThreadDemo(probe, 4);
    R4.start();

    try {
      Thread.sleep(3000);
    } catch (Exception ignore) {}

    ActorRef<Wallet.Command> walletservice = Globals.wallets.get("201");

    walletservice.tell(new Wallet.GetBalance(probe.getRef()));
    Wallet.ResponseBalance resp = probe.receiveMessage();
    System.out.println(resp.balance);
    assertEquals(Globals.initBalance, resp.balance);

    System.out.println("TEST 6 PASSED");
  }
}

class ThreadDemo extends Thread {
  private Thread t;
  private String threadName;
  private int threadid;
  private TestProbe<Wallet.ResponseBalance> threadprobe;

  ThreadDemo(TestProbe<Wallet.ResponseBalance> probe, int id) {
    threadprobe = probe;
    threadid = id;

  }

  public void run() {
    ActorRef<Wallet.Command> walletservice = Globals.wallets.get("201");

    for (int i = 0; i < 10; i++) {
      if (threadid % 2 == 0) {
        walletservice.tell(new Wallet.AddBalance(100));
      }

      else {
        walletservice.tell(new Wallet.DeductBalance(100, threadprobe.getRef()));
        Wallet.ResponseBalance resp = threadprobe.receiveMessage();
        assert (resp.balance != -1);
      }
    }
  }

  public void start() {
 
    if (t == null) {
      t = new Thread(this, Integer.toString(threadid));
      t.start();
    }
  }
}
