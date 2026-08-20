package sse.demo.messages;

public enum ResponseStatus {
    OK,
    FAILED;

    public static ResponseStatus[] values = values();

    public static ResponseStatus getResponseStatus(int ordinal) {
        return values[ordinal];
    }
}
