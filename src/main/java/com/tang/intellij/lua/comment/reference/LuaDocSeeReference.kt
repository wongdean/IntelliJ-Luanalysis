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

package com.tang.intellij.lua.comment.reference

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.tang.intellij.lua.comment.psi.LuaDocTagSee
import com.tang.intellij.lua.psi.LuaElementFactory
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.ITyClass

class LuaDocSeeReference(see: LuaDocTagSee) :
        PsiPolyVariantReferenceBase<LuaDocTagSee>(see){

    val id = see.id!!

    override fun getRangeInElement(): TextRange {
        val start = id.node.startOffset - myElement.node.startOffset
        return TextRange(start, start + id.textLength)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val id = LuaElementFactory.createDocTagField(myElement.project, newElementName)
        this.id.replace(id)
        return id
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun multiResolve(incomplete: Boolean): Array<ResolveResult> {
        val list = mutableListOf<ResolveResult>()
        val searchContext = SearchContext.get(myElement.project)
        val type = myElement.typeRef?.resolveType(searchContext) as ITyClass
        LuaClassMemberIndex.processMember(searchContext, type, id.text, true, true) { _, member ->
            list.add(PsiElementResolveResult(member))
            true
        }
        return list.toTypedArray()
    }
}
