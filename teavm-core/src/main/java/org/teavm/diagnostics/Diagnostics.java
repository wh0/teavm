/*
 *  Copyright 2014 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.diagnostics;

import org.teavm.model.CallLocation;

/**
 *
 * @author Alexey Andreev <konsoletyper@gmail.com>
 */
public interface Diagnostics {
    void error(CallLocation location, String error, Object... params);

    void warning(CallLocation location, String error, Object... params);
}
