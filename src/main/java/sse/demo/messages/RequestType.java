package sse.demo.messages;

public enum RequestType {
    SEARCH,
    UPDATE,
    STATE,
    INIT_STATE;

    public static RequestType[] values = values();

    public static RequestType getRequestType(int ordinal) {
        return values[ordinal];
    }
}
