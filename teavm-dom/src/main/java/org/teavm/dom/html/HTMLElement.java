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
package org.teavm.dom.html;

import org.teavm.dom.core.Element;
import org.teavm.dom.core.NodeList;
import org.teavm.dom.css.ElementCSSInlineStyle;
import org.teavm.dom.events.EventTarget;
import org.teavm.jso.JSProperty;

/**
 *
 * @author Alexey Andreev <konsoletyper@gmail.com>
 */
public interface HTMLElement extends Element, ElementCSSInlineStyle, EventTarget {
    @Override
    @JSProperty
    NodeList<? extends HTMLElement> getElementsByTagName(String name);

    @JSProperty
    String getTitle();

    @JSProperty
    void setTitle(String title);

    @JSProperty
    String getLang();

    @JSProperty
    void setLang(String lang);

    @JSProperty
    boolean isTranslate();

    @JSProperty
    void setTranslate(boolean translate);

    @JSProperty
    String getDir();

    @JSProperty
    void setDir(String dir);

    @JSProperty
    boolean isHidden();

    @JSProperty
    void setHidden(boolean hidden);

    void click();

    @JSProperty
    int getTabIndex();

    @JSProperty
    void setTabIndex(int tabIndex);

    void focus();

    void blur();

    @JSProperty
    String getAccessKey();

    @JSProperty
    void setAccessKey(String accessKey);

    @JSProperty
    String getAccessKeyLabel();

    @JSProperty
    int getClientWidth();

    @JSProperty
    int getClientHeight();

    @JSProperty
    int getAbsoluteLeft();

    @JSProperty
    int getAbsoluteTop();

    @JSProperty
    int getScrollLeft();

    @JSProperty
    int getScrollTop();

    @JSProperty
    @Override
    HTMLDocument getOwnerDocument();
}
