package io.thill.jacoio.mapper;

import io.thill.jacoio.ConcurrentFile;
import org.agrona.IoUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class SingleProcessRollingConcurrentFileTest extends SingleProcessConcurrentFileTest {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private File tmpDirectory;

  @After
  public void cleanup() throws Exception {
    if(file != null) {
      file.close();
      file = null;
    }
    if(tmpDirectory != null) {
      logger.info("Deleting {}", tmpDirectory.getAbsolutePath());
      IoUtil.delete(tmpDirectory, false);
      tmpDirectory = null;
    }
  }

  @Override
  protected void createFile(int capacity, boolean fillWithZeros) throws Exception {
    if(file != null)
      file.close();
    tmpDirectory = Files.createTempDirectory(getClass().getSimpleName()).toFile();
    logger.info("Testing with directory at {}", tmpDirectory.getAbsolutePath());

    file = ConcurrentFile.map()
            .location(tmpDirectory)
            .capacity(capacity)
            .fillWithZeros(fillWithZeros)
            .multiProcess(false)
            .roll(roll -> roll
                    .enabled(true)
                    .fileNamePrefix("test-")
                    .fileNameSuffix(".bin")
                    .asyncClose(false)
                    .fileCreatedListener(f -> logger.info("Created: {}", f.getFile().getAbsolutePath()))
                    .fileMappedListener(f -> logger.info("Mapped: {}", f.getFile().getAbsolutePath()))
                    .fileCompleteListener(f -> logger.info("Complete: {}", f.getFile().getAbsolutePath()))
                    .fileClosedListener(f -> logger.info("Closed: {}", f.getAbsolutePath()))
                    .yieldOnAllocateContention(true)
            )
            .map();

    Assert.assertEquals(RollingConcurrentFile.class, file.getClass());
  }

  @Override
  @Test(expected = IOException.class)
  public void testSingleWriteExceedsCapacity() throws Exception {
    // rolling implementation will throw an IOException to indicate this write will never fit
    super.testSingleWriteExceedsCapacity();
  }

  @Test
  public void testMultipleWritesExceedCapacity() throws Exception {
    // rolling implementation will put these values in 2 separate files
    createFile(20 + frameHeaderSize() * 3, false);

    // these end up in the first mapper
    byte[] buffer1 = "buffer1".getBytes();
    int offset1 = file.write(buffer1, 0, buffer1.length);
    byte[] buffer2 = "buffer2".getBytes();
    int offset2 = file.write(buffer2, 0, buffer2.length);

    // test first mapper before roll
    Assert.assertEquals(startOffset(), offset1);
    Assert.assertEquals(startOffset() + frameHeaderSize() + buffer1.length, offset2);
    assertBytesAt(buffer1, offset1 + frameHeaderSize());
    assertBytesAt(buffer2, offset2 + frameHeaderSize());

    // test end up in the second mapper
    byte[] buffer3 = "buffer3".getBytes();
    int offset3 = file.write(buffer3, 0, buffer3.length);
    Assert.assertEquals(startOffset(), offset3);

    // test second mapper after roll
    Assert.assertEquals(startOffset(), offset3);
    assertBytesAt(buffer3, offset3 + frameHeaderSize());
  }

}
