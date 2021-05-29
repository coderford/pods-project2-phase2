package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

public class Cab extends AbstractBehavior<Cab.Command> {

    private String id;
    private int numRides;
    private CabState state;

    private boolean interested;
    private int rideId;
    private int location;
    private int sourceLoc;
    private int destinationLoc;

    private ActorRef<FulfillRide.Command> fulfillActor;

    public interface Command {}

    /*
     * COMMAND DEFINITIONS
     */
    public static final class RequestRide implements Command {
        final int rideId;
        final int sourceLoc;
        final int destinationLoc;
        final ActorRef<FulfillRide.Command> replyTo;

        public RequestRide(int rideId, int sourceLoc, int destinationLoc,
                           ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static final class RideStarted implements Command {
        final int rideId;
        final ActorRef<FulfillRide.Command> replyTo;

        public RideStarted(int rideId, ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.replyTo = replyTo;
        }
    }

    public static final class RideCancelled implements Command {
        final int rideId;
        final ActorRef<FulfillRide.Command> replyTo;

        public RideCancelled(int rideId, ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.replyTo = replyTo;
        }
    }

    public static final class RideEnded implements Command {
        final int rideId;

        public RideEnded(int rideId) {
            this.rideId = rideId;
        }
    }

    public static final class SignIn implements Command {
        final int initialPos;

        public SignIn(int initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOut implements Command {

    }

    public static final class NumRides implements Command {
        final ActorRef<Cab.NumRidesResponse> replyTo;

        public NumRides(ActorRef<Cab.NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements Command {
        final ActorRef<Cab.NumRidesResponse> replyTo;

        public Reset(ActorRef<Cab.NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /*
     * RESPONSE 
     */
    public static final class NumRidesResponse {
        final int numRides;

        public NumRidesResponse(int numRides) {
            this.numRides = numRides;
        }
    }


    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(String id) {
        return Behaviors.setup(
	        context -> {
                return new Cab(context, id);
	        }
        );
    }

    private Cab(ActorContext<Command> context, String id) {
        super(context);
        this.id = id;
        this.numRides = 0;
        this.state = CabState.SIGNED_OUT;
        this.rideId = -1;
        this.location = 0;
        this.interested = true;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
    }

    /*
     * MESSAGE HANDLING
     */
    @Override
    public Receive<Command> createReceive() {
        ReceiveBuilder<Command> builder = newReceiveBuilder();

        builder.onMessage(RequestRide.class,   this::onRequestRide);
        builder.onMessage(RideStarted.class,   this::onRideStarted);
        builder.onMessage(RideCancelled.class, this::onRideCancelled);
        builder.onMessage(RideEnded.class,     this::onRideEnded);
        builder.onMessage(SignIn.class,        this::onSignIn);
        builder.onMessage(SignOut.class,       this::onSignOut);
        builder.onMessage(NumRides.class,      this::onNumRides);
        builder.onMessage(Reset.class,         this::onReset);

        return builder.build();
    }

    private Behavior<Command> onRequestRide(RequestRide message) {
        if(interested) {
            interested = false;
        } else {
            interested = true;
            message.replyTo.tell(new FulfillRide.RequestRideResponse(false));
            return this;
        }

        // Source and destination location must not be negative
        if(message.sourceLoc < 0 || message.destinationLoc < 0) {
            message.replyTo.tell(new FulfillRide.RequestRideResponse(false));
            return this;
        }

        // Accept ride only if current state is available
        if(state == CabState.AVAILABLE) {
            this.fulfillActor = message.replyTo;
            this.rideId = message.rideId;
            this.state = CabState.COMMITTED;
            this.sourceLoc = message.sourceLoc;
            this.destinationLoc = message.destinationLoc;

            message.replyTo.tell(new FulfillRide.RequestRideResponse(true));
        }
        else {
            message.replyTo.tell(new FulfillRide.RequestRideResponse(false));
        }

        return this;
    }

    private Behavior<Command> onRideStarted(RideStarted message) {
        // Must be comitted to start ride
        if(state != CabState.COMMITTED) {
            message.replyTo.tell(new FulfillRide.RideStartedResponse(false));
            return this;
        }

        state = CabState.GIVING_RIDE;
        location = sourceLoc;
        numRides++;

        message.replyTo.tell(new FulfillRide.RideStartedResponse(true));

        return this;
    }

    private Behavior<Command> onRideCancelled(RideCancelled message) {
        // Can cancel only if cab is committed and valid ride ID was sent
        if(this.state != CabState.COMMITTED || this.rideId != message.rideId) {
            message.replyTo.tell(new FulfillRide.RideCancelledResponse(false));
            return this;
        }

        this.state = CabState.AVAILABLE;
        this.rideId = -1;
        this.sourceLoc = -1;
        this.destinationLoc = -1;

        message.replyTo.tell(new FulfillRide.RideCancelledResponse(true));
        return this;
    }

    private Behavior<Command> onRideEnded(RideEnded message) {
        // Can't end ride if not giving ride, or ride ID is invalid
        if(this.state != CabState.GIVING_RIDE || this.rideId != message.rideId)
            return this;

        this.state = CabState.AVAILABLE;
        this.rideId = -1;
        this.location = this.destinationLoc;
        this.sourceLoc = -1;
        this.destinationLoc = -1;

        this.fulfillActor.tell(new FulfillRide.RideEndedByCab(
            this.id,
            this.rideId,
            this.location
        ));
        return this;
    }

    private Behavior<Command> onSignIn(SignIn message) {
        // Can sign-in only if signed-out and initial position is non-negative
        boolean signInAllowed = (state == CabState.SIGNED_OUT && message.initialPos >= 0);

        if(signInAllowed) {
            // update variables
            state = CabState.AVAILABLE;
            location = message.initialPos;

            // send sign-in message to a random ride service instance
            int randomIndex = (int) (Math.random() * Globals.rideService.size());
            Globals.rideService.get(randomIndex).tell(new RideService.CabSignsIn(
                this.id,
                message.initialPos
            ));
        }

        return this;
    }

    private Behavior<Command> onSignOut(SignOut message) {
        // Cab shouldn't already be signed out, or in giving-ride or committed state
        boolean signOutAllowed = (state != CabState.SIGNED_OUT &&
                                  state != CabState.GIVING_RIDE &&
                                  state != CabState.COMMITTED);

        if(signOutAllowed) {
            // update variables
            state = CabState.SIGNED_OUT;
            location = 0;
            interested = true;
            numRides = 0;

            // send sign-out message to a random ride service instance
            int randomIndex = (int) (Math.random() * Globals.rideService.size());
            Globals.rideService.get(randomIndex).tell(new RideService.CabSignsOut(
                this.id
            ));
        }

        return this;
    }

    private Behavior<Command> onNumRides(NumRides message) {
        message.replyTo.tell(new NumRidesResponse(this.numRides));
        return this;
    }

    private Behavior<Command> onReset(Reset message) {
        message.replyTo.tell(new NumRidesResponse(this.numRides));

        // First, check if currently giving ride. If so, end ride.
        if(this.state == CabState.GIVING_RIDE) {
            this.state = CabState.AVAILABLE;
            this.rideId = -1;
            this.location = this.destinationLoc;
            this.sourceLoc = -1;
            this.destinationLoc = -1;

            this.fulfillActor.tell(new FulfillRide.RideEndedByCab(
                this.id,
                this.rideId,
                this.location
            ));
        }

        // Then, check if signed-in, then sign-out
        if(this.state == CabState.AVAILABLE) {
            // update variables
            state = CabState.SIGNED_OUT;
            location = 0;
            interested = true;
            numRides = 0;

            // send sign-out message to a random ride service instance
            int randomIndex = (int) (Math.random() * Globals.rideService.size());
            Globals.rideService.get(randomIndex).tell(new RideService.CabSignsOut(
                this.id
            ));
        }

        // As a final measure, reset all variables manually
        this.numRides = 0;
        this.state = CabState.SIGNED_OUT;
        this.rideId = -1;
        this.location = 0;
        this.interested = true;
        this.sourceLoc = -1;
        this.destinationLoc = -1;

        return this;
    }
}
