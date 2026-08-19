package confidential.client;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.HashedExtractor;
import bftsmart.tom.util.ServiceContent;
import bftsmart.tom.util.ServiceResponse;

import java.util.Comparator;
import java.util.Map;

final class ConfigurableServiceProxy extends ServiceProxy {
	ConfigurableServiceProxy(int processId, String configHome,
							 Comparator<ServiceContent> replyComparator,
							 Extractor replyExtractor,
							 HashedExtractor hashedReplyExtractor) {
		super(processId, configHome, replyComparator, replyExtractor, hashedReplyExtractor);
	}

	ServiceResponse invokeOrdered(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
	}

	ServiceResponse invokeOrderedHashed(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.ORDERED_HASHED_REQUEST, request, replicaSpecificContents, metadata);
	}

	ServiceResponse invokeUnordered(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.UNORDERED_REQUEST, request, replicaSpecificContents, metadata);
	}

	ServiceResponse invokeUnorderedHashed(byte[] request, Map<Integer, byte[]> replicaSpecificContents, byte metadata) {
		return invoke(TOMMessageType.UNORDERED_HASHED_REQUEST, request, replicaSpecificContents, metadata);
	}
}
