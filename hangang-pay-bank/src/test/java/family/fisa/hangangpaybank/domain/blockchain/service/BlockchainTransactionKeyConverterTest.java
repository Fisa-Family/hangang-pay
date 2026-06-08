package family.fisa.hangangpaybank.domain.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockchainTransactionKeyConverterTest {

    private final BlockchainTransactionKeyConverter converter =
            new BlockchainTransactionKeyConverter();

    private static final String UUID_1 = "550e8400-e29b-41d4-a716-446655440000";
    private static final String UUID_2 = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    @Test
    void sameUuidProducesSameBytes32() {
        byte[] first = converter.toBytes32(UUID_1);
        byte[] second = converter.toBytes32(UUID_1);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void resultIsExactly32Bytes() {
        byte[] result = converter.toBytes32(UUID_1);
        assertThat(result).hasSize(32);
    }

    @Test
    void differentUuidsProduceDifferentBytes32() {
        byte[] first = converter.toBytes32(UUID_1);
        byte[] second = converter.toBytes32(UUID_2);
        assertThat(first).isNotEqualTo(second);
    }
}
