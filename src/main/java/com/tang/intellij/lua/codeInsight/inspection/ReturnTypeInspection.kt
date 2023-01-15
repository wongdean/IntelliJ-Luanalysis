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

package com.tang.intellij.lua.codeInsight.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.tang.intellij.lua.comment.psi.impl.LuaDocTagTypeImpl
import com.tang.intellij.lua.lang.LuaFileType.DEFINITION_FILE_REGEX
import com.tang.intellij.lua.psi.*
import com.tang.intellij.lua.search.PsiSearchContext
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import java.util.*

class ReturnTypeInspection : StrictInspection() {
    override fun buildVisitor(myHolder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        if (session.file.name.matches(DEFINITION_FILE_REGEX)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return object : LuaVisitor() {
            override fun visitReturnStat(o: LuaReturnStat) {
                super.visitReturnStat(o)
                if (o.parent is PsiFile)
                    return

                val context = PsiSearchContext(o)
                val bodyOwner = PsiTreeUtil.getParentOfType(o, LuaFuncBodyOwner::class.java) ?: return
                val expectedReturnTy = ScopedTypeSubstitutor.substitute(
                    context,
                    if (bodyOwner is LuaClassMethodDefStat) {
                        guessSuperReturnTypes(context, bodyOwner)
                    } else {
                        bodyOwner.tagReturn?.type
                    } ?: TyMultipleResults(listOf(Primitives.UNKNOWN), true)
                )

                val expressionTy = context.withMultipleResults {
                    o.exprList?.guessType(context)
                } ?: Primitives.VOID

                val expressionTyLists = if (expressionTy is TyUnion && expressionTy.getChildTypes().any { it is TyMultipleResults }) {
                    expressionTy.getChildTypes().map { toList(it) }
                } else {
                    listOf(toList(expressionTy))
                }

                val statementDocTagType = o.comment?.let { PsiTreeUtil.getChildrenOfTypeAsList(it, LuaDocTagTypeImpl::class.java).firstOrNull() }
                val statementDocTy = statementDocTagType?.getType()

                val processCandidate = fun(expressionTyLists: List<ITy>, candidateReturnTy: ITy): Collection<Problem> {
                    val problems = mutableListOf<Problem>()

                    val abstractTys = toList(statementDocTy ?: candidateReturnTy)
                    val variadicAbstractType = if (candidateReturnTy is TyMultipleResults && candidateReturnTy.variadic) {
                        candidateReturnTy.list.last()
                    } else null

                    for (i in 0 until expressionTyLists.size) {
                        val element = o.exprList?.getExpressionAt(i) ?: o
                        val targetType = abstractTys.getOrNull(i) ?: variadicAbstractType ?: Primitives.VOID
                        val scopedExpressionTy = ScopedTypeSubstitutor.substitute(context, expressionTyLists[i])
                        val varianceFlags = if (element is LuaTableExpr) TyVarianceFlags.WIDEN_TABLES else 0

                        ProblemUtil.contravariantOf(context, targetType, scopedExpressionTy, varianceFlags, null, element) { problem ->
                            val targetMessage = problem.message

                            if (expressionTyLists.size > 1) {
                                problem.message = "Result ${i + 1}, ${targetMessage.replaceFirstChar {
                                    it.lowercase(
                                        Locale.getDefault()
                                    )
                                }}"
                            }

                            problems.add(problem)

                            if (problem.targetElement != null && problem.targetElement != problem.sourceElement) {
                                problems.add(Problem(null, problem.targetElement, targetMessage, problem.highlightType))
                            }
                        }
                    }

                    val abstractReturnCount = if (variadicAbstractType != null) {
                        abstractTys.size - 1
                    } else abstractTys.size

                    val concreteReturnCount = if (expressionTy is TyMultipleResults && expressionTy.variadic) {
                        expressionTyLists.size - 1
                    } else expressionTyLists.size

                    if (concreteReturnCount < abstractReturnCount) {
                        problems.add(
                            Problem(
                                null,
                                o.lastChild,
                                "Incorrect number of values. Expected %s but found %s.".format(abstractReturnCount, concreteReturnCount)
                            )
                        )
                    }

                    if (statementDocTy != null) {
                        val expectedReturnTys = toList(candidateReturnTy)
                        val expectedVariadicReturnTy = if (candidateReturnTy is TyMultipleResults && candidateReturnTy.variadic) {
                            candidateReturnTy.list.last()
                        } else null

                        for (i in 0 until abstractTys.size) {
                            val targetType = expectedReturnTys.getOrNull(i) ?: expectedVariadicReturnTy ?: Primitives.VOID
                            val scopedAbstractTy = ScopedTypeSubstitutor.substitute(context, abstractTys[i])

                            if (!targetType.contravariantOf(context, scopedAbstractTy, 0)) {
                                val element = statementDocTagType.typeList?.tyList?.let { it.getOrNull(i) ?: it.last() } ?: statementDocTagType
                                val message = "Type mismatch. Required: '%s' Found: '%s'".format(targetType.displayName, scopedAbstractTy.displayName)
                                problems.add(Problem(null, element, message))
                            }
                        }

                        val candidateReturnCount = if (expectedVariadicReturnTy != null) {
                            expectedReturnTys.size - 1
                        } else expectedReturnTys.size

                        if (abstractReturnCount < candidateReturnCount) {
                            val element = statementDocTagType.typeList ?: statementDocTagType
                            val message = "Incorrect number of values. Expected %s but found %s.".format(candidateReturnCount, abstractReturnCount)
                            problems.add(Problem(null, element, message))
                        }
                    }

                    return problems
                }

                val multipleCandidates = expectedReturnTy is TyUnion && expectedReturnTy.getChildTypes().any { it is TyMultipleResults }

                for (guessedReturnTyList in expressionTyLists) {
                    if (multipleCandidates) {
                        val candidateProblems = mutableMapOf<String, Collection<Problem>>()
                        var matchFound = false

                        TyUnion.each(expectedReturnTy) {
                            val problems = processCandidate(guessedReturnTyList, it)

                            if (problems.size == 0) {
                                matchFound = true
                                return@each
                            }

                            candidateProblems.put(it.displayName, problems)
                        }

                        if (matchFound) {
                            continue
                        }

                        candidateProblems.forEach { candidate, problems ->
                            problems.forEach {
                                val message = "${it.message} for candidate return type (${candidate})"
                                myHolder.registerProblem(it.sourceElement, message, it.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                            }
                        }
                    } else {
                        processCandidate(guessedReturnTyList, expectedReturnTy).forEach {
                            myHolder.registerProblem(it.sourceElement, it.message, it.highlightType ?: ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                        }
                    }
                }
            }

            private fun toList(type: ITy): List<ITy> {
                return when (type) {
                    Primitives.VOID -> emptyList()
                    is TyMultipleResults -> type.list
                    else -> listOf(type)
                }
            }

            private fun guessSuperReturnTypes(context: SearchContext, function: LuaClassMethodDefStat): ITy? {
                val comment = function.comment
                if (comment != null) {
                    if (comment.isOverride()) {
                        // Find super type
                        val cls = function.guessParentClass(context)
                        val superMember = cls?.getSuperType(context)?.findEffectiveMember(context, function.name ?: "")
                        if (superMember is LuaClassMethodDefStat) {
                            return superMember.guessReturnType(context)
                        }
                    } else {
                        return comment.tagReturn?.type
                    }
                }
                return null
            }

            override fun visitFuncBody(o: LuaFuncBody) {
                super.visitFuncBody(o)

                // If some return type is defined, we require at least one return type
                val returnStat = PsiTreeUtil.findChildOfType(o, LuaReturnStat::class.java)

                if (returnStat == null) {
                    // Find function definition
                    val context = SearchContext.get(o.project)
                    val bodyOwner = PsiTreeUtil.getParentOfType(o, LuaFuncBodyOwner::class.java)

                    val type = if (bodyOwner is LuaClassMethodDefStat) {
                        guessSuperReturnTypes(context, bodyOwner)
                    } else {
                        val returnDef = (bodyOwner as? LuaCommentOwner)?.comment?.tagReturn
                        returnDef?.type
                    }

                    if (type != null && type != Primitives.VOID && o.textLength != 0) {
                        myHolder.registerProblem(o, "Return type '%s' specified but no return values found.".format(type.displayName))
                    }
                }
            }
        }
    }
}
