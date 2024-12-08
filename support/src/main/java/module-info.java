module hyphanet.support {
    requires org.bouncycastle.provider;
    requires org.jspecify;
    requires org.apache.commons.rng.simple;
    requires org.apache.commons.rng.api;
    requires ch.qos.logback.classic;
    requires org.slf4j;
    requires org.apache.commons.lang3;

    exports hyphanet.support;
    exports hyphanet.support.io;
}