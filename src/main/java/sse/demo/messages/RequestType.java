package sse.demo.messages;

public enum RequestType {
    SEARCH,
    UPDATE,
    STATE,
    INIT_STATE,
    IS_INITIALIZED;

    public static RequestType[] values = values();

    public static RequestType getRequestType(int ordinal) {
        return values[ordinal];
    }
}
