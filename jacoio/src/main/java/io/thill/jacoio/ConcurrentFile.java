/**
 * Copyright (c) 2019 Eric Thill
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this getFile except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package io.thill.jacoio;

import io.thill.jacoio.function.*;
import io.thill.jacoio.mapper.ConcurrentFileMapper;
import org.agrona.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Maps an underlying getFile to provide concurrent, lock-free write operations.
 *
 * @author Eric Thill
 */
public interface ConcurrentFile extends AutoCloseable {

  static ConcurrentFileMapper map() {
    return new ConcurrentFileMapper();
  }

  int NULL_OFFSET = -1;

  /**
   * Check if there are pending local writes to be completed
   *
   * @return true if there are pending local writes, false otherwise
   */
  boolean isPending();

  /**
   * Check if all pending writes have completed and no more writes can ever be written
   *
   * @return true if no writes can ever be performed
   */
  boolean isFinished();

  /**
   * Mark the getFile as finished, so no more writes can ever be performed. This will populate the fileSize field in the getFile header.
   */
  void finish();

  /**
   * Get the underlying {@link File}
   *
   * @return the underlying getFile
   */
  File getFile();

  /**
   * Write the given bytes from the given offset to the given length
   *
   * @param srcBytes  the source byte array
   * @param srcOffset the offset in the source byte array
   * @param length    the number of bytes to write
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int write(byte[] srcBytes, int srcOffset, int length) throws IOException;

  /**
   * Write the given buffer from the given offset to the given length
   *
   * @param srcBuffer the source buffer
   * @param srcOffset the offset in the source buffer
   * @param length    the number of bytes to write
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int write(DirectBuffer srcBuffer, int srcOffset, int length) throws IOException;

  /**
   * Write the given ByteBuffer from {@link ByteBuffer#position()} with length={@link ByteBuffer#remaining()}. The position of the {@link ByteBuffer} will not
   * be changed.
   *
   * @param srcByteBuffer the source byte buffer
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int write(ByteBuffer srcByteBuffer) throws IOException;

  /**
   * Write the given CharSequence as ascii characters
   *
   * @param srcCharSequence the source character sequence
   * @return the offset at which the characters were written, -1 if it could not fit
   */
  int writeAscii(CharSequence srcCharSequence) throws IOException;

  /**
   * Write the given CharSequence as 2-byte characters
   *
   * @param srcCharSequence the source character sequence
   * @param byteOrder       the destination byte-order
   * @return the offset at which the characters were written, -1 if it could not fit
   */
  int writeChars(CharSequence srcCharSequence, ByteOrder byteOrder) throws IOException;

  /**
   * Write to the underlying buffer using the given {@link WriteFunction}
   *
   * @param length        the total number of bytes that will be written by the {@link WriteFunction}
   * @param writeFunction the write function
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int write(int length, WriteFunction writeFunction) throws IOException;

  /**
   * Write to the underlying buffer using the given {@link ParametizedWriteFunction} with 1 parameter to pass through.
   *
   * @param length        the total number of bytes that will be written by the {@link WriteFunction}
   * @param parameter     the parameter to pass through to the write function
   * @param writeFunction the write function
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  <P> int write(int length, P parameter, ParametizedWriteFunction<P> writeFunction) throws IOException;

  /**
   * Write to the underlying buffer using the given {@link BiParametizedWriteFunction} with 2 parameters to pass through.
   *
   * @param length        the total number of bytes that will be written by the {@link WriteFunction}
   * @param parameter1    the first parameter to pass through to the write function
   * @param parameter2    the second parameter to pass through to the write function
   * @param writeFunction the write function
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  <P1, P2> int write(int length, P1 parameter1, P2 parameter2, BiParametizedWriteFunction<P1, P2> writeFunction) throws IOException;

  /**
   * Write to the underlying buffer using the given {@link TriParametizedWriteFunction} with 3 parameters to pass through.
   *
   * @param length        the total number of bytes that will be written by the {@link WriteFunction}
   * @param parameter1    the first parameter to pass through to the write function
   * @param parameter2    the second parameter to pass through to the write function
   * @param parameter3    the third parameter to pass through to the write function
   * @param writeFunction the write function
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  <P1, P2, P3> int write(int length, P1 parameter1, P2 parameter2, P3 parameter3, TriParametizedWriteFunction<P1, P2, P3> writeFunction) throws IOException;

  /**
   * Write the given long to the underlying buffer
   *
   * @param value     the long value to write
   * @param byteOrder the byte order
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int writeLong(long value, ByteOrder byteOrder) throws IOException;

  /**
   * Write the given longs to the underlying buffer
   *
   * @param value1    the first long value to write
   * @param value2    the second long value to write
   * @param byteOrder the byte order
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int writeLongs(long value1, long value2, ByteOrder byteOrder) throws IOException;

  /**
   * Write the given longs to the underlying buffer
   *
   * @param value1    the first long value to write
   * @param value2    the second long value to write
   * @param value3    the third long value to write
   * @param byteOrder the byte order
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int writeLongs(long value1, long value2, long value3, ByteOrder byteOrder) throws IOException;

  /**
   * Write the given longs to the underlying buffer
   *
   * @param value1    the first long value to write
   * @param value2    the second long value to write
   * @param value3    the third long value to write
   * @param value4    the fourth long value to write
   * @param byteOrder the byte order
   * @return the offset at which the bytes were written, -1 if it could not fit
   */
  int writeLongs(long value1, long value2, long value3, long value4, ByteOrder byteOrder) throws IOException;

  @Override
  void close() throws IOException;

}
