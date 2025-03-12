package hyphanet.support.io.storage.rab;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.bucket.PersistentTempFileBucketFactory;
import java.util.Random;

public class MockResumeContext implements ResumeContext {
  @Override
  public PersistentFileTracker getPersistentFileTracker() {
    return null;
  }

  @Override
  public PersistentTempFileBucketFactory getPersistentTempBucketFactory() {
    return null;
  }

  @Override
  public MasterSecret getPersistentMasterSecret() {
    return persistentMasterSecret;
  }

  public void setPersistentMasterSecret(MasterSecret persistentMasterSecret) {
    this.persistentMasterSecret = persistentMasterSecret;
  }

  @Override
  public PersistentTempFileBucketFactory getPersistentBucketFactory() {
    return null;
  }

  @Override
  public Random getFastWeakRandom() {
    return null;
  }

  @Override
  public FilenameGenerator getPersistentFg() {
    return null;
  }

  private MasterSecret persistentMasterSecret;
}
