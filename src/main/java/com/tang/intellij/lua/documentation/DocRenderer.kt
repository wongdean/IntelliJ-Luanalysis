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

package com.tang.intellij.lua.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerUtil
import com.intellij.psi.PsiElement
import com.tang.intellij.lua.comment.psi.*
import com.tang.intellij.lua.comment.psi.api.LuaComment
import com.tang.intellij.lua.search.SearchContext
import com.tang.intellij.lua.ty.*
import java.util.*

inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
    this.append(prefix)
    body()
    this.append(postfix)
}

inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
    wrap("<$tag>", "</$tag>", body)
}

private fun StringBuilder.appendClassLink(clazz: String) {
    DocumentationManagerUtil.createHyperlink(this, clazz, clazz, true)
}

fun renderTy(sb: StringBuilder, ty: ITy, tyRenderer: ITyRenderer) {
    tyRenderer.render(ty, sb)
}

fun renderSignature(sb: StringBuilder, signature: IFunSignature, tyRenderer: TyRenderer) {
    val sig = mutableListOf<String>()
    val params = signature.params
    val varargTy = signature.variadicParamTy

    if (params != null || varargTy != null) {
        params?.forEach {
            sig.add("${it.name}${if (it.optional) "?" else ""}: ${tyRenderer.render(it.ty ?: Primitives.UNKNOWN)}")
        }
        varargTy?.let {
            sig.add("...: ${tyRenderer.render(it)}")
        }
        sb.append("(${sig.joinToString(", <br>        ")})")
    }
    signature.returnTy?.let {
        val parenthesisRequired = tyRenderer.isReturnPunctuationRequired(it)

        sb.append(": ")

        if (parenthesisRequired) {
            sb.append("(")
        }

        tyRenderer.render(it, sb)

        if (parenthesisRequired) {
            sb.append(")")
        }
    }
}

fun renderComment(sb: StringBuilder, comment: LuaComment?, tyRenderer: ITyRenderer) {
    if (comment != null) {
        var child: PsiElement? = comment.firstChild

        sb.append("<div class='content'>")
        val docStrBuilder = StringBuilder()
        val flushDocString = {
            sb.append(markdownToHtml(docStrBuilder.toString()))
            docStrBuilder.setLength(0)
        }
        var seenString = false
        while (child != null) {
            val elementType = child.node.elementType
            if (elementType == LuaDocTypes.STRING) {
                seenString = true
                docStrBuilder.append(child.text)
            }
            else if (elementType == LuaDocTypes.DASHES) {
                if (seenString) {
                    docStrBuilder.append("\n")
                }
            }
            else if (child is LuaDocPsiElement) {
                seenString = false
                when (child) {
                    is LuaDocTagClass -> {
                        flushDocString()
                        renderClassDef(sb, child, tyRenderer)
                    }
                    is LuaDocTagAlias -> {
                        flushDocString()
                        renderAliasDef(sb, child, tyRenderer)
                    }
                    is LuaDocTagType -> {
                        flushDocString()
                        renderTypeDef(sb, child, tyRenderer)
                    }
                    is LuaDocTagField -> {}
                    is LuaDocTagSee -> {}
                    is LuaDocTagParam -> {}
                    is LuaDocTagReturn -> {}
                    is LuaDocTagOverload -> {}
                }
            }
            child = child.nextSibling
        }
        flushDocString()
        sb.append("</div>")

        val sections = StringBuilder()
        sections.append("<table class='sections'>")
        //Tags
        renderTagList(sections, "Version", comment)
        renderTagList(sections, "Author", comment)
        renderTagList(sections, "Since", comment)
        renderTagList(sections, "Deprecated", comment)
        //Fields
        val fields = comment.findTags(LuaDocTagField::class.java)
        renderTagList(sections, "Fields", fields) { renderFieldDef(sections, it, tyRenderer) }
        //Parameters
        val docParams = comment.findTags(LuaDocTagParam::class.java)
        renderTagList(sections, "Parameters", docParams) { renderDocParam(sections, it, tyRenderer) }
        //Returns
        val retTag = comment.findTag(LuaDocTagReturn::class.java)
        retTag?.let { renderTagList(sections, "Returns", listOf(retTag)) { renderReturn(sections, it, tyRenderer) } }
        //Overloads
        val overloads = comment.findTags(LuaDocTagOverload::class.java)
        renderTagList(sections, "Overloads", overloads) { renderOverload(sections, it, tyRenderer) }
        //See
        val seeTags = comment.findTags(LuaDocTagSee::class.java)
        renderTagList(sections, "See", seeTags) { renderSee(sections, it, tyRenderer) }

        sb.append(sections.toString())
        sb.append("</table>")
    }
}

private fun renderReturn(sb: StringBuilder, tagReturn: LuaDocTagReturn, tyRenderer: ITyRenderer) {
    val returnType = tagReturn.functionReturnType
    if (returnType != null) {
        val list = returnType.returnListList
        list.forEachIndexed { index, returnList ->
            renderDocType(if (index != 0) " | " else null, null, sb, returnList, tyRenderer)
            sb.append(" ")
        }
        renderCommentString(" - ", null, sb, tagReturn.commentString)
    }
}

