package sse.domain;

import java.io.Serializable;
import java.util.Objects;

public final class KeywordState implements Serializable {

    private final SearchTokenValue currentToken;
    private final int counter;
    private final boolean locked;

    public KeywordState(SearchTokenValue currentToken, int counter, boolean locked) {
        if (currentToken == null) {
            throw new IllegalArgumentException("currentToken cannot be null");
        }
        if (counter <= 0) {
            throw new IllegalArgumentException("counter must be greater than zero");
        }
        this.currentToken = currentToken;
        this.counter = counter;
        this.locked = locked;
    }

    public SearchTokenValue currentToken() {
        return currentToken;
    }

    public int counter() {
        return counter;
    }

    public boolean locked() {
        return locked;
    }

    public KeywordState withLock(boolean locked) {
        return new KeywordState(currentToken, counter, locked);
    }

    public KeywordState withCurrentToken(SearchTokenValue currentToken) {
        return new KeywordState(currentToken, counter, locked);
    }

    public KeywordState withCounter(int counter) {
        return new KeywordState(currentToken, counter, locked);
    }

    public KeywordState advanceTo(SearchTokenValue currentToken) {
        return new KeywordState(currentToken, counter + 1, locked);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KeywordState that = (KeywordState) o;
        return counter == that.counter
                && locked == that.locked
                && Objects.equals(currentToken, that.currentToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentToken, counter, locked);
    }

    @Override
    public String toString() {
        return "KeywordState[" +
                "currentToken=" + currentToken +
                ", counter=" + counter +
                ", locked=" + locked +
                ']';
    }
}
