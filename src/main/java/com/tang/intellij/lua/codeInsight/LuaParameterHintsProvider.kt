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

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.LuaBundle
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*

/**

 * Created by TangZX on 2016/12/14.
 */
@Suppress("UnstableApiUsage")
class LuaParameterHintsProvider : InlayParameterHintsProvider {
    companion object {
        private val ARGS_HINT = Option(
            "lua.hints.show_args_type",
            { LuaBundle.message("ui.hints.show_args_type") },
            true
        )
        private val LOCAL_VARIABLE_HINT = Option(
            "lua.hints.show_local_var_type",
            { LuaBundle.message("ui.hints.show_local_var_type") },
            false
        )
        private val PARAMETER_TYPE_HINT = Option(
            "lua.hints.show_parameter_type",
            { LuaBundle.message("ui.hints.show_parameter_type") },
            false
        )
        private val FUNCTION_HINT = Option(
            "lua.hints.show_function_type",
            { LuaBundle.message("ui.hints.show_function_type") },
            false
        )
        private const val TYPE_INFO_PREFIX = "@TYPE@"
        private var EXPR_HINT = arrayOf(LuaLiteralExpr::class.java,
                LuaBinaryExpr::class.java,
                LuaUnaryExpr::class.java,
                LuaClosureExpr::class.java,
                LuaTableExpr::class.java)
    }

    override fun getParameterHints(psi: PsiElement): List<InlayInfo> {
        val list = ArrayList<InlayInfo>()
        if (psi is LuaCallExpr) {
            if (!ARGS_HINT.get())
                return list
            @Suppress("UnnecessaryVariable")
            val callExpr = psi
            val args = callExpr.args as? LuaListArgs ?: return list
            val exprList = args.expressionList
            val context = SearchContext.get(callExpr.getProject())
            val type = callExpr.guessParentType(context)

            if (type != null) {
                val ty = TyUnion.find(type, ITyFunction::class.java) ?: return list

                // 是否是 inst:method() 被用为 inst.method(self) 形式
                val isInstanceMethodUsedAsStaticMethod = ty.isColonCall && callExpr.isMethodDotCall
                val searchContext = PsiSearchContext(callExpr)

                val sig = ty.matchSignature(searchContext, callExpr)?.substitutedSignature

                sig?.processParameters(null, callExpr.isMethodColonCall) { index, paramInfo ->
                    val expr = exprList.getOrNull(index) ?: return@processParameters false
                    val show =
                            if (index == 0 && isInstanceMethodUsedAsStaticMethod) {
                                true
                            } else PsiTreeUtil.instanceOf(expr, *EXPR_HINT)
                    if (show)
                        list.add(InlayInfo(paramInfo.name, expr.node.startOffset))
                    true
                }
            }
        }
        else if (psi is LuaParamDef) {
            if (PARAMETER_TYPE_HINT.get()) {
                psi.guessType(SearchContext.get(psi.project))?.let {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${it.displayName}", psi.textOffset + psi.textLength))
                }
            }
        }
        else if (psi is LuaLocalDef) {
            if (LOCAL_VARIABLE_HINT.get()) {
                val type = psi.guessType(SearchContext.get(psi.project))
                if (type != null) {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${type.displayName}", psi.textOffset + psi.textLength))
                }
            }
        }
        else if (psi is LuaFuncBodyOwner<*>) {
            val paren = psi.funcBody?.rparen
            if (FUNCTION_HINT.get() && paren != null) {
                val type = psi.guessReturnType(SearchContext.get(psi.project))
                if (type != null) {
                    return listOf(InlayInfo("$TYPE_INFO_PREFIX${type.displayName}", paren.textOffset + paren.textLength))
                }
            }
        }

        return list
    }

    override fun getHintInfo(psiElement: PsiElement): HintInfo? = null

    override fun getDefaultBlackList(): Set<String> {
        return HashSet()
    }

    override fun isBlackListSupported() = false

    override fun getSupportedOptions(): List<Option> {
        return listOf(ARGS_HINT, LOCAL_VARIABLE_HINT, PARAMETER_TYPE_HINT, FUNCTION_HINT)
    }

    override fun getInlayPresentation(inlayText: String): String {
        if (inlayText.startsWith(TYPE_INFO_PREFIX)) {
            return " : ${inlayText.substring(TYPE_INFO_PREFIX.length)}"
        }
        return "$inlayText : "
    }
}
