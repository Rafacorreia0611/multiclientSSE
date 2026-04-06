package sse.demo.messages;

public enum RequestType {
    SEARCH,
    UPDATE,
    STATE_SRCH,
    STATE_UPD,
    INIT_TOKEN_GEN_KEY;

    public static RequestType[] values = values();

    public static RequestType getRequestType(int ordinal) {
        return values[ordinal];
    }
}
