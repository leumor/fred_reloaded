package hyphanet.support.io.storage.rab;

public class ByteArrayRabTest extends RabTestBase {

  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};

  public ByteArrayRabTest() {
    super(TEST_LIST);
  }

  @Override
  protected Rab construct(long size) {
    assert (size < Integer.MAX_VALUE);
    return new ByteArrayRab(new byte[(int) size]);
  }
}
