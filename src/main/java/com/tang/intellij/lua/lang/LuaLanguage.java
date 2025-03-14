/*
 * Copyright (c) 2017. tangzx(love.tangzx@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tang.intellij.lua.lang;

import com.intellij.lang.Language;

/**
 * Created by tangzx on 2015/11/15.
 * Email:love.tangzx@qq.com
 */
public class LuaLanguage extends Language {

    public static final int INDEX_VERSION = 69;

    public static final LuaLanguage INSTANCE = new LuaLanguage();

    public LuaLanguage() {
        super("Lua");
    }
}
