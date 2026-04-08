package com.github.quantumweb.edgejsjetbrains.edge.psi

import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeLexer
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTFactory
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class EdgeParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = EdgeLexer()

    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
            val rootMarker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            rootMarker.done(root)
            builder.treeBuilt
        }
    }

    override fun getFileNodeType(): IFileElementType = EdgeTokenTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY

    override fun getCommentTokens(): TokenSet = EdgeTokenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = EdgePsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = EdgeFile(viewProvider)

    override fun spaceExistenceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        return ParserDefinition.SpaceRequirements.MAY
    }
}

class EdgeAstFactory : ASTFactory() {
    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement {
        return when (type) {
            EdgeTokenTypes.TAG,
            EdgeTokenTypes.MUSTACHE,
            EdgeTokenTypes.SAFE_MUSTACHE,
            -> EdgeInjectionHostLeaf(type, text)
            else -> super.createLeaf(type, text) ?: LeafPsiElement(type, text)
        }
    }

    override fun createComposite(type: IElementType): CompositeElement =
        super.createComposite(type) ?: error("Unable to create composite for $type")
}

internal class EdgePsiElement(node: ASTNode) : ASTWrapperPsiElement(node), ContributedReferenceHost, PsiLanguageInjectionHost {
    override fun getReferences(): Array<PsiReference> = PsiReferenceService.getService().getContributedReferences(this)

    override fun isValidHost(): Boolean {
        return when (node.elementType) {
            EdgeTokenTypes.MUSTACHE, EdgeTokenTypes.SAFE_MUSTACHE, EdgeTokenTypes.TAG -> true
            else -> false
        }
    }

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
        return object : LiteralTextEscaper<EdgePsiElement>(this) {
            override fun decode(rangeInsideHost: com.intellij.openapi.util.TextRange, outChars: StringBuilder): Boolean {
                outChars.append(rangeInsideHost.substring(myHost.text))
                return true
            }

            override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: com.intellij.openapi.util.TextRange): Int {
                val offset = rangeInsideHost.startOffset + offsetInDecoded
                return offset.coerceAtMost(rangeInsideHost.endOffset)
            }

            override fun isOneLine(): Boolean = false
        }
    }
}

internal class EdgeInjectionHostLeaf(
    type: IElementType,
    text: CharSequence,
) : LeafPsiElement(type, text), ContributedReferenceHost, PsiLanguageInjectionHost {
    override fun getReferences(): Array<PsiReference> = PsiReferenceService.getService().getContributedReferences(this)

    override fun isValidHost(): Boolean = true

    override fun updateText(text: String): PsiLanguageInjectionHost = this

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> {
        return object : LiteralTextEscaper<EdgeInjectionHostLeaf>(this) {
            override fun decode(rangeInsideHost: TextRange, outChars: StringBuilder): Boolean {
                outChars.append(rangeInsideHost.substring(myHost.text))
                return true
            }

            override fun getOffsetInHost(offsetInDecoded: Int, rangeInsideHost: TextRange): Int {
                val offset = rangeInsideHost.startOffset + offsetInDecoded
                return offset.coerceAtMost(rangeInsideHost.endOffset)
            }

            override fun isOneLine(): Boolean = false
        }
    }
}
