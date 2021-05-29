package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import java.util.HashMap;

public class RideService extends AbstractBehavior<RideService.Command> {
    // CabData HashMap
    private HashMap<String, CabData> cabDataMap;
    int fulfillSpawnCount = 0;

    public interface Command {
    }

    /*
     * COMMAND DEFINITIONS
     */

    public static final class CabSignsIn implements Command {
        final String cabId;
        final int initialPos;

        public CabSignsIn(String cabId, int initialPos) {

            this.cabId = cabId;
            this.initialPos = initialPos;
        }
    }

    public static final class CabSignsOut implements Command {
        final String cabId;

        public CabSignsOut(String cabId) {
            this.cabId = cabId;
        }
    }

    public static final class RequestRide implements Command {
        final String custId;
        final int sourceLoc;
        final int destinationLoc;
        final ActorRef<RideService.RideResponse> replyTo;

        public RequestRide(String custId, int sourceLoc, int destinationLoc, ActorRef<RideService.RideResponse> replyTo) {
            this.custId = custId;
            this.sourceLoc = sourceLoc;
            this.destinationLoc = destinationLoc;
            this.replyTo = replyTo;
        }
    }

    public static final class RideEnded implements Command {
        final String cabId;
        final int newCabLocation;

        public RideEnded(String cabId, int newCabLocation) {
            this.cabId = cabId;
            this.newCabLocation = newCabLocation;
        }
    }

    public static final class CabUpdate implements Command {
        final String cabId;
        final CabData cabData;
        final int timestamp;

        public CabUpdate(String cabId, CabData cabData, int timestamp) {
            this.cabId = cabId;
            this.cabData = cabData;
            this.timestamp = timestamp;
        }
    }

    public static final class Reset implements Command {

    }

    /*
     * RESPONSE
     */

    public static final class RideResponse implements Command {
        final int rideId;
        final String cabId;
        final int fare;
        final ActorRef<FulfillRide.Command> fRide;
        final ActorRef<RideService.RideResponse> probe;

        public RideResponse(
            int rideId, 
            String cabId, 
            int fare, 
            ActorRef<FulfillRide.Command> fRide,
            ActorRef<RideService.RideResponse> probe
        ) {

            this.rideId = rideId;
            this.cabId = cabId;
            this.fare = fare;
            this.fRide = fRide;
            this.probe = probe;
        }
    }

    /*
     * INITIALIZATION
     */
    public static Behavior<Command> create(HashMap<String, CabData> cabDataMap) {
        return Behaviors.setup(context -> {
            return new RideService(context, cabDataMap);
        });
    }

    private RideService(ActorContext<Command> context, HashMap<String, CabData> CabDataMap) {
        super(context);
        this.cabDataMap = CabDataMap;
        this.fulfillSpawnCount = 0;
    }

    /*
     * MESSAGE HANDLING
     */
    @Override
    public Receive<Command> createReceive() {
        ReceiveBuilder<Command> builder = newReceiveBuilder();

        builder.onMessage(RequestRide.class, this::onRequestRide);
        builder.onMessage(CabSignsIn.class, this::onCabSignsIn);
        builder.onMessage(CabSignsOut.class, this::onCabSignsOut);
        builder.onMessage(RideResponse.class, this::onRideResponse);
        builder.onMessage(RideEnded.class, this::onRideEnded);
        builder.onMessage(Reset.class, this::onReset);
        builder.onMessage(CabUpdate.class, this::onCabUpdate);

        return builder.build();
    }

    private Behavior<Command> onRequestRide(RequestRide message) {
        getContext().getLog().info("-- RideService: received ride request for custtomer " + message.custId);
        
        // Spawn a new FulfillRide actor, with a unique name
        fulfillSpawnCount++;
        String name = this.toString() + "-ff" + fulfillSpawnCount;
        ActorRef<FulfillRide.Command> fulfillActor = getContext().spawn(FulfillRide.create(cabDataMap), name);

        // Forward request to spawned FulfillRide
        fulfillActor.tell(new FulfillRide.FulfillRideRequest(
                message.custId, 
                message.sourceLoc, 
                message.destinationLoc,
                getContext().getSelf(),
                message.replyTo
        ));

        return this;
    }

