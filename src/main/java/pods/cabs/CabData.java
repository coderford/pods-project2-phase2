package pods.cabs;

public class CabData {
    public String id;
    public int numRides;
    public CabState state;

    public boolean interested;
    public int rideId;
    public int custId;
    public int location;
    public int sourceLoc;
    public int destinationLoc;
    public int timestamp;

    public CabData(String id) {
        this.id = id;
        this.numRides = 0;
        this.state = CabState.SIGNED_OUT;
        this.rideId = -1;
        this.location = 0;
        this.interested = true;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
        this.timestamp = 0;
    }

    public void reset() {
        this.numRides = 0;
        this.state = CabState.SIGNED_OUT;
        this.rideId = -1;
        this.location = 0;
        this.interested = true;
        this.sourceLoc = -1;
        this.destinationLoc = -1;
        this.timestamp = 0;
    }
}
