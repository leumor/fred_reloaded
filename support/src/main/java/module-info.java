module hyphanet.support {
  requires org.apache.commons.lang3;
  requires com.machinezoo.noexception;
  requires hyphanet.crypt;
  requires hyphanet.base;
  requires org.bouncycastle.provider;
  requires org.jspecify;
  requires com.google.errorprone.annotations;

  exports hyphanet.support;
  exports hyphanet.support.io;
  exports hyphanet.support.io.storage.bucket;
  exports hyphanet.support.io.storage.rab;
  exports hyphanet.support.io.stream;
  exports hyphanet.support.io.util;
  exports hyphanet.support.io.storage;
}
