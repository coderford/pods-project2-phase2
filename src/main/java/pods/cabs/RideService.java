package pods.cabs;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

public class RideService extends AbstractBehavior<RideService.Command> {
    int fulfillSpawnCount;
    private final String rsid;

    public interface Command {}

    // Declare typekey for cluster sharding
    public static final EntityTypeKey<Command> TypeKey =
        EntityTypeKey.create(RideService.Command.class, "RideServiceEntity");

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
    public static Behavior<Command> create(String rsid) {
        return Behaviors.setup(context -> {
            return new RideService(context, rsid);
        });
    }

    private RideService(ActorContext<Command> context, String rsid) {
        super(context);
        this.fulfillSpawnCount = 0;
        this.rsid = rsid;
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

        return builder.build();
    }

    private Behavior<Command> onRequestRide(RequestRide message) {
        getContext().getLog().info("-- RideService: received ride request for custtomer " + message.custId);
        
        // Spawn a new FulfillRide actor, with a unique name
        fulfillSpawnCount++;
        String name = this.toString() + "-ff" + fulfillSpawnCount;
        ActorRef<FulfillRide.Command> fulfillActor = getContext().spawn(FulfillRide.create(), name);

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
        return this;
    }

    private Behavior<Command> onCabSignsOut(CabSignsOut message) {
        return this;
    }

    private Behavior<Command> onRideResponse(RideResponse message) {
        // Send message to testProbe about the ride response
        message.probe.tell(message);
        return this;
    }

    private Behavior<Command> onRideEnded(RideEnded message) {
        return this;
    }

    private Behavior<Command> onReset(Reset message) {
        return this;
    }

}