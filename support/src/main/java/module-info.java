module hyphanet.support {
  requires org.apache.commons.lang3;
  requires com.machinezoo.noexception;
  requires hyphanet.crypt;
  requires hyphanet.base;
  requires org.bouncycastle.provider;
  requires org.jspecify;
  requires com.google.errorprone.annotations;
  requires com.sun.jna;
  requires com.sun.jna.platform;
  requires nullaway.annotations;

  exports hyphanet.support.io;
  exports hyphanet.support.io.storage.bucket;
  exports hyphanet.support.io.storage.rab;
  exports hyphanet.support.io.stream;
  exports hyphanet.support.io.util;
  exports hyphanet.support.io.storage;
  exports hyphanet.support.io.storage.bucket.wrapper;
}