    private Behavior<Command> onCabSignsIn(CabSignsIn message) {
        // Update cached cab state
        cabDataMap.get(message.cabId).state = CabState.AVAILABLE;

        // Send update messages to all other ride services
        int timestamp = Globals.getUpdateTimestamp();
        for(int i = 0; i < Globals.rideService.size(); i++) {
            if(!Globals.rideService.get(i).equals(getContext().getSelf()))
            Globals.rideService.get(i).tell(new RideService.CabUpdate(
                message.cabId, 
                this.cabDataMap.get(message.cabId), 
                timestamp
            ));
            else {
                getContext().getLog().info("Not sending sign-in update to ride service " + i);
            }
        }

        return this;
    }

    private Behavior<Command> onCabSignsOut(CabSignsOut message) {
        // Update cached state
        cabDataMap.get(message.cabId).state = CabState.SIGNED_OUT;

        // Send update messages to all other ride services
        int timestamp = Globals.getUpdateTimestamp();
        for(int i = 0; i < Globals.rideService.size(); i++) {
            if(!Globals.rideService.get(i).equals(getContext().getSelf()))
            Globals.rideService.get(i).tell(new RideService.CabUpdate(
                message.cabId, 
                this.cabDataMap.get(message.cabId), 
                timestamp
            ));
            else {
                getContext().getLog().info("Not sending sign-in update to ride service " + i);
            }
        }

        return this;
    }

    private Behavior<Command> onRideResponse(RideResponse message) {
        // Ride request was successful, update cache and send update messages to other ride services
        if(!message.cabId.equals("-1")) {
            cabDataMap.get(message.cabId).rideId = message.rideId;
            cabDataMap.get(message.cabId).state = CabState.GIVING_RIDE;
            
            int timestamp = Globals.getUpdateTimestamp();
            for(int i = 0; i < Globals.rideService.size(); i++) {
                if(!Globals.rideService.get(i).equals(getContext().getSelf()))
                Globals.rideService.get(i).tell(new RideService.CabUpdate(
                    message.cabId, 
                    this.cabDataMap.get(message.cabId), 
                    timestamp
                ));
                else {
                    getContext().getLog().info("Not sending update to ride service " + i);
                }
            }
        }

        // Send message to testProbe about the ride response
        message.probe.tell(message);
        return this;
    }

    private Behavior<Command> onRideEnded(RideEnded message) {
        // Update cache
        cabDataMap.get(message.cabId).state = CabState.AVAILABLE;
        cabDataMap.get(message.cabId).rideId = -1;
        cabDataMap.get(message.cabId).location = message.newCabLocation;

        // Send update messages to other ride service actors
        int timestamp = Globals.getUpdateTimestamp();
        for(int i = 0; i < Globals.rideService.size(); i++) {
            if(!Globals.rideService.get(i).equals(getContext().getSelf()))
            Globals.rideService.get(i).tell(new RideService.CabUpdate(
                message.cabId, 
                this.cabDataMap.get(message.cabId), 
                timestamp
            ));
            else {
                getContext().getLog().info("Not sending update to ride service " + i);
            }
        }
        return this;
    }

    private Behavior<Command> onCabUpdate(CabUpdate message) {
        // Update only if timestamp of update is more recent then currently stored timestamp
        if(message.timestamp > cabDataMap.get(message.cabId).timestamp)  {
            cabDataMap.put(message.cabId, message.cabData);
            cabDataMap.get(message.cabId).timestamp = message.timestamp;
        }
        return this;
    }

    private Behavior<Command> onReset(Reset message) {

        for (String i : cabDataMap.keySet()) {
            cabDataMap.get(i).numRides = 0;
            cabDataMap.get(i).state = CabState.SIGNED_OUT;
            cabDataMap.get(i).rideId = -1;
            cabDataMap.get(i).location = 0;
            cabDataMap.get(i).sourceLoc = -1;
            cabDataMap.get(i).destinationLoc = -1;
        }

        return this;
    }

}