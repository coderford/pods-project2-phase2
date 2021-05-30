package pods.cabs;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ActorContext;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.CommandHandlerBuilder;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;

import com.fasterxml.jackson.annotation.JsonCreator;

public class Cab extends EventSourcedBehavior<Cab.Command, Cab.CabEvent, Cab.PersistState> {

    private int numRides;
    private CabState state;

    private boolean interested;
    private int rideId;
    private int location;
    private int sourceLoc;
    private int destinationLoc;
    

    private ActorRef<FulfillRide.Command> fulfillActor;

    public static final EntityTypeKey<Command> TypeKey =
    EntityTypeKey.create(Cab.Command.class, "CabPersistEntity");

    public interface Command extends CborSerializable  {}

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



    // Event. 
	interface CabEvent extends CborSerializable {}

    public static final class RequestRideEvent implements CabEvent {
		int dummy=0;
	}
	
    public static final class SignInEvent implements CabEvent {
		int dummy=0;
	}

    public static final class SignOutEvent implements CabEvent {
		int dummy=0;
	}

    public static final class RideStartedEvent implements CabEvent {
		int dummy=0;
	}

    public static final class RideCancelledEvent implements CabEvent {
		int dummy=0;
	}

    public static final class RideEndedEvent implements CabEvent {
		int dummy=0;
	}

    public static final class ResetEvent implements CabEvent {
		int dummy=0;
	}


    // State
	static final class PersistState implements CborSerializable {
    public int numRides;
    public CabState state;

    public boolean interested;
    public int rideId;
    public int location;
    public int sourceLoc;
    public int destinationLoc;
	}
	
    @Override
	public PersistState emptyState() {
		return new PersistState();
	}


    @Override
	public CommandHandler<Command, CabEvent, PersistState> commandHandler() {
        return newCommandHandlerBuilder().forAnyState()
        .onCommand(RequestRide.class,   this::onRequestRide)
        .onCommand(RideStarted.class,   this::onRideStarted)
        .onCommand(RideCancelled.class, this::onRideCancelled)
        .onCommand(RideEnded.class,     this::onRideEnded)
        .onCommand(SignIn.class,        this::onSignIn)
        .onCommand(SignOut.class,       this::onSignOut)
        .onCommand(NumRides.class,      this::onNumRides)
        .onCommand(Reset.class,         this::onReset)
        .build();
	}

    //Event Handler

    @Override
	public EventHandler<PersistState, CabEvent> eventHandler() {
		return newEventHandlerBuilder().forAnyState()

				.onEvent(SignInEvent.class, (state, evt) -> {state.state=CabState.AVAILABLE; return state;})

                .onEvent(SignOutEvent.class, (state, evt) -> {state.state=CabState.SIGNED_OUT; return state;})

                .onEvent(RideStartedEvent.class, (state, evt) -> 
                {
                    state.state = CabState.GIVING_RIDE;
                    state.location = sourceLoc;
                    state.numRides++;
                    return state;}  )

                .onEvent(RideEndedEvent.class, (state, evt) -> 
                {
                    state.state = CabState.GIVING_RIDE;
                    state.location = sourceLoc;
                    state.numRides++;
                    return state;}  )

                .onEvent(RideCancelledEvent.class, (state, evt) -> 
                {
                    state.state = CabState.AVAILABLE;
                       state.rideId = -1;
                     state.sourceLoc = -1;
                      state.destinationLoc = -1;}  )
                
              .onEvent(ResetEvent.class, (state, evt) -> 
              {
               state.numRides = 0;
               state.state = CabState.SIGNED_OUT;
               state.rideId = -1;
               state.location = 0;
               state.interested = true;
               state.sourceLoc = -1;
               state.destinationLoc = -1;}  )

				.build();
	}



	


    

   


    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(String EntityId,PersistenceId persistenceId) {
        return Behaviors.setup(
	        context -> {
                return new Cab(context,persistenceId);
	        }
        );
    }

    private Cab(ActorContext<Command> context,PersistenceId persistenceId) {
        super(persistenceId);
        this.numRides = 0;
        this.state = CabState.SIGNED_OUT;
        this.rideId = -1;
        this.location = 0;
        this.interested = true;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
    }

    

    private Effect<CabEvent,PersistState> onRequestRide(RequestRide message) {
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

    private Effect<CabEvent,PersistState> onRideStarted(RideStarted message) {
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

    private Effect<CabEvent,PersistState> onRideCancelled(RideCancelled message) {
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

    private Effect<CabEvent,PersistState> onRideEnded(RideEnded message) {
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

    private Effect<CabEvent,PersistState> onSignIn(SignIn message) {
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

    private Effect<CabEvent,PersistState>onSignOut(SignOut message) {
        // Cab shouldn't already be signed out, or in giving-ride or committed state
        boolean signOutAllowed = (state != CabState.SIGNED_OUT &&
                                  state != CabState.GIVING_RIDE &&
                                  state != CabState.COMMITTED);

        if(signOutAllowed) {
            // update variables
           return Effect().persist(new SignOutEvent())
            .thenRun(newState -> 
            {
                System.out.println("reseting cab " + this.persistenceId() );
                // send sign-out message to a random ride service instance
            int randomIndex = (int) (Math.random() * Globals.rideService.size());
            Globals.rideService.get(randomIndex).tell(new RideService.CabSignsOut(
                this.id));
            }
                    );

            
        }

        return Effect().none()
				.thenRun(newState -> 
							System.out.println("SignIn Failed"));
    }

    private Effect<CabEvent,PersistState> onNumRides(NumRides message) {
        
        return Effect().none()
        .thenRun(newState -> 
        message.replyTo.tell(new NumRidesResponse(this.numRides)));
    }

    private Effect<CabEvent,PersistState> onReset(Reset message) {
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
        

        return Effect().persist(new ResetEvent())
				.thenRun(newState -> 
						System.out.println("resetting cab " + this.persistenceId() ));
    }
}
