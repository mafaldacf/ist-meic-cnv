package pt.ulisboa.tecnico.cnv.awsmanagement;

public class InstanceProperties {

    private final String ip;
    private StatusType status;

    private long workload;

    private int requests;
    private final String id;

    public enum StatusType {
        RUNNING,
        HOLD_TO_TERMINATE,
        TERMINATE,
        RESERVED,
        DEAD
    }
    public InstanceProperties(String ip, String id) {
        this.id = id;
        this.ip = ip;
        this.status = StatusType.RUNNING;
        this.workload = 0;
        this.requests = 0;
    }

    public String getId() {
        return this.id;
    }

    public void addWorkload(long workload) {
        this.workload += workload;
    }

    public void removeWorkload(long workload) {
        this.workload -= workload;
    }

    public void addRequest() {
        this.requests += 1;
    }

    public void removeRequest() {
        this.requests -= 1;
    }

    public long getWorkload() {
        return workload;
    }

    public int getRequests() {
        return requests;
    }

    public String getIp() {
        return ip;
    }

    public StatusType getStatus() {
        return status;
    }

    public boolean isRunning() {
        return this.status == StatusType.RUNNING;
    }

    public boolean isDead() {
        return this.status == StatusType.DEAD;
    }

    public boolean isTerminate() {
        return this.status == StatusType.TERMINATE;
    }

    // To be set by the AS
    protected void setOnHoldToTerminate() {
        this.status = StatusType.HOLD_TO_TERMINATE;
    }

    // To be set and used by the LB
    protected void setTerminate() {
        this.status = StatusType.TERMINATE;
    }

    protected boolean isHoldingToTerminate() {
        return this.status == StatusType.HOLD_TO_TERMINATE;
    }

    protected void setReserved() {
        this.status = StatusType.RESERVED;
    }

    protected void setRunning() {
        this.status = StatusType.RUNNING;
    }

    protected void setDead() {
        this.status = StatusType.DEAD;
    }

}