fun renderAliasDef(sb: StringBuilder, tag: LuaDocTagAlias, tyRenderer: ITyRenderer) {
    val cls = tag.type
    sb.append("<pre>alias ")
    sb.wrapTag("b") { tyRenderer.render(cls, sb) }
    sb.append("</pre>")
    renderCommentString(" - ", null, sb, tag.commentString)
}

fun renderClassDef(sb: StringBuilder, tag: LuaDocTagClass, tyRenderer: ITyRenderer) {
    val cls = tag.type
    sb.append("<pre>")
    sb.append(if (tag.isShape) "shape " else "class ")
    sb.wrapTag("b") { tyRenderer.render(cls, sb) }

    cls.superClass?.let { superClass ->
        sb.append(" : ")

        if (superClass is ITyClass) {
            sb.appendClassLink(superClass.className)
            superClass.params?.let { sb.append("&lt;${it.joinToString(", ")}>") }
        } else {
            sb.append(superClass)
        }
    }

    sb.append("</pre>")
    renderCommentString(" - ", null, sb, tag.commentString)
}

private fun renderFieldDef(sb: StringBuilder, tagField: LuaDocTagField, tyRenderer: ITyRenderer) {
    val name = tagField.name ?: "[${tagField.guessIndexType(SearchContext.get(tagField.project))}]"
    sb.append("${name}: ")
    renderDocType(null, null, sb, tagField.valueType, tyRenderer)
    renderCommentString(" - ", null, sb, tagField.commentString)
}

fun renderDefinition(sb: StringBuilder, block: () -> Unit) {
    sb.append("<div class='definition'><pre>")
    block()
    sb.append("</pre></div>")
}

private fun renderTagList(sb: StringBuilder, name: String, comment: LuaComment) {
    val tags = comment.findTags(name.lowercase())
    renderTagList(sb, name, tags) { tagDef ->
        tagDef.commentString?.text?.let { sb.append(it) }
    }
}

private fun <T : LuaDocPsiElement> renderTagList(sb: StringBuilder, name: String, tags: Collection<T>, block: (tag: T) -> Unit) {
    if (tags.isEmpty())
        return
    sb.wrapTag("tr") {
        sb.append("<td valign='top' class='section'><p>$name</p></td>")
        sb.append("<td valign='top'>")
        for (tag in tags) {
            sb.wrapTag("p") {
                block(tag)
            }
        }
        sb.append("</td>")
    }
}

fun renderDocParam(sb: StringBuilder, child: LuaDocTagParam, tyRenderer: ITyRenderer, paramTitle: Boolean = false) {
    val paramNameRef = child.paramNameRef
    if (paramNameRef != null) {
        if (paramTitle)
            sb.append("<b>param</b> ")
        sb.append("<code>${paramNameRef.text}</code>: ")
        renderDocType(null, null, sb, child.ty, tyRenderer)
        renderCommentString(" - ", null, sb, child.commentString)
    }
}

fun renderCommentString(prefix: String?, postfix: String?, sb: StringBuilder, child: LuaDocCommentString?) {
    child?.string?.text?.let {
        sb.wrap("<div class='content'>", "</div>") {
            if (prefix != null) sb.append(prefix)
            var html = markdownToHtml(it)
            if (html.startsWith("<p>"))
                html = html.substring(3, html.length - 4)
            sb.append(html)
            if (postfix != null) sb.append(postfix)
        }
    }
}

private fun renderDocType(prefix: String?, postfix: String?, sb: StringBuilder, type: LuaDocType?, tyRenderer: ITyRenderer) {
    if (type != null) {
        if (prefix != null) sb.append(prefix)

        val ty = type.getType()
        val parenthesesRequired = ty is TyFunction || ty is TyMultipleResults

        if (parenthesesRequired) {
            sb.append('(')
        }

        renderTy(sb, ty, tyRenderer)

        if (parenthesesRequired) {
            sb.append(')')
        }

        if (postfix != null) sb.append(postfix)
    }
}

private fun renderOverload(sb: StringBuilder, tagOverload: LuaDocTagOverload, tyRenderer: ITyRenderer) {
    tagOverload.functionTy?.getType()?.let {
        renderTy(sb, it, tyRenderer)
    }
}

private fun renderTypeDef(sb: StringBuilder, tagType: LuaDocTagType, tyRenderer: ITyRenderer) {
    renderTy(sb, tagType.getType(), tyRenderer)
}

private fun renderSee(sb: StringBuilder, see: LuaDocTagSee, tyRenderer: ITyRenderer) {
    val typeRef = see.typeRef

    if (typeRef != null) {
        val ty = typeRef.resolveType(SearchContext.get(typeRef.project))
        renderTy(sb, ty, tyRenderer)
        see.id?.let {
            sb.append("#${it.text}")
        }
    }
}
