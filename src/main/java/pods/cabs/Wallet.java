package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class Wallet extends AbstractBehavior<Wallet.Command> {
    private int balance;
    private int initialBalance;
    private String id;

    public interface Command {}

    /*
     * COMMAND DEFINITIONS
     */

    public static final class GetBalance implements Command {
        final ActorRef<Wallet.ResponseBalance> replyTo;

        public GetBalance(ActorRef<Wallet.ResponseBalance> replyTo) {

            this.replyTo = replyTo;
        }
    }

    public static final class DeductBalance implements Command {
        final ActorRef<Wallet.ResponseBalance> replyTo;
        final int toDeduct;

        public DeductBalance(int toDeduct, ActorRef<Wallet.ResponseBalance> replyTo) {

            this.toDeduct = toDeduct;
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements Command {
        final ActorRef<Wallet.ResponseBalance> replyTo;

        public Reset(ActorRef<Wallet.ResponseBalance> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AddBalance implements Command {
        final int balance;

        public AddBalance(int toAdd) {
            this.balance = toAdd;
        }
    }

    /*
     * RESPONSE DEFINITION
     */
    public static final class ResponseBalance {
        int balance;

        public ResponseBalance(int balance) {
            this.balance = balance;
        }
    }

    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(String id, int balance) {
        return Behaviors.setup(context -> {
            return new Wallet(context, id, balance);
        });
    }

    private Wallet(ActorContext<Command> context, String id, int balance) {
        super(context);
        this.id = id;
        this.balance = balance;
        this.initialBalance = balance;
    }

    /*
     * MESSAGE HANDLING
     */

    @Override
    public Receive<Command> createReceive() {
        ReceiveBuilder<Command> builder = newReceiveBuilder();

        builder.onMessage(GetBalance.class, this::onGetBalance);
        builder.onMessage(DeductBalance.class, this::onDeductBalance);
        builder.onMessage(AddBalance.class, this::onAddBalance);
        builder.onMessage(Reset.class, this::onReset);

        return builder.build();
    }

    private Behavior<Command> onGetBalance(GetBalance message) {
        message.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

    private Behavior<Command> onDeductBalance(DeductBalance message) {
        if (this.balance - message.toDeduct < 0 || message.toDeduct < 0) {
            message.replyTo.tell(new ResponseBalance(-1));
            return this;
        }

        this.balance -= message.toDeduct;
        message.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

    private Behavior<Command> onAddBalance(AddBalance message) {
        if(message.balance < 0) return this;

        this.balance += message.balance;
        return this;
    }

    private Behavior<Command> onReset(Reset message) {
        this.balance = initialBalance;
        message.replyTo.tell(new ResponseBalance(this.balance));
        return this;
    }

}
