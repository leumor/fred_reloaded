module hyphanet.crypt {
  requires org.jspecify;
  requires hyphanet.base;
  requires org.bouncycastle.provider;
  requires ch.qos.logback.classic;
  requires org.slf4j;
  requires com.google.errorprone.annotations;

  exports hyphanet.crypt;
  exports hyphanet.crypt.hash;
  exports hyphanet.crypt.key;
  exports hyphanet.crypt.mac;
  exports hyphanet.crypt.io;
}
