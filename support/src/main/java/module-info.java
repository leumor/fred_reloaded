module hyphanet.support {
    requires org.bouncycastle.provider;
    requires org.jspecify;
    requires org.apache.commons.rng.simple;
    requires org.apache.commons.rng.api;
    requires ch.qos.logback.classic;
    requires org.slf4j;
    requires org.apache.commons.lang3;
    requires com.machinezoo.noexception;
    requires hyphanet.base;

    exports hyphanet.support;
    exports hyphanet.support.io;
    exports hyphanet.support.field;
    exports hyphanet.support.io.bucket;
    exports hyphanet.support.io.randomaccessbuffer;
    exports hyphanet.support.io.stream;
}