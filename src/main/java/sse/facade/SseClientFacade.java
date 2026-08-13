package sse.facade;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import sse.crypto.KeywordAddressMapEncryption;
import sse.crypto.TrapdoorPermutation;
import sse.domain.state.EncryptedKeywordAddressMap;
import sse.domain.update.EncryptedUpdateTuple;
import sse.domain.setup.InitializationMaterial;
import sse.domain.state.KeywordAddressMap;
import sse.domain.update.KeywordUpdate;
import sse.domain.update.PreparedUpdateRequest;
import sse.domain.search.SearchToken;
import sse.domain.state.State;
import sse.domain.update.UpdateTuple;
import sse.service.client.SearchTokenService;
import sse.service.client.UpdateTokenService;
import sse.vocabulary.LuceneKeywordNormalizer;
import sse.vocabulary.VocabularyLoader;

public final class SseClientFacade {

    private final SearchTokenService searchTokenService;
    private final UpdateTokenService updateTokenService;

    public SseClientFacade() {
        this.searchTokenService = new SearchTokenService();
        this.updateTokenService = new UpdateTokenService();
    }

    public InitializationMaterial generateInitialStateData() {
        return generateInitialStateData(VocabularyLoader.DEFAULT_VOCABULARY_PATH);
    }

    public InitializationMaterial generateInitialStateData(Path vocabularyPath) {
        if (vocabularyPath == null) {
            throw new IllegalArgumentException("vocabularyPath cannot be null");
        }

        SecretKey masterKey = generateMasterKey();
        KeyPair trapdoorKeyPair = TrapdoorPermutation.generateKeyPair();
        EncryptedKeywordAddressMap encryptedKeywordAddressMap =
                generateEncryptedKeywordAddressMap(masterKey, vocabularyPath);
        return new InitializationMaterial(
                masterKey,
                (RSAPublicKey) trapdoorKeyPair.getPublic(),
                (RSAPrivateKey) trapdoorKeyPair.getPrivate(),
                encryptedKeywordAddressMap
        );
    }

    public UpdateTuple decryptUpdateTuple(SecretKey key, byte[] iv, EncryptedUpdateTuple encryptedTuple)
            throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException,
            InvalidKeyException, ClassNotFoundException, IllegalBlockSizeException, IOException,
            BadPaddingException {
        return searchTokenService.decryptUpdateTuple(key, iv, encryptedTuple);
    }

    public SearchToken generateSearchToken(SecretKey masterKey, State state, String keyword) {
        return searchTokenService.generateSearchToken(masterKey, state, keyword);
    }

    public PreparedUpdateRequest prepareUpdateRequest(SecretKey masterKey, RSAPrivateKey trapdoorPrivateKey,
                                                      State state, List<KeywordUpdate> updates) {
        return updateTokenService.prepareUpdateRequest(
                masterKey,
                trapdoorPrivateKey,
                state,
                updates
        );
    }

    public List<String> extractAddedDocIds(Map<EncryptedUpdateTuple, SecretKey> updates) {
        return searchTokenService.extractAddedDocIds(updates);
    }

    private SecretKey generateMasterKey() {
        KeyGenerator keyGen;
        try {
            keyGen = KeyGenerator.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HmacSHA256 algorithm not available for master key", e);
        }
        keyGen.init(256);
        return keyGen.generateKey();
    }

    private EncryptedKeywordAddressMap generateEncryptedKeywordAddressMap(SecretKey masterKey, Path vocabularyPath) {
        VocabularyLoader vocabularyLoader = new VocabularyLoader(new LuceneKeywordNormalizer());
        List<String> keywords = vocabularyLoader.load(vocabularyPath);
        KeywordAddressMap keywordAddressMap = KeywordAddressMap.build(keywords);
        SecretKey keywordMapKey = KeywordAddressMapEncryption.deriveKey(masterKey);
        return KeywordAddressMapEncryption.encrypt(
                keywordMapKey,
                KeywordAddressMapEncryption.generateIv(),
                keywordAddressMap
        );
    }
}
