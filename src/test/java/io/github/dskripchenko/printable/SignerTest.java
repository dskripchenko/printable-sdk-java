package io.github.dskripchenko.printable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Vectors generated from the Printable server implementation
 * (App\Auth\HashSigner, salt-123 / sec-1) — byte-compatibility contract.
 */
class SignerTest {

    private static final String SALT = "salt-123";
    private static final String SECRET = "sec-1";

    static Stream<Arguments> vectors() {
        Map<String, Object> v1 = orderedMap(
                "b", 1,
                "a", "x",
                "nested", orderedMap("z", true, "a", List.of(3, 1, 2)));
        Map<String, Object> v2 = orderedMap(
                "template", "invoice",
                "timestamp", 1753600000L,
                "variables", orderedMap("user", orderedMap("name", "Иван/Ко", "age", "42")));
        Map<String, Object> v3 = orderedMap(
                "num_str", "100.0",
                "exp", "1e3",
                "zeros", "007",
                "plain", "text",
                "empty_obj", Map.of(),
                "empty_arr", List.of());
        Map<String, Object> itemA = orderedMap("k", "v", "a", 1);
        Map<String, Object> itemB = new LinkedHashMap<>();
        itemB.put("x", null);
        Map<String, Object> v4 = orderedMap(
                "list", orderedMap("b", 2, "items", List.of(itemA, itemB)),
                "flag", false);

        return Stream.of(
                Arguments.of(v1,
                        "{\"a\":\"x\",\"b\":1,\"nested\":{\"a\":[3,1,2],\"z\":true}}",
                        "f14fc443af8b3891f927aeb32c725923a4dc42fbf78eba2ce1439940be4ec5c2"),
                Arguments.of(v2,
                        "{\"template\":\"invoice\",\"timestamp\":1753600000,\"variables\":{\"user\":{\"age\":42,\"name\":\"Иван/Ко\"}}}",
                        "3ede97642552144bbb774d077f56bc9c5fc9fe16e8d529a1941d7d155dd3faa6"),
                Arguments.of(v3,
                        "{\"empty_arr\":[],\"empty_obj\":[],\"exp\":1000,\"num_str\":100,\"plain\":\"text\",\"zeros\":7}",
                        "35698b058d732971e4f74a3ef78db7ea1662b5758e03f353500dc2f9f7448be4"),
                Arguments.of(v4,
                        "{\"flag\":false,\"list\":{\"b\":2,\"items\":[{\"a\":1,\"k\":\"v\"},{\"x\":null}]}}",
                        "be151b065cdd8f2eb10d79a463ddc7467a185d154255fb427335574b8acbc845"));
    }

    @Test
    void signTokenMatchesServerSimpleMode() {
        assertEquals(
                "bf9a3d9e15dbac35b13b0324bac385564bcec7a1d9f4fc56c7da5293da3ebf02",
                new Signer(SALT).signToken("tok-1", 1753600000L, SECRET));
    }

    @ParameterizedTest
    @MethodSource("vectors")
    void canonicalizeAndSignPayload(Map<String, Object> payload, String canonical, String digest) {
        assertEquals(canonical, Canonical.canonicalize(payload));
        assertEquals(digest, new Signer(SALT).signPayload(payload, SECRET));
    }

    @Test
    void sequentialKeyMapsBecomeArrays() {
        assertEquals("{\"list\":[\"a\",\"b\"]}",
                Canonical.canonicalize(Map.of("list", orderedMap("0", "a", "1", "b"))));
    }

    @Test
    void verifyWebhookMatchesServerSignature() {
        Signer signer = new Signer(SALT);
        assertTrue(signer.verifyWebhook(
                "7641aadedab7b606585ba0c68ddfe4583b778b7a9bef62c700c76f9596dc9141",
                1753600000L, "{\"uuid\":\"abc\"}", SECRET));
        assertFalse(signer.verifyWebhook("deadbeef", 1753600000L, "{\"uuid\":\"abc\"}", SECRET));
    }

    @Test
    void rejectsEmptySalt() {
        assertThrows(IllegalArgumentException.class, () -> new Signer(""));
    }

    private static Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
