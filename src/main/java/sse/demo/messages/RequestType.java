package sse.demo.messages;

public enum RequestType {
    // Interactive client requests
    SEARCH,
    UPDATE,
    STATE,
    SETUP_STATE,
    INIT_STATE,

    // PopulateDB requests
    SETUP_COMPLETE,
    SETUP_ABORT;

    public static RequestType[] values = values();

    public static RequestType getRequestType(int ordinal) {
        return values[ordinal];
    }
}
