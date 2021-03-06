package pods.cabs;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.ActorContext;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;

public class Cab extends EventSourcedBehavior<Cab.Command, Cab.CabEvent, Cab.PersistState> {
    private String entityId;

    public static final EntityTypeKey<Command> TypeKey = 
        EntityTypeKey.create(Cab.Command.class, "CabPersistEntity");

    public interface Command extends CborSerializable {}

    /*
     * COMMAND DEFINITIONS
     */
    public static final class RequestRide implements Command {
        int sourceLoc;
        int destinationLoc;
        ActorRef<FulfillRide.Command> replyTo;

        public RequestRide() {
            super();
        }

        public RequestRide(int sourceLoc, int destinationLoc, ActorRef<FulfillRide.Command> replyTo) {
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static final class RideStarted implements Command {
        int rideId;
        ActorRef<FulfillRide.Command> replyTo;

        public RideStarted() {
            super();
        }

        public RideStarted(int rideId, ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.replyTo = replyTo;
        }
    }

    public static final class RideCancelled implements Command {
        int rideId;
        ActorRef<FulfillRide.Command> replyTo;

        public RideCancelled() {
            super();
        }

        public RideCancelled(int rideId, ActorRef<FulfillRide.Command> replyTo) {
            this.rideId = rideId;
            this.replyTo = replyTo;
        }
    }

    public static final class RideEnded implements Command {
        int rideId;

        public RideEnded() {
            super();
        }

        public RideEnded(int rideId) {
            this.rideId = rideId;
        }
    }

    public static final class SignIn implements Command {
        int initialPos;

        public SignIn() {
            super();
        }

        public SignIn(int initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOut implements Command {
        int dummy = 0;
    }

    public static final class NumRides implements Command {
        ActorRef<Cab.NumRidesResponse> replyTo;
        
        public NumRides() {
            super();
        }

        public NumRides(ActorRef<Cab.NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Reset implements Command {
        ActorRef<Cab.NumRidesResponse> replyTo;

        public Reset() {
            super();
        }

        public Reset(ActorRef<Cab.NumRidesResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /*
     * RESPONSE
     */
    public static final class NumRidesResponse implements CborSerializable {
        int numRides;

        public NumRidesResponse() {
            super();
        }

        public NumRidesResponse(int numRides) {
            this.numRides = numRides;
        }
    }

    /*
     * EVENTS
     */
    interface CabEvent extends CborSerializable {
    }

    public static final class RequestRideEvent implements CabEvent {
        int dummy = 0;
        int sourceLoc;
        int destinationLoc;
        ActorRef<FulfillRide.Command> replyTo;

        public RequestRideEvent() {
            super();
        }

        public RequestRideEvent(int sourceLoc, int destinationLoc, ActorRef<FulfillRide.Command> replyTo) {
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    
    }

    public static final class SignInEvent implements CabEvent {
        int dummy = 0;
        int initialPos;

        public SignInEvent() {
            super();
        }

        public SignInEvent(int initialPos) {
            this.initialPos = initialPos;
        }
    }

    public static final class SignOutEvent implements CabEvent {
        int dummy = 0;
    }

    public static final class RideStartedEvent implements CabEvent {
        int dummy = 0;
    }

    public static final class RideCancelledEvent implements CabEvent {
        int dummy = 0;
        int rideId;

        public RideCancelledEvent() {
            super();
        }

        public RideCancelledEvent(int rideId) {
            this.rideId = rideId;
        }
    }

    public static final class RideEndedEvent implements CabEvent {
        int dummy = 0;
        int rideId;
        
        public RideEndedEvent() {
            super();
        }

        public RideEndedEvent(int rideId) {
            this.rideId = rideId;
        }
    }

    public static final class ResetEvent implements CabEvent {
        int dummy = 0;
    }

    // State
    static final class PersistState implements CborSerializable {
        public String id;
        public int numRides;
        public CabState status;

        public boolean interested;
        public int rideId;
        public int nextRideId;
        public int location;
        public int sourceLoc;
        public int destinationLoc;

        public ActorRef<FulfillRide.Command> fulfillActor;

        // These are not-mandatory variables that help in passing information
        // between event handler and command handler
        public boolean rideWasEndedOnReset;
        public boolean rideRequestAccepted;
        public boolean rideStartedSuccessful;
        public boolean rideCancelledSuccessful;

        public PersistState(String id) {
            this.id = id;
            this.numRides = 0;
            this.status = CabState.SIGNED_OUT;
            this.rideId = -1;
            this.nextRideId = Integer.parseInt(this.id)*10000 + 1;
            this.location = 0;
            this.interested = true;
            this.sourceLoc = -1;
            this.destinationLoc = -1;
            this.rideWasEndedOnReset = false;
            this.rideRequestAccepted = false;
            this.rideStartedSuccessful = false;
            this.rideCancelledSuccessful = false;
        }
    }

    @Override
    public PersistState emptyState() {
        return new PersistState(this.entityId);
    }

    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(String entityId, PersistenceId persistenceId) {
        return Behaviors.setup(context -> {
            return new Cab(context, persistenceId, entityId);
        });
    }

    private Cab(ActorContext<Command> context, PersistenceId persistenceId, String entityId) {
        super(persistenceId);
        this.entityId = entityId;
    }

    /*
     * COMMAND HANDLERS
     */

    @Override
    public CommandHandler<Command, CabEvent, PersistState> commandHandler() {
        return newCommandHandlerBuilder().forAnyState().onCommand(RequestRide.class, this::onRequestRide)
                .onCommand(RideStarted.class, this::onRideStarted).onCommand(RideCancelled.class, this::onRideCancelled)
                .onCommand(RideEnded.class, this::onRideEnded).onCommand(SignIn.class, this::onSignIn)
                .onCommand(SignOut.class, this::onSignOut).onCommand(NumRides.class, this::onNumRides)
                .onCommand(Reset.class, this::onReset).build();
    }

    private Effect<CabEvent, PersistState> onRequestRide(RequestRide message) {
        return Effect().persist(new RequestRideEvent(
            message.sourceLoc,
            message.destinationLoc,
            message.replyTo
        )).thenRun(
            newState -> {
                if(newState.rideRequestAccepted) {
                    message.replyTo.tell(new FulfillRide.RequestRideResponse(true, newState.rideId));
                } else {
                    message.replyTo.tell(new FulfillRide.RequestRideResponse(false, newState.rideId));
                }
            }
        );
    }

    private Effect<CabEvent, PersistState> onRideStarted(RideStarted message) {
        return Effect().persist(new RideStartedEvent()).thenRun(
            newState -> {
                if(newState.rideStartedSuccessful)
                    message.replyTo.tell(new FulfillRide.RideStartedResponse(false));
                else
                    message.replyTo.tell(new FulfillRide.RideStartedResponse(true));
            }
        );
    }

    private Effect<CabEvent, PersistState> onRideCancelled(RideCancelled message) {
        return Effect().persist(new RideCancelledEvent(message.rideId)).thenRun(
            newState -> {
                if(newState.rideCancelledSuccessful) {
                    message.replyTo.tell(new FulfillRide.RideCancelledResponse(false));
                } else {
                    message.replyTo.tell(new FulfillRide.RideCancelledResponse(true));
                }
            }
        );
    }

    private Effect<CabEvent, PersistState> onRideEnded(RideEnded message) {
        return Effect().persist(new RideEndedEvent(message.rideId)).thenRun(
            newState -> {
                newState.fulfillActor.tell(new FulfillRide.RideEndedByCab(
                    newState.id, 
                    newState.rideId, 
                    newState.location
                ));
            }
        );
    }

    private Effect<CabEvent, PersistState> onSignIn(SignIn message) {
        return Effect().persist(new SignInEvent(message.initialPos)).thenRun(
            newState -> { }
        );
    }

    private Effect<CabEvent, PersistState> onSignOut(SignOut message) {
        return Effect().persist(new SignOutEvent()).thenRun(
            newState -> { }
        );
    }

    private Effect<CabEvent, PersistState> onNumRides(NumRides message) {
        return Effect().none().thenRun(
            newState -> message.replyTo.tell(new NumRidesResponse(newState.numRides))
        );
    }

    private Effect<CabEvent, PersistState> onReset(Reset message) {
        System.out.println("\n=== RESET EVENT RECEIVED ===\n");
        return Effect().persist(new ResetEvent()).thenRun(
            newState -> {
                if(newState.rideWasEndedOnReset)
                    newState.fulfillActor.tell(new FulfillRide.RideEndedByCab(
                        newState.id, 
                        newState.rideId, 
                        newState.location
                    ));
                message.replyTo.tell(new NumRidesResponse(newState.numRides));
                System.out.println("\n=== replyTo is "+message.replyTo + " ===\n");
                System.out.println("\n=== Resetting cab " + this.entityId + " ===\n");
            }
        );
    }

    /*
     * EVENT HANDLER
     */

    @Override
    public EventHandler<PersistState, CabEvent> eventHandler() {
        return newEventHandlerBuilder().forAnyState()
        .onEvent(SignInEvent.class, (state, evt) -> {
            // Can sign-in only if signed-out and initial position is non-negative
            boolean signInAllowed = (state.status == CabState.SIGNED_OUT && evt.initialPos >= 0);

            if (signInAllowed) {
                // update variables
                state.status = CabState.AVAILABLE;
                state.location = evt.initialPos;
            }

            return state;
        })
        .onEvent(SignOutEvent.class, (state, evt) -> {
            // Cab shouldn't already be signed out, or in giving-ride or committed state
            boolean signOutAllowed = (
                   state.status != CabState.SIGNED_OUT 
                && state.status != CabState.GIVING_RIDE
                && state.status != CabState.COMMITTED
            );

            if (signOutAllowed) {
                // update variables
                state.status = CabState.SIGNED_OUT;
                state.location = 0;
                state.interested = true;
                state.numRides = 0;
            }

            return state;
        })
        .onEvent(RequestRideEvent.class, (state,evt) -> {
            state.rideRequestAccepted = false;

            if(state.interested) {
                state.interested = false;
            } else {
                state.interested = true;
                return state;
            }

            // Source and destination location must not be negative
            if(evt.sourceLoc < 0 || evt.destinationLoc < 0) {
                return state;
            }

            // Accept ride only if current state is available
            if(state.status == CabState.AVAILABLE) {
                state.fulfillActor = evt.replyTo;
                state.rideId = state.nextRideId;
                state.nextRideId += 1;
                state.status = CabState.COMMITTED;
                state.sourceLoc = evt.sourceLoc;
                state.destinationLoc = evt.destinationLoc;
                state.rideRequestAccepted = true;
            }

            return state;
        })
        .onEvent(RideStartedEvent.class, (state, evt) -> {
            state.rideStartedSuccessful = false;
            if (state.status != CabState.COMMITTED) {
                return state;
            }

            state.status = CabState.GIVING_RIDE;
            state.location = state.sourceLoc;
            state.numRides++;
            state.rideStartedSuccessful = true;

            return state;
        })
        .onEvent(RideEndedEvent.class, (state, evt) -> {
            // Can't end ride if not giving ride, or ride ID is invalid
            if (state.status != CabState.GIVING_RIDE || state.rideId != evt.rideId)
                return state;

            state.status = CabState.AVAILABLE;
            state.rideId = -1;
            state.location = state.destinationLoc;
            state.sourceLoc = -1;
            state.destinationLoc = -1;

            return state;
        })
        .onEvent(RideCancelledEvent.class, (state, evt) -> {
            state.rideCancelledSuccessful = false;
            // Cannot cancel if not committed or ride id does not match
            if (state.status != CabState.COMMITTED || state.rideId != evt.rideId) {
                return state;
            }

            state.status = CabState.AVAILABLE;
            state.rideId = -1;
            state.sourceLoc = -1;
            state.destinationLoc = -1;
            state.rideCancelledSuccessful = true;

            return state;
        })
        .onEvent(ResetEvent.class, (state, evt) -> {
            state.rideWasEndedOnReset = false;

            // First, check if currently giving ride. If so, end ride.
            if(state.status == CabState.GIVING_RIDE) {
                state.status = CabState.AVAILABLE;
                state.rideId = -1;
                state.location = state.destinationLoc;
                state.sourceLoc = -1;
                state.destinationLoc = -1;

                state.rideWasEndedOnReset = true;
            }

            // Then, check if signed-in, then sign-out
            if(state.status == CabState.AVAILABLE) {
                // update variables
                state.status = CabState.SIGNED_OUT;
                state.location = 0;
                state.interested = true;
                state.numRides = 0;
            }

            // As a final measure, reset all variables manually
            state.numRides = 0;
            state.status = CabState.SIGNED_OUT;
            state.rideId = -1;
            state.location = 0;
            state.interested = true;
            state.sourceLoc = -1;
            state.destinationLoc = -1;

            return state;
        }).build();
    }
}
