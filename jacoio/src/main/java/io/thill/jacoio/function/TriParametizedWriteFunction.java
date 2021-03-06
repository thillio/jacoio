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
package io.thill.jacoio.function;

import org.agrona.concurrent.AtomicBuffer;

@FunctionalInterface
public interface TriParametizedWriteFunction<P1, P2, P3> {
  void write(AtomicBuffer buffer, int offset, int length, P1 parameter1, P2 parameter2, P3 parameter3);
}
