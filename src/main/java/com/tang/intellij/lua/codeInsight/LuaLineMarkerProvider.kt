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

package com.tang.intellij.lua.codeInsight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.FunctionUtil
import com.intellij.util.Query
import com.tang.intellij.lua.comment.psi.LuaDocTagClass
import com.tang.intellij.lua.lang.LuaIcons
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.psi.search.LuaClassInheritorsSearch
import com.tang.intellij.lua.psi.search.LuaOverridingMethodsSearch
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.stubs.index.LuaClassMemberIndex
import com.tang.intellij.lua.ty.TyClass
import com.tang.intellij.lua.ty.guessParentClass

/**
 * line marker
 * Created by tangzx on 2016/12/11.
 */
class LuaLineMarkerProvider : LineMarkerProvider {

    private val daemonSettings = DaemonCodeAnalyzerSettings.getInstance()
    private val colorsManager = EditorColorsManager.getInstance()

    private fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (element is LuaClassMethodName) {
            val methodDef = PsiTreeUtil.getParentOfType(element, LuaTypeMethod::class.java)!!
            val project = methodDef.project
            val context = SearchContext.get(project)
            val type = methodDef.guessParentClass(context)

            //OverridingMethod
            val classMethodNameId = element.id
            if (type != null && classMethodNameId != null) {
                val methodName = methodDef.name!!
                var superType = type.getSuperType(context)

                while (superType != null && superType is TyClass) {
                    ProgressManager.checkCanceled()
                    val superMethod = LuaClassMemberIndex.findMethod(context, superType, methodName)
                    if (superMethod != null) {
                        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                                .setTargets(superMethod)
                                .setTooltipText("Overrides function in ${superType.className}")
                        result.add(builder.createLineMarkerInfo(classMethodNameId))
                        break
                    }
                    superType = superType.getSuperType(context)
                }
            }

            // OverriddenMethod
            val search = LuaOverridingMethodsSearch.search(methodDef)
            if (search.findFirst() != null && classMethodNameId != null) {
                result.add(LineMarkerInfo(
                        classMethodNameId,
                        classMethodNameId.textRange,
                        AllIcons.Gutter.OverridenMethod,
                        null,
                        object : LuaLineMarkerNavigator<PsiElement, LuaTypeMethod<*>>() {

                            override fun getTitle(elt: PsiElement)
                                    = "Choose Overriding Method of ${methodDef.name}"

                            override fun search(elt: PsiElement)
                                    = LuaOverridingMethodsSearch.search(methodDef)
                        },
                        GutterIconRenderer.Alignment.CENTER
                ) { "Overridden Method" })
            }

            //line separator
            if (daemonSettings.SHOW_METHOD_SEPARATORS) {
                //todo : module file method
                val anchor = PsiTreeUtil.firstChild(methodDef)
                val lineSeparator = LineMarkerInfo(anchor,
                        anchor.textRange)
                lineSeparator.separatorColor = colorsManager.globalScheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR)
                lineSeparator.separatorPlacement = SeparatorPlacement.TOP
                result.add(lineSeparator)
            }
        } else if (element is LuaDocTagClass) {
            val classType = element.type
            val project = element.getProject()
            val query = LuaClassInheritorsSearch.search(GlobalSearchScope.allScope(project), project, classType.className)
            if (query.findFirst() != null) {
                val id = element.id
                result.add(LineMarkerInfo(id,
                        id.textRange,
                        AllIcons.Gutter.OverridenMethod,
                        { element.name },
                        object : LuaLineMarkerNavigator<PsiElement, LuaDocTagClass>() {
                            override fun getTitle(elt: PsiElement)
                                    = "Choose Subclass of ${element.name}"

                            override fun search(elt: PsiElement): Query<LuaDocTagClass> {
                                return LuaClassInheritorsSearch.search(GlobalSearchScope.allScope(project), project, element.name)
                            }
                        },
                        GutterIconRenderer.Alignment.CENTER) { "Overridden Method" })
            }

            // class 标记
            val id = element.id
            val startOffset = id.textOffset
            val classIcon = LineMarkerInfo(id,
                    TextRange(startOffset, startOffset),
                    LuaIcons.CLASS,
                    null,
                    null,
                    GutterIconRenderer.Alignment.CENTER)
            { "Class" }
            result.add(classIcon)
        } else if (element is LuaCallExpr) {
            val expr = element.expression
            val reference = expr.reference
            if (reference != null) {
                val resolve = reference.resolve()
                if (resolve != null) {
                    var cur: PsiElement? = element
                    while (cur != null) {
                        ProgressManager.checkCanceled()
                        val bodyOwner = PsiTreeUtil.getParentOfType(cur, LuaFuncBodyOwner::class.java)
                        if (bodyOwner === resolve) {
                            val anchor = PsiTreeUtil.firstChild(element)
                            result.add(LineMarkerInfo<PsiElement>(anchor,
                                    anchor.textRange,
                                    AllIcons.Gutter.RecursiveMethod,
                                    FunctionUtil.constant("Recursive call"),
                                    null,
                                    GutterIconRenderer.Alignment.CENTER) { "Recursive call" })
                            break
                        }
                        cur = bodyOwner
                    }
                }
            }
        } else if (element is LuaReturnStat) {
            val exprList = element.exprList
            if (exprList != null) {
                for (psiElement in exprList.children) {
                    if (psiElement is LuaCallExpr) {
                        val returnKeyWord = element.firstChild
                        result.add(LineMarkerInfo(returnKeyWord,
                                returnKeyWord.textRange,
                                LuaIcons.LineMarker.TailCall,
                                FunctionUtil.constant("Tail call"), null,
                                GutterIconRenderer.Alignment.CENTER) { "Tail call" })
                        break
                    }
                }
            }
        }
    }

    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(elements: MutableList<out PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            ProgressManager.checkCanceled()
            collectNavigationMarkers(element, result)
        }
    }
}
