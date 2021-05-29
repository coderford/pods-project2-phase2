package pods.cabs;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import pods.cabs.Cab.RideStarted;

public class FulfillRide extends AbstractBehavior<FulfillRide.Command> {
    final HashMap<String, CabData> cabDataMap;
    private List<CabData> cabList;

    private int nextRideId;
    private int requestCount;
    private int nextCabIndex;
    private int fareCalculated;
    private String requestedCabId;

    private FulfillRideRequest origMessage;

    public enum FFState {
        REQ_CABS,
        WAIT_FOR_CAB,
        DEDUCT_AMOUNT,
        WAIT_FOR_RIDE_END,
    }
    private FFState curState;

    public interface Command {}
    public interface Response {}

    /*
     * COMMAND DEFINITIONS
     */
    public static final class FulfillRideRequest implements Command {
        final String custId;
        final int sourceLoc;
        final int destinationLoc;
        final ActorRef<RideService.Command> replyTo;
        final ActorRef<RideService.RideResponse> probe;

        public FulfillRideRequest(
            String custId, 
            int sourceLoc, 
            int destinationLoc,
            ActorRef<RideService.Command> replyTo,
            ActorRef<RideService.RideResponse> probe
        )
        {
            this.custId = custId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
            this.probe = probe;
        }
    }

    public static final class RideEndedByCab implements Command {
        final String cabId;
        final int rideId;
        final int newCabLocation;

        public RideEndedByCab(String cabId, int rideId, int newCabLocation) {
            this.cabId = cabId;
            this.rideId = rideId;
            this.newCabLocation = newCabLocation;
        }
    }

    public static final class RequestRideResponse implements Command {
        final boolean accepted;

        public RequestRideResponse(boolean accepted) {
            this.accepted = accepted;
        }
    }

    public static final class RideStartedResponse implements Command {
        final boolean accepted;

        public RideStartedResponse(boolean accepted) {
            this.accepted = accepted;
        }
    }

    public static final class RideCancelledResponse implements Command {
        final boolean accepted;

        public RideCancelledResponse(boolean accepted) {
            this.accepted = accepted;
        }
    }

    public static final class WrappedResponseBalance implements Command {
        final Wallet.ResponseBalance response;
        
        public WrappedResponseBalance(Wallet.ResponseBalance response) {
            this.response = response;
        }
    }

    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(
        HashMap<String, CabData> cabDataMap
    ) {
        return Behaviors.setup(
	        context -> {
                return new FulfillRide(context, cabDataMap);
	        }
        );
    }

    private FulfillRide(
        ActorContext<Command> context, 
        HashMap<String, CabData> cabDataMap
    ) {
        super(context);
        this.cabDataMap = cabDataMap;
        this.curState = FFState.REQ_CABS;
        this.requestCount = 0;
        this.nextCabIndex = 0;
        this.requestedCabId = "";
    }

    /*
     * MESSAGE HANDLING
     */
    @Override
    public Receive<Command> createReceive() {
        ReceiveBuilder<Command> builder = newReceiveBuilder();

        builder.onMessage(FulfillRideRequest.class, this::onFulfillRideRequest);
        builder.onMessage(RequestRideResponse.class, this::onRequestRideResponse);
        builder.onMessage(WrappedResponseBalance.class, this::onWrappedResponseBalance);
        builder.onMessage(RideEndedByCab.class, this::onRideEndedByCab);
        builder.onMessage(RideStartedResponse.class, this::onRideStartedResponse);
        builder.onMessage(RideCancelledResponse.class, this::onRideCancelledResponse);

        return builder.build();
    }

    private Behavior<Command> onFulfillRideRequest(FulfillRideRequest message) {
        this.origMessage = message;
        // Will try to find an available cab and start a ride
        
        // - first, make a list of cabs and sort them by distance from source
        class CabComparer implements Comparator<CabData>{
            int sourceLoc;
            public CabComparer(int sourceLoc) {
                this.sourceLoc = sourceLoc;
            }

            // Will use this custom comparator for sorting
            public int compare(CabData a, CabData b) {
                return Math.abs(a.location - sourceLoc) - Math.abs(b.location - sourceLoc);
            }
            
        }

        // create cab list
        this.cabList = cabDataMap.values()
                                 .stream()
                                 .sorted(new CabComparer(message.sourceLoc))
                                 .collect(Collectors.toList());

        // - get a new rideId
        this.nextRideId = Globals.getNextRideId();

        // - in the list, send a ride request to the first available ride, then change state
        if(requestCount < 3) requestNextCab();

        // - if no available cab was found, terminate
        if(this.curState != FFState.WAIT_FOR_CAB) {
            message.replyTo.tell(new RideService.RideResponse(
                -1,
                "-1",
                0,
                getContext().getSelf(),
                origMessage.probe
            ));
            return Behaviors.stopped();
        }

        return this;
    }

