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

package com.tang.intellij.lua.search

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.tang.intellij.lua.ext.ILuaTypeInfer
import com.tang.intellij.lua.psi.LuaTypeGuessable
import com.tang.intellij.lua.ty.ITy
import com.tang.intellij.lua.ty.Ty
import java.util.*

/**

 * Created by tangzx on 2017/1/14.
 */
class SearchContext {

    companion object {
        private val threadLocal = object : ThreadLocal<Stack<SearchContext>>() {
            override fun initialValue(): Stack<SearchContext> {
                return Stack()
            }
        }

        fun get(project: Project): SearchContext {
            val stack = threadLocal.get()
            return if (stack.isEmpty()) {
                SearchContext(project)
            } else {
                stack.peek()
            }
        }

        fun get(element: PsiElement): SearchContext {
            val stack = threadLocal.get()
            return stack.find { c -> c.element == element } ?: SearchContext(element)
        }

        fun infer(psi: LuaTypeGuessable): ITy? {
            return with(psi.project) { it.inferAndCache(psi) }
        }

        fun infer(psi: LuaTypeGuessable, context: SearchContext): ITy? {
            return with(context, null) { it.inferAndCache(psi) }
        }

        private fun <T> with(ctx: SearchContext, defaultValue: T, action: (ctx: SearchContext) -> T): T {
            return if (ctx.myInStack) {
                val result = action(ctx)
                result
            } else {
                val stack = threadLocal.get()
                val size = stack.size
                stack.push(ctx)
                ctx.myInStack = true
                val result = try {
                    action(ctx)
                } catch (e: Exception) {
                    defaultValue
                }
                ctx.myInStack = false
                stack.pop()
                assert(size == stack.size)
                result
            }
        }

        private fun <T> with(project: Project, action: (ctx: SearchContext) -> T): T {
            val ctx = get(project)
            return with(ctx, action)
        }

        fun <T> withDumb(project: Project, defaultValue: T, action: (ctx: SearchContext) -> T): T {
            val context = SearchContext(project)
            return withDumb(context, defaultValue, action)
        }

        fun <T> withDumb(ctx: SearchContext, defaultValue: T, action: (ctx: SearchContext) -> T): T {
            return with(ctx, defaultValue) {
                val dumb = it.myDumb
                it.myDumb = true
                val ret = action(it)
                it.myDumb = dumb
                ret
            }
        }
    }

    val project: Project
    val element: PsiElement?

    val index: Int get() = myIndex // Multiple results index
    val supportsMultipleResults: Boolean get() = myMultipleResults

    private var myDumb = false
    private var myIndex = 0
    private var myMultipleResults = false
    private var myInStack = false
    private val myGuardList = mutableListOf<InferRecursionGuard>()
    private val myInferCache = mutableMapOf<LuaTypeGuessable, ITy>()
    private var myScope: GlobalSearchScope? = null

    private constructor(project: Project) {
        this.project = project
        element = null
    }

    private constructor(element: PsiElement) {
        project = element.project
        this.element = element
    }

    fun <T> withIndex(index: Int, supportMultipleResults: Boolean = false, action: () -> T): T {
        val savedIndex = this.index
        val savedMultipleResults = this.supportsMultipleResults
        myIndex = index
        myMultipleResults = supportMultipleResults
        val ret = action()
        myIndex = savedIndex
        myMultipleResults = savedMultipleResults
        return ret
    }

    fun <T> withMultipleResults(action: () -> T): T {
        val savedIndex = this.index
        val savedMultipleResults = this.supportsMultipleResults
        myIndex = -1
        myMultipleResults = true
        val ret = action()
        myIndex = savedIndex
        myMultipleResults = savedMultipleResults
        return ret
    }

    val scope get(): GlobalSearchScope {
        if (isDumb)
            return GlobalSearchScope.EMPTY_SCOPE
        if (myScope == null) {
            myScope = ProjectAndLibrariesScope(project)
        }
        return myScope!!
    }

    val isDumb: Boolean
        get() = myDumb || DumbService.isDumb(project)

    fun <T> withScope(scope: GlobalSearchScope, action: () -> T): T {
        val oriScope = myScope
        myScope = scope
        val ret = action()
        myScope = oriScope
        return ret
    }

    private fun guardExists(psi: PsiElement, type: GuardType): Boolean {
        myGuardList.forEach {
            if (it.check(psi, type)) {
                return true
            }
        }
        return false
    }

    fun withRecursionGuard(psi: PsiElement, type: GuardType, action: () -> ITy?): ITy? {
        if (guardExists(psi, type)) {
            return null
        }
        val guard = createGuard(psi, type)
        if (guard != null)
            myGuardList.add(guard)
        val result = action()
        if (guard != null)
            myGuardList.remove(guard)
        return result
    }

    fun withInferenceGuard(psi: PsiElement, action: () -> ITy?): ITy? {
        val guard = createGuard(psi, GuardType.Inference)
        if (guard != null)
            myGuardList.add(guard)
        val result = action()
        if (guard != null)
            myGuardList.remove(guard)
        return result
    }

    private fun inferAndCache(psi: LuaTypeGuessable): ITy? {
        if (guardExists(psi, GuardType.Inference)) {
            return null
        }

        return if (index == -1) {
            val result = ILuaTypeInfer.infer(psi, this)
            if (result != null) {
                myInferCache[psi] = result
            }
            result
        } else {
            ILuaTypeInfer.infer(psi, this)
        }
    }

    fun getTypeFromCache(psi: LuaTypeGuessable): ITy? {
        return if (index == -1) myInferCache.getOrDefault(psi, null) else null
    }
}
