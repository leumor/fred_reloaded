package hyphanet.support.io.storage.rab;

public class ArrayRabTest extends RabTestBase {

  private static final int[] TEST_LIST =
      new int[] {0, 1, 32, 64, 32768, 1024 * 1024, 1024 * 1024 + 1};

  public ArrayRabTest() {
    super(TEST_LIST);
  }

  @Override
  protected Rab construct(long size) {
    assert (size < Integer.MAX_VALUE);
    return new ArrayRab(new byte[(int) size]);
  }
}