    private Behavior<Command> onRequestRideResponse(RequestRideResponse message) {
        if(this.curState == FFState.WAIT_FOR_CAB) {
            // process this message only if currently waiting for cab response

            if(message.accepted) {
                getContext().getLog().info("-- Cab " + requestedCabId + " accepted");
                // If the request was accepted, move on to deducting from wallet
                // get customer id and send a deduct request for his wallet
                String custId = this.origMessage.custId;
                int cabLoc = cabDataMap.get(requestedCabId).location;
                int fare = 10*(Math.abs(cabLoc - origMessage.sourceLoc) + Math.abs(origMessage.sourceLoc - origMessage.destinationLoc));
                this.fareCalculated = fare;

                ActorRef<Wallet.ResponseBalance> walletResponseAdapter = getContext().messageAdapter(
                    Wallet.ResponseBalance.class, WrappedResponseBalance::new
                );

                Globals.wallets.get(custId).tell(new Wallet.DeductBalance(
                    fare,
                    walletResponseAdapter
                ));

                // change state
                this.curState = FFState.DEDUCT_AMOUNT;
            }
            else {
                getContext().getLog().info("-- Cab " + requestedCabId + " rejected...");
                // cab did not accept; try and request another cab
                this.curState = FFState.REQ_CABS;
                if(requestCount < 3) requestNextCab();

                // if no available cab was found, terminate
                if(this.curState != FFState.WAIT_FOR_CAB) {
                    getContext().getLog().info("No other ride was found!");
                    origMessage.replyTo.tell(new RideService.RideResponse(
                        -1,
                        "-1",
                        0,
                        getContext().getSelf(),
                        origMessage.probe
                    ));
                    return Behaviors.stopped();
                }
            }
        }
        return this;
    }

    private Behavior<Command> onWrappedResponseBalance(WrappedResponseBalance message) {
        if(message.response.balance < 0) {
            // deduction failed; cancel ride and terminate.
            Globals.cabs.get(cabDataMap.get(requestedCabId).id).tell(new Cab.RideCancelled(
                this.nextRideId,
                getContext().getSelf()
            ));
            origMessage.replyTo.tell(new RideService.RideResponse(
                -1,
                "-1",
                0,
                getContext().getSelf(),
                origMessage.probe
            ));
            return Behaviors.stopped();
        }

        // deduction was successul; start ride, tell parent and wait for rideEnded from cab
        Globals.cabs.get(cabDataMap.get(requestedCabId).id).tell(new Cab.RideStarted(
            this.nextRideId,
            getContext().getSelf()
        ));

        origMessage.replyTo.tell(new RideService.RideResponse(
            nextRideId,
            requestedCabId,
            fareCalculated,
            getContext().getSelf(),
            origMessage.probe
        ));

        this.curState = FFState.WAIT_FOR_RIDE_END;
        return this;
    }

    private Behavior<Command> onRideEndedByCab(RideEndedByCab message) {
        // tell parent
        origMessage.replyTo.tell(new RideService.RideEnded(requestedCabId, message.newCabLocation));
        return this;
    }

    private Behavior<Command> onRideStartedResponse(RideStartedResponse message) {
        return this;
    }

    private Behavior<Command> onRideCancelledResponse(RideCancelledResponse message) {
        return this;
    }

    private void requestNextCab() {
        // This method goes through the cab list (while forwarding the nextCabIndex)
        // and stops after sending request to next available cab
        while(nextCabIndex < cabList.size()) {
            getContext().getLog().info("-- nextCabIndex = " + nextCabIndex);
            CabData c = cabList.get(nextCabIndex);
            nextCabIndex++;

            if(c.state == CabState.AVAILABLE) {
                getContext().getLog().info("Cab " + c.id + " is available");
                requestCount++;
                Globals.cabs.get(c.id).tell(new Cab.RequestRide(
                    nextRideId,
                    origMessage.sourceLoc,
                    origMessage.destinationLoc,
                    this.getContext().getSelf()
                ));

                this.requestedCabId = c.id;
                this.curState = FFState.WAIT_FOR_CAB;
                break;
            }
        }
    }
}

