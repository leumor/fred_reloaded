module hyphanet.crypt {
    requires org.jspecify;
    requires hyphanet.support;
    requires org.bouncycastle.provider;
    requires ch.qos.logback.classic;
    requires org.slf4j;
    requires org.apache.commons.rng.simple;
    requires org.apache.commons.rng.api;

    exports hyphanet.crypt;
    exports hyphanet.crypt.blockcipher;
    exports hyphanet.crypt.cryptokey;
}