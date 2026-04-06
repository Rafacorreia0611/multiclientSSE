package sse.demo.messages;

public enum ResponseStatus {
    OK,
    RETRY,
    BUSY,
    FAILED;

    public static ResponseStatus[] values = values();

    public static ResponseStatus getResponseStatus(int ordinal) {
        return values[ordinal];
    }
}
