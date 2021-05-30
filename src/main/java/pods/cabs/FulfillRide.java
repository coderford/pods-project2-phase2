package pods.cabs;

import java.io.File;
import java.util.List;
import java.util.Scanner;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;

public class FulfillRide extends AbstractBehavior<FulfillRide.Command> {
    private ClusterSharding sharding;
    private List<String> cabList;

    private int nextRideId;
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
        final int rideId;

        public RequestRideResponse(boolean accepted, int rideId) {
            this.accepted = accepted;
            this.rideId = rideId;
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

    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create() {
        return Behaviors.setup(
	        context -> {
                return new FulfillRide(context);
	        }
        );
    }

    private FulfillRide(ActorContext<Command> context) {
        super(context);
        this.curState = FFState.REQ_CABS;
        this.nextCabIndex = 0;
        this.requestedCabId = "";

        this.sharding = ClusterSharding.get(this.getContext().getSystem());

        populateCabList();
    }

    /*
     * MESSAGE HANDLING
     */
    @Override
    public Receive<Command> createReceive() {
        ReceiveBuilder<Command> builder = newReceiveBuilder();

        builder.onMessage(FulfillRideRequest.class, this::onFulfillRideRequest);
        builder.onMessage(RequestRideResponse.class, this::onRequestRideResponse);
        builder.onMessage(RideEndedByCab.class, this::onRideEndedByCab);
        builder.onMessage(RideStartedResponse.class, this::onRideStartedResponse);
        builder.onMessage(RideCancelledResponse.class, this::onRideCancelledResponse);

        return builder.build();
    }

    private Behavior<Command> onFulfillRideRequest(FulfillRideRequest message) {
        this.origMessage = message;
        // Will try to find an available cab and start a ride
        
        // - in the list, send a ride request to the first available ride, then change state
        requestNextCab();

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
                // Send ride started, and send ride response to ride service
                this.nextRideId = message.rideId;

                sharding.entityRefFor(Cab.TypeKey, requestedCabId).tell(new Cab.RideStarted(
                    this.nextRideId,
                    getContext().getSelf()
                ));

                origMessage.replyTo.tell(new RideService.RideResponse(
                    message.rideId,
                    requestedCabId,
                    fareCalculated,
                    getContext().getSelf(),
                    origMessage.probe
                ));

                // don't wait for ride end, stop now
                return Behaviors.stopped();
            }
            else {
                // cab did not accept; try and request another cab
                this.curState = FFState.REQ_CABS;
                requestNextCab();

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
            String cid = cabList.get(nextCabIndex);
            nextCabIndex++;

            sharding.entityRefFor(Cab.TypeKey, cid).tell(new Cab.RequestRide(
                origMessage.sourceLoc,
                origMessage.destinationLoc,
                this.getContext().getSelf()
            ));

            this.requestedCabId = cid;
            this.curState = FFState.WAIT_FOR_CAB;
            break;
        }
    }

    private void populateCabList() {
        try {
            File inputFile = new File("IDs.txt");
            Scanner in = new Scanner(inputFile);

            int section = 0;
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.compareTo("****") == 0) {
                    section++;
                } else if (section == 1) {
                    this.cabList.add(line);
                } 
            }

            in.close();
        } catch (Exception e) {
            System.out.println("ERROR: Could not read input file!");
        }
    }
}

