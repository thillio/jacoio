/**
 * Copyright (c) 2019 Eric Thill
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this getFile except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package io.thill.jacoio.mapper;

import io.thill.jacoio.ConcurrentFile;
import io.thill.jacoio.function.BiParametizedWriteFunction;
import io.thill.jacoio.function.ParametizedWriteFunction;
import io.thill.jacoio.function.TriParametizedWriteFunction;
import io.thill.jacoio.function.WriteFunction;
import org.agrona.DirectBuffer;
import org.agrona.IoUtil;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extends {@link ConcurrentFile} to provide single-process writing. There is no getFile header, and a getFile cannot be reopened after it has been closed.
 *
 * @author Eric Thill
 */
class SingleProcessConcurrentFile implements MappedConcurrentFile {

  static SingleProcessConcurrentFile map(File file, int capacity, boolean fillWithZeros) throws IOException {
    if(file.exists())
      throw new IOException("File Exists. SingleProcessConcurrentFile cannot modify an existing getFile.");
    final int fileSize = capacity;
    final FileChannel fileChannel = IoUtil.createEmptyFile(file, fileSize, fillWithZeros);
    final long address = IoUtil.map(fileChannel, MapMode.READ_WRITE, 0, fileSize);
    final AtomicBuffer buffer = new UnsafeBuffer();
    buffer.wrap(address, fileSize);
    return new SingleProcessConcurrentFile(file, fileChannel, buffer, fileSize);
  }

  private final AtomicLong nextWriteOffset = new AtomicLong(0);
  private final AtomicLong writeComplete = new AtomicLong(0);
  private final AtomicLong finalFileSize = new AtomicLong(-1);
  private final File file;
  private final FileChannel fileChannel;
  private final AtomicBuffer buffer;
  private final long fileSize;

  SingleProcessConcurrentFile(File file, FileChannel fileChannel, AtomicBuffer buffer, int fileSize) {
    this.file = file;
    this.fileChannel = fileChannel;
    this.buffer = buffer;
    this.fileSize = fileSize;
  }

  @Override
  public void close() throws IOException {
    if(fileChannel.isOpen()) {
      if(isPending())
        throw new IOException("There are pending writes");
      if(finalFileSize.get() >= 0)
        fileChannel.truncate(finalFileSize.get());
      fileChannel.close();
      IoUtil.unmap(fileChannel, buffer.addressOffset(), fileSize);
    }
  }

  @Override
  public boolean isPending() {
    return nextWriteOffset.get() != writeComplete.get();
  }

  @Override
  public void finish() {
    // this will happen automatically if we reserve more bytes than can fit in the int32 (minus header) worth of data
    reserve(Integer.MAX_VALUE);
  }

  @Override
  public boolean isFinished() {
    final long writeComplete = this.writeComplete.get();
    final long nextOffset = this.nextWriteOffset.get();
    return writeComplete == nextOffset && writeComplete >= fileSize && finalFileSize.get() > 0;
  }

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public int write(final byte[] srcBytes, final int srcOffset, final int length) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putBytes(dstOffset, srcBytes, srcOffset, length);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int write(final DirectBuffer srcBuffer, final int srcOffset, final int length) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putBytes(dstOffset, srcBuffer, srcOffset, length);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int write(final ByteBuffer srcByteBuffer) {
    final int length = srcByteBuffer.remaining();
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putBytes(dstOffset, srcByteBuffer, srcByteBuffer.position(), srcByteBuffer.remaining());
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeAscii(final CharSequence srcCharSequence) {
    final int length = srcCharSequence.length();
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      for(int i = 0; i < srcCharSequence.length(); i++) {
        final char c = srcCharSequence.charAt(i);
        buffer.putByte(dstOffset + i, c > 127 ? (byte)'?' : (byte)c);
      }
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeChars(final CharSequence srcCharSequence, ByteOrder byteOrder) {
    final int length = srcCharSequence.length() * 2;
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      for(int i = 0; i < srcCharSequence.length(); i++) {
        buffer.putChar(dstOffset + (i * 2), srcCharSequence.charAt(i), byteOrder);
      }
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int write(final int length, final WriteFunction writeFunction) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      writeFunction.write(buffer, dstOffset, length);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public <P> int write(final int length, final P parameter, final ParametizedWriteFunction<P> writeFunction) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      writeFunction.write(buffer, dstOffset, length, parameter);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public <P1, P2> int write(final int length, final P1 parameter1, final P2 parameter2, final BiParametizedWriteFunction<P1, P2> writeFunction) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      writeFunction.write(buffer, dstOffset, length, parameter1, parameter2);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public <P1, P2, P3> int write(final int length, final P1 parameter1, final P2 parameter2, P3 parameter3, final TriParametizedWriteFunction<P1, P2, P3> writeFunction) {
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      writeFunction.write(buffer, dstOffset, length, parameter1, parameter2, parameter3);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeLong(final long value, final ByteOrder byteOrder) {
    final int length = 8;
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putLong(dstOffset, value, byteOrder);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeLongs(final long value1, final long value2, final ByteOrder byteOrder) {
    final int length = 16;
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putLong(dstOffset, value1, byteOrder);
      buffer.putLong(dstOffset + 8, value2, byteOrder);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeLongs(final long value1, final long value2, final long value3, final ByteOrder byteOrder) {
    final int length = 24;
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putLong(dstOffset, value1, byteOrder);
      buffer.putLong(dstOffset + 8, value2, byteOrder);
      buffer.putLong(dstOffset + 16, value3, byteOrder);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public int writeLongs(final long value1, final long value2, final long value3, final long value4, final ByteOrder byteOrder) {
    final int length = 32;
    final int dstOffset = reserve(length);
    if(dstOffset < 0)
      return NULL_OFFSET;

    try {
      buffer.putLong(dstOffset, value1, byteOrder);
      buffer.putLong(dstOffset + 8, value2, byteOrder);
      buffer.putLong(dstOffset + 16, value3, byteOrder);
      buffer.putLong(dstOffset + 24, value4, byteOrder);
    } finally {
      wrote(length);
    }

    return dstOffset;
  }

  @Override
  public AtomicBuffer getBuffer() {
    return buffer;
  }

  @Override
  public int reserve(int length) {
    long offset;
    do {
      offset = nextWriteOffset.get();
      if(offset >= fileSize) {
        // offset exceeded capacity field, do not attempt to increment nextWriteOffset field, nothing more can ever be written
        // no outside write cycle, increment local writes complete now
        return NULL_OFFSET;
      }
    } while(!nextWriteOffset.compareAndSet(offset, offset + length));

    if(offset + length > fileSize) {
      // first message that will not fit
      // set final getFile size
      finalFileSize.set(offset);
      // increment writeComplete so it will still eventually match nextWriteOffset at exceeded capacity value
      wrote(length);
      return NULL_OFFSET;
    }

    // return offset to write bytes
    return (int)offset;
  }

  @Override
  public void wrote(int length) {
    long lastVal;
    do {
      lastVal = writeComplete.get();
    } while(!writeComplete.compareAndSet(lastVal, lastVal + length));
  }

  @Override
  public int capacity() {
    return (int)fileSize;
  }

  @Override
  public boolean hasAvailableCapacity() {
    return nextWriteOffset.get() < fileSize;
  }
}
