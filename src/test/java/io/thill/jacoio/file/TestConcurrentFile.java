package io.thill.jacoio.file;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static io.thill.jacoio.file.ConcurrentFile.HEADER_SIZE;
import static io.thill.jacoio.file.ConcurrentFile.NULL_OFFSET;

public class TestConcurrentFile {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private ConcurrentFile file;

  @After
  public void cleanup() throws Exception {
    if(file != null) {
      file.close();
      file = null;
    }
  }

  private void createFile(int capacity, boolean fillWithZeros) throws Exception {
    if(file != null)
      file.close();
    File underlyingFile = File.createTempFile(getClass().getSimpleName(), ".bin");
    while(!underlyingFile.delete())
      Thread.sleep(10);
    logger.info("Testing with file at {}", underlyingFile.getAbsolutePath());
    file = ConcurrentFile.mapNewFile(underlyingFile, capacity, fillWithZeros);
  }

  @Test
  public void testWriteByteArray() throws Exception  {
    createFile(128, false);

    byte[] writeBytes = "Hello World!".getBytes();
    int offset = file.write(writeBytes, 0, writeBytes.length);

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt(writeBytes, offset);
  }

  @Test
  public void testWriteByteBuffer() throws Exception  {
    createFile(128, false);

    ByteBuffer writeByteBuffer = ByteBuffer.wrap("Hello World!".getBytes());
    int offset = file.write(writeByteBuffer);

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt(writeByteBuffer.array(), offset);
  }

  @Test
  public void testWriteDirectBuffer() throws Exception  {
    createFile(128, false);

    DirectBuffer writeDirectBuffer = new UnsafeBuffer("Hello World!".getBytes());
    int offset = file.write(writeDirectBuffer, 0, writeDirectBuffer.capacity());

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt(writeDirectBuffer.byteArray(), offset);
  }

  @Test
  public void testWriteFunction() throws Exception  {
    createFile(128, false);

    byte[] writeBytes = "Hello World!".getBytes();
    int offset = file.write(writeBytes.length, (buffer, off) -> {
      buffer.putBytes(off, writeBytes);
    });

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt(writeBytes, offset);
  }

  @Test
  public void testWriteAscii() throws Exception  {
    createFile(128, false);

    int offset = file.writeAscii("Hello ");
    file.writeAscii("World!");

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt("Hello World!".getBytes("UTF-8"), offset);
  }

  @Test
  public void testWriteChars() throws Exception  {
    createFile(128, false);

    int offset = file.writeChars("Hello World!", ByteOrder.LITTLE_ENDIAN);

    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt("Hello World!".getBytes("UTF-16LE"), offset);
  }

  @Test
  public void testMultipleWrites() throws Exception {
    createFile(128, false);

    byte[] buffer1 = "buffer1".getBytes();
    int offset1 = file.write(buffer1, 0, buffer1.length);
    byte[] buffer2 = "bytes2".getBytes();
    int offset2 = file.write(buffer2, 0, buffer2.length);

    Assert.assertEquals(HEADER_SIZE, offset1);
    Assert.assertEquals(HEADER_SIZE + buffer1.length, offset2);

    assertBytesAt(buffer1, offset1);
    assertBytesAt(buffer2, offset2);
  }

  @Test
  public void testMultipleWritesExceedCapacity() throws Exception {
    createFile(20, false);

    byte[] buffer1 = "buffer1".getBytes();
    int offset1 = file.write(buffer1, 0, buffer1.length);
    byte[] buffer2 = "buffer2".getBytes();
    int offset2 = file.write(buffer2, 0, buffer2.length);
    byte[] buffer3 = "buffer3".getBytes();
    int offset3 = file.write(buffer3, 0, buffer3.length);

    Assert.assertEquals(HEADER_SIZE, offset1);
    Assert.assertEquals(HEADER_SIZE + buffer1.length, offset2);
    Assert.assertEquals(NULL_OFFSET, offset3);

    assertBytesAt(buffer1, offset1);
    assertBytesAt(buffer2, offset2);
  }

  @Test
  public void testSingleWriteExceedsCapacity() throws Exception {
    createFile(128, false);
    int offset = file.write(ByteBuffer.wrap(new byte[129]));
    Assert.assertEquals(NULL_OFFSET, offset);
  }

  @Test
  public void testSingleWriteExactlyFits() throws Exception {
    createFile(128, false);
    byte[] writeBytes = new byte[128];
    for(int i = 0; i < writeBytes.length; i++) {
      writeBytes[i] = (byte)i;
    }
    int offset = file.write(writeBytes, 0, writeBytes.length);
    Assert.assertEquals(HEADER_SIZE, offset);
    assertBytesAt(writeBytes, offset);
  }

  @Test
  public void testMapExistingFile() throws Exception {
    createFile(128, false);
    file.write(ByteBuffer.wrap("Hello ".getBytes()));

    try(ConcurrentFile existing = ConcurrentFile.mapExistingFile(file.getFile())) {
      existing.write(ByteBuffer.wrap("World!".getBytes()));
      assertBytesAt("Hello World!".getBytes(), existing, HEADER_SIZE);
    }

    assertBytesAt("Hello World!".getBytes(), file, HEADER_SIZE);
  }

  private void assertBytesAt(byte[] expected, int offset) throws IOException {
    assertBytesAt(expected, file, offset);
  }

  private static void assertBytesAt(byte[] expected, ConcurrentFile src, int offset) throws IOException {
    byte[] fileBytes = Files.readAllBytes(Paths.get(src.getFile().toURI()));
    byte[] actual = Arrays.copyOfRange(fileBytes, offset, offset + expected.length);
    Assert.assertArrayEquals(expected, actual);
  }

}
