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

package com.tang.intellij.lua.comment.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.LuaCommentUtil
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.psi.LuaClassMethodDefStat
import com.tang.intellij.lua.psi.LuaCommentOwner
import com.tang.intellij.lua.psi.LuaTypes
import com.tang.intellij.lua.psi.LuaVisitor
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

/**
 * Created by Tangzx on 2016/11/21.
 *
 */
class LuaCommentImpl(node: ASTNode) : ASTWrapperPsiElement(node), LuaComment {
    override fun findGenericDefs(): Collection<LuaDocGenericDef> {
        val typeGenericDefList = (PsiTreeUtil.getChildOfType(this, LuaDocTagClass::class.java))?.genericDefList
                ?: PsiTreeUtil.getChildOfType(this, LuaDocTagAlias::class.java)?.genericDefList

        if (typeGenericDefList != null) {
            return typeGenericDefList
        }

        val genericDefs = mutableListOf<LuaDocGenericDef>()

        findTags(LuaDocTagGenericList::class.java).forEach {
            genericDefs.addAll(it.genericDefList)
        }

        return genericDefs
    }

    override fun <T : LuaDocPsiElement> findTag(t: Class<T>): T? {
        return PsiTreeUtil.getChildOfType(this, t)
    }

    override fun <T : LuaDocPsiElement> findTags(t:Class<T>): Collection<T> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, t)
    }

    override fun findTags(name: String): Collection<LuaDocTagDef> {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LuaDocTagDef::class.java).filter { it.tagName.text == name }
    }

    override fun getTokenType(): IElementType {
        return LuaTypes.DOC_COMMENT
    }

    override val owner: LuaCommentOwner?
        get() = LuaCommentUtil.findOwner(this)

    override val moduleName: String?
        get() {
            val classDef = PsiTreeUtil.getChildOfType(this, LuaDocTagClass::class.java)
            if (classDef != null && classDef.module != null) {
                return classDef.name
            }
            return null
        }

    override val isDeprecated: Boolean
        get() = findTags("deprecated").isNotEmpty()

    override val isFunctionImplementation: Boolean
        get() = (PsiTreeUtil.getChildOfType(this, LuaDocTagOverload::class.java)
                ?: PsiTreeUtil.getChildOfType(this, LuaDocTagParam::class.java)
                ?: PsiTreeUtil.getChildOfType(this, LuaDocTagReturn::class.java)
                ?: PsiTreeUtil.getChildOfType(this, LuaDocTagVararg::class.java)) != null

    override fun getParamDef(name: String): LuaDocTagParam? {
        var element: PsiElement? = firstChild
        while (element != null) {
            if (element is LuaDocTagParam) {
                val nameRef = element.paramNameRef
                if (nameRef != null && nameRef.text == name)
                    return element
            }
            element = element.nextSibling
        }
        return null
    }

    override fun getFieldDef(name: String): LuaDocTagField? {
        var element: PsiElement? = firstChild
        while (element != null) {
            if (element is LuaDocTagField) {
                val nameRef = element.name
                if (nameRef != null && nameRef == name)
                    return element
            }
            element = element.nextSibling
        }
        return null
    }

    override val tagClass: LuaDocTagClass? =
        PsiTreeUtil.getStubChildOfType(this, LuaDocTagClass::class.java)

    override val tagReturn: LuaDocTagReturn? =
        PsiTreeUtil.getChildOfType(this, LuaDocTagReturn::class.java)

    override val tagType: LuaDocTagType? =
        PsiTreeUtil.getStubChildOfType(this, LuaDocTagType::class.java)

    override val tagVararg: LuaDocTagVararg? =
        PsiTreeUtil.getChildOfType(this, LuaDocTagVararg::class.java)

    override val overloads: Array<IFunSignature>?
        get() {
            val list = mutableListOf<IFunSignature>()
            val colonCall = (owner as? LuaClassMethodDefStat)?.isStatic == false
            var element: PsiElement? = firstChild

            while (element != null) {
                if (element is LuaDocTagOverload) {
                    val fty = element.functionTy

                    if (fty != null) {
                        list.add(TyDocFunSignature(fty, colonCall))
                    }
                }

                element = element.nextSibling
            }

            return if (list.size != 0) list.toTypedArray() else null
        }

    override fun guessType(context: SearchContext): ITy {
        val classDef = tagClass
        if (classDef != null)
            return classDef.type
        val typeDef = tagType
        return if (typeDef != null) {
            if (context.supportsMultipleResults) typeDef.getType() else typeDef.getType(context.index)
        } else Primitives.UNKNOWN
    }

    override fun isOverride(): Boolean {
        var elem = firstChild
        while (elem != null) {
            if (elem is LuaDocTagDef) {
                if (elem.text == "override") return true
            }
            elem = elem.nextSibling
        }
        return false
    }

    override fun createSubstitutor(context: SearchContext): ITySubstitutor? {
        val list = findGenericDefs().map {
            TyGenericParameter(it)
        }

        return if (list.isEmpty()) {
            null
        } else {
            val map = list.associateBy { it.varName }

            object : TySubstitutor() {
                override val name = "name substitutor"

                override fun substitute(context: SearchContext, clazz: ITyClass): ITy {
                    return map[clazz.className] ?: super.substitute(context, clazz)
                }
            }
        }
    }

    override fun toString(): String {
        return "DOC_COMMENT"
    }

    fun accept(visitor: LuaVisitor) {
        visitor.visitComment(this)
    }

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is LuaVisitor) accept(visitor)
        else super.accept(visitor)
    }
}
