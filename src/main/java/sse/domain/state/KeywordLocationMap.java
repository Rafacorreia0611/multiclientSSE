package sse.domain.state;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class KeywordLocationMap implements Serializable {

    private final Map<String, Integer> addresses;
    private final int oramCapacity;

    private KeywordLocationMap(Map<String, Integer> addresses, int oramCapacity) {
        validate(addresses, oramCapacity);
        this.addresses = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(addresses));
        this.oramCapacity = oramCapacity;
    }

    public static KeywordLocationMap build(List<String> keywords) {
        if (keywords == null) {
            throw new IllegalArgumentException("keywords cannot be null");
        }
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords cannot be empty");
        }

        Set<String> seenKeywords = new HashSet<String>();
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                throw new IllegalArgumentException("keywords cannot contain null or blank values");
            }
            if (!seenKeywords.add(keyword)) {
                throw new IllegalArgumentException("keywords cannot contain duplicates: " + keyword);
            }
        }

        int oramCapacity = chooseMinimumOramCapacity(keywords.size());
        List<Integer> shuffledAddresses = new ArrayList<Integer>(oramCapacity);
        for (int address = 0; address < oramCapacity; address++) {
            shuffledAddresses.add(address);
        }
        Collections.shuffle(shuffledAddresses, new SecureRandom());

        Map<String, Integer> result = new LinkedHashMap<String, Integer>(keywords.size());
        for (int i = 0; i < keywords.size(); i++) {
            result.put(keywords.get(i), shuffledAddresses.get(i));
        }
        return new KeywordLocationMap(result, oramCapacity);
    }

    private static int chooseMinimumOramCapacity(int keywordCount) {
        int capacity = 1;
        while (capacity < keywordCount) {
            if (capacity > (Integer.MAX_VALUE - 1) / 2) {
                throw new IllegalArgumentException("keyword count is too large for integer ORAM addresses");
            }
            capacity = capacity * 2 + 1;
        }
        return capacity;
    }

    public Integer addressOf(String keyword) {
        if (keyword == null) {
            return null;
        }
        return addresses.get(keyword);
    }

    public boolean containsKeyword(String keyword) {
        return keyword != null && addresses.containsKey(keyword);
    }

    public int size() {
        return addresses.size();
    }

    public int oramCapacity() {
        return oramCapacity;
    }

    public Map<String, Integer> addresses() {
        return addresses;
    }

    private void validate(Map<String, Integer> addresses, int oramCapacity) {
        if (addresses == null) {
            throw new IllegalArgumentException("addresses cannot be null");
        }
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("addresses cannot be empty");
        }
        if (oramCapacity <= 0) {
            throw new IllegalArgumentException("oramCapacity must be greater than zero");
        }

        Set<Integer> seenAddresses = new HashSet<Integer>();
        for (Map.Entry<String, Integer> entry : addresses.entrySet()) {
            String keyword = entry.getKey();
            Integer address = entry.getValue();
            if (keyword == null || keyword.trim().isEmpty()) {
                throw new IllegalArgumentException("addresses cannot contain null or blank keywords");
            }
            if (address == null) {
                throw new IllegalArgumentException("addresses cannot contain null ORAM addresses");
            }
            if (address < 0 || address >= oramCapacity) {
                throw new IllegalArgumentException("ORAM address is outside capacity: " + address);
            }
            if (!seenAddresses.add(address)) {
                throw new IllegalArgumentException("Duplicate ORAM address: " + address);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        KeywordLocationMap that = (KeywordLocationMap) o;
        return oramCapacity == that.oramCapacity
                && Objects.equals(addresses, that.addresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addresses, oramCapacity);
    }

    @Override
    public String toString() {
        return "KeywordLocationMap[" +
                "size=" + addresses.size() +
                ", oramCapacity=" + oramCapacity +
                ']';
    }
}
