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
package org.teavm.classlib.java.util;

/**
 *
 * @author Alexey Andreev <konsoletyper@gmail.com>
 */
public abstract class TAbstractSet<E> extends TAbstractCollection<E> implements TSet<E> {
    public TAbstractSet() {
        super();
    }

    @Override
    public boolean removeAll(TCollection<?> c) {
        boolean modified = false;
        if (size() < c.size()) {
            for (TIterator<E> iter = iterator(); iter.hasNext();) {
                E elem = iter.next();
                if (c.contains(elem)) {
                    modified = true;
                    iter.remove();
                }
            }
        } else {
            for (TIterator<?> iter = c.iterator(); iter.hasNext();) {
                if (remove(iter.next())) {
                    modified = true;
                }
            }
        }
        return modified;
    }
}
