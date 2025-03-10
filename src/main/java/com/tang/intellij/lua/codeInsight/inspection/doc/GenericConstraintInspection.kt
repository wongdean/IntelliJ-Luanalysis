/*
 * Copyright (c) 2020
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

package com.tang.intellij.lua.codeInsight.inspection.doc

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.psi.LuaPsiTreeUtil
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.ty.GenericAnalyzer
import com.tang.intellij.lua.ty.ProblemUtil
import com.tang.intellij.lua.ty.TyVarianceFlags

fun pluralizedParameter(count: Int): String {
    return if (count == 1) "parameter" else "parameters"
}

class GenericConstraintInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : LuaDocVisitor() {
            private fun validateGenericArguments(typeElement: LuaDocPsiElement, typeRef: LuaDocTypeRef, args: List<LuaDocTy>) {
                val context = PsiSearchContext(typeRef)
                val params = LuaPsiTreeUtil.findType(context, typeRef.text)?.type?.getParams(context)

                if (params != null && params.size > 0) {
                    val genericAnalyzer = GenericAnalyzer(params, context)

                    if (params.size != args.size) {
                        holder.registerProblem(typeElement, "\"${typeRef.text}\" requires ${params.size} generic ${pluralizedParameter(params.size)}", ProblemHighlightType.ERROR)
                    }

                    params.forEachIndexed { index, param ->
                        if (index < args.size) {
                            genericAnalyzer.analyze(context, args[index].getType(), param)
                        }
                    }

                    params.forEachIndexed { index, param ->
                        if (index < args.size) {
                            val argElement = args[index]
                            val arg = argElement.getType()
                            val analyzedParamType = genericAnalyzer.analyzedParams[param.className]

                            if (analyzedParamType != null) {
                                val varianceFlags = TyVarianceFlags.STRICT_UNKNOWN or TyVarianceFlags.ABSTRACT_PARAMS
                                ProblemUtil.contravariantOf(context, analyzedParamType, arg, varianceFlags, null, argElement) { problem ->
                                    holder.registerProblem(problem.sourceElement, problem.message, ProblemHighlightType.ERROR)
                                }
                            }
                        }
                    }
                } else if (typeRef.text == "table") {
                    if (args.size != 0 && args.size != 2) {
                        holder.registerProblem(typeElement, "table requires 2 generic parameters", ProblemHighlightType.ERROR)
                    }
                } else if (args.size != 0) {
                    holder.registerProblem(typeElement, "\"${typeRef.text}\" is not a generic type", ProblemHighlightType.ERROR)
                }
            }

            override fun visitType(o: LuaDocType) {
                if (o is LuaDocGenericTy) {
                    validateGenericArguments(o, o.typeRef, o.tyList)
                } else if (o is LuaDocGeneralTy) {
                    validateGenericArguments(o, o.typeRef, emptyList())
                }
            }
        }
    }
}
