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

package com.tang.intellij.lua.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.CustomParsingType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.util.CharTable;
import com.tang.intellij.lua.comment.lexer.LuaDocLexerAdapter;
import com.tang.intellij.lua.comment.parser.LuaDocParser;
import com.tang.intellij.lua.lang.LuaLanguage;
import com.tang.intellij.lua.lang.LuaParserDefinition;
import com.tang.intellij.lua.lexer.LuaLexerAdapter;
import com.tang.intellij.lua.parser.LuaParser;
import com.tang.intellij.lua.stubs.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by tangzx on 2015/11/15.
 * Email:love.tangzx@qq.com
 */
public class LuaElementType extends IElementType {
    public LuaElementType(String debugName) {
        super(debugName, LuaLanguage.INSTANCE);
    }

    public static final CustomParsingType DOC_COMMENT = new CustomParsingType ("DOC_COMMENT", LuaLanguage.INSTANCE) {
        @NotNull
        @Override
        public ASTNode parse(@NotNull CharSequence charSequence, @NotNull CharTable charTable) {
            PsiParser parser = new LuaDocParser();
            PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(
                    new LuaParserDefinition(),
                    new LuaDocLexerAdapter(),
                    charSequence);
            return parser.parse(this, builder);
        }
    };

    public static final LuaStubElementType FUNC_DEF_STAT = new LuaFuncType();
    public static final LuaStubElementType CLASS_METHOD_DEF_STAT = new LuaClassMethodType();
    public static final LuaStubElementType CLASS_FIELD_DEF = new LuaDocTagFieldType();
    public static final LuaStubElementType TYPE_DEF = new LuaDocTagTypeType();
    public static final LuaStubElementType CLASS_DEF = new LuaDocTagClassType();
    public static final LuaStubElementType DOC_TABLE_DEF = new LuaDocTableDefType();
    public static final LuaStubElementType DOC_TABLE_FIELD_DEF = new LuaDocTableFieldType();
    public static final LuaStubElementType DOC_ALIAS = new LuaDocTagAliasType();
    public static final LuaStubElementType DOC_NOT = new LuaDocTagNotType();
    public static final LuaStubElementType TABLE = new LuaTableExprType();
    public static final LuaStubElementType TABLE_FIELD = new LuaTableFieldType();
    public static final LuaStubElementType INDEX = new LuaIndexExprType();
    public static final LuaStubElementType NAME_EXPR = new LuaNameExprType();
    public static final ILazyParseableElementType BLOCK = new LuaBlockElementType();

    static class LuaBlockElementType extends IReparseableElementType {

        LuaBlockElementType() {
            super("LuaBlock", LuaLanguage.INSTANCE);
        }

        @Override
        public ASTNode parseContents(@NotNull ASTNode chameleon) {
            Project project = chameleon.getPsi().getProject();
            PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(
                    project,
                    chameleon,
                    new LuaLexerAdapter(),
                    LuaLanguage.INSTANCE,
                    chameleon.getText());
            PsiParser luaParser = new LuaParser();
            return luaParser.parse(this, builder).getFirstChildNode();
        }

        @Nullable
        @Override
        public ASTNode createNode(CharSequence text) {
            return null;
        }
    }

    public static final LuaLocalDefElementType LOCAL_DEF = new LuaLocalDefElementType();
    public static final LuaParamDefElementType PARAM_DEF = new LuaParamDefElementType();
    public static final LuaLiteralElementType LITERAL_EXPR = new LuaLiteralElementType();
}
