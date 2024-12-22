package hyphanet.access;

public enum CryptoAlgorithm {
    ALGO_AES_PCFB_256_SHA256(2), ALGO_AES_CTR_256_SHA256(3);

    CryptoAlgorithm(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    private final int value;
}
