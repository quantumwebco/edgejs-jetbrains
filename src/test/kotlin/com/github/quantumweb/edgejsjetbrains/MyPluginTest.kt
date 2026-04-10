package com.github.quantumweb.edgejsjetbrains

import com.github.quantumweb.edgejsjetbrains.edge.EdgeFileType
import com.github.quantumweb.edgejsjetbrains.edge.EdgeDirectiveSupport
import com.github.quantumweb.edgejsjetbrains.edge.EdgeLanguage
import com.github.quantumweb.edgejsjetbrains.edge.highlighting.EdgeTextAttributes
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeLexer
import com.github.quantumweb.edgejsjetbrains.edge.lexer.EdgeTokenTypes
import com.github.quantumweb.edgejsjetbrains.edge.lexer.findMatchingClosingParen
import com.github.quantumweb.edgejsjetbrains.edge.references.EdgeTagReferenceFactory
import com.intellij.codeInsight.highlighting.HighlightedReference
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.ReferenceRange
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil

class MyPluginTest : BasePlatformTestCase() {
    fun testEdgeFileKeepsHtmlPsiAvailable() {
        val psiFile = myFixture.configureByText(
            EdgeFileType,
            """
            @if(user)
            <div class="{{ classes }}">Hello</div>
            @end
            """.trimIndent(),
        )

        val htmlPsi = psiFile.viewProvider.getPsi(HtmlFileType.INSTANCE.language)
        val xmlFile = assertInstanceOf(htmlPsi, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))
        assertEquals("div", xmlFile.rootTag?.name)
        assertEquals("Hello", xmlFile.rootTag?.value?.text?.trim())
    }

    fun testEdgeLexerRecognizesCoreSyntax() {
        val tokenTypes = lex(
            """
            <section>
            {{-- comment --}}
            {{ user.username }}
            {{{ html.safe(snippet) }}}
            Edge should not parse @{{ username }}
            @!component('button', { size: 'large' })
            @debugger
            </section>
            """.trimIndent(),
        )

        assertContainsElements(tokenTypes, EdgeTokenTypes.CONTENT)
        assertContainsElements(tokenTypes, EdgeTokenTypes.COMMENT)
        assertContainsElements(tokenTypes, EdgeTokenTypes.MUSTACHE)
        assertContainsElements(tokenTypes, EdgeTokenTypes.SAFE_MUSTACHE)
        assertContainsElements(tokenTypes, EdgeTokenTypes.ESCAPED_MUSTACHE)
        assertContainsElements(tokenTypes, EdgeTokenTypes.TAG)
    }

    fun testEditorHighlighterProviderIsRegisteredAndSplitsDirectiveToken() {
        val file = myFixture.configureByText(EdgeFileType, "@if(user)\n<div>Hello</div>")
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
        highlighter.setText(file.text)
        val iterator = highlighter.createIterator(0)

        assertEquals(0, iterator.start)
        assertTrue(iterator.end > iterator.start)
        assertEquals(dumpHighlightTokens(file.text, highlighter, 0), "@", file.text.substring(iterator.start, iterator.end))
        assertContainsElements(iterator.textAttributesKeys.toList(), EdgeTextAttributes.DIRECTIVE)

        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, 0), "if", file.text.substring(iterator.start, iterator.end))
        assertContainsElements(iterator.textAttributesKeys.toList(), EdgeTextAttributes.DIRECTIVE)

        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, 0), "(", file.text.substring(iterator.start, iterator.end))
        assertContainsElements(iterator.textAttributesKeys.toList(), EdgeTextAttributes.DIRECTIVE)
    }

    fun testEditorHighlighterUsesEmbeddedLanguageForMustacheContent() {
        val file = myFixture.configureByText(EdgeFileType, "<div>{{ user.username }}</div>")
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
        highlighter.setText(file.text)
        val mustacheOffset = file.text.indexOf("{{")
        val iterator = highlighter.createIterator(0)

        while (!iterator.atEnd() && file.text.substring(iterator.start, iterator.end) != "{{") {
            iterator.advance()
        }

        assertEquals(dumpHighlightTokens(file.text, highlighter, mustacheOffset), "{{", file.text.substring(iterator.start, iterator.end))
        assertContainsElements(iterator.textAttributesKeys.toList(), EdgeTextAttributes.MUSTACHE)

        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, mustacheOffset), " ", file.text.substring(iterator.start, iterator.end))
        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, mustacheOffset), "user", file.text.substring(iterator.start, iterator.end))
        assertFalse(iterator.textAttributesKeys.contains(EdgeTextAttributes.MUSTACHE))

        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, mustacheOffset), ".", file.text.substring(iterator.start, iterator.end))

        iterator.advance()
        assertEquals(dumpHighlightTokens(file.text, highlighter, mustacheOffset), "username", file.text.substring(iterator.start, iterator.end))
    }

    fun testEditorHighlighterUsesEmbeddedLanguageForLetDirectiveArguments() {
        val file = myFixture.configureByText(
            EdgeFileType,
            """
            @let(inputValue = useOldValue
              ? ${'$'}context.oldValue || ${'$'}props.get('value') || ''
              : ${'$'}props.get('value') || ''
            )
            """.trimIndent(),
        )
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
        highlighter.setText(file.text)
        val iterator = highlighter.createIterator(0)

        while (!iterator.atEnd() && file.text.substring(iterator.start, iterator.end) != "'value'") {
            iterator.advance()
        }

        assertFalse(dumpHighlightTokens(file.text, highlighter, 0), iterator.atEnd())
        assertFalse(iterator.textAttributesKeys.contains(EdgeTextAttributes.DIRECTIVE))
        assertTrue(dumpHighlightTokens(file.text, highlighter, 0), iterator.textAttributesKeys.isNotEmpty())
    }

    fun testEditorHighlighterDoesNotSplitLeadingDirectiveArgumentIdentifiers() {
        val directives = listOf(
            "@if(stepErrors.clann_member)" to "stepErrors",
            "@let(stepErrors = errors ?? {})" to "stepErrors",
            "@each(message in stepErrors.clann_member)" to "message",
        )

        for ((source, identifier) in directives) {
            val file = myFixture.configureByText(EdgeFileType, source)
            val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file.virtualFile)
            highlighter.setText(file.text)
            val identifierStart = file.text.indexOf(identifier)
            val identifierEnd = identifierStart + identifier.length
            val tokens = collectHighlightTokens(file.text, highlighter)
            val overlappingTokens = tokens.filter { it.end > identifierStart && it.start < identifierEnd }

            assertTrue(dumpHighlightTokens(file.text, highlighter, 0), overlappingTokens.isNotEmpty())
            assertEquals(dumpHighlightTokens(file.text, highlighter, 0), identifierStart, overlappingTokens.first().start)
            assertEquals(dumpHighlightTokens(file.text, highlighter, 0), identifierEnd, overlappingTokens.last().end)
            assertEquals(
                dumpHighlightTokens(file.text, highlighter, 0),
                listOf(identifier),
                overlappingTokens.map { file.text.substring(it.start, it.end) },
            )
        }
    }

    fun testJavaScriptFileHighlighterTokenDump() {
        val expression = "${'$'}props.get('route')\nclasses = [activeClass]"
        val jsFile = myFixture.configureByText("sample.js", expression)
        val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, jsFile.virtualFile)
        highlighter.setText(expression)

        val iterator = highlighter.createIterator(0)
        var sawStringToken = false
        var stringTokenWasHighlighted = false

        while (!iterator.atEnd()) {
            if (expression.substring(iterator.start, iterator.end) == "'route'") {
                sawStringToken = true
                stringTokenWasHighlighted = iterator.textAttributesKeys.isNotEmpty()
                break
            }
            iterator.advance()
        }

        assertTrue(dumpHighlightTokens(expression, highlighter, 0), sawStringToken)
        assertTrue(dumpHighlightTokens(expression, highlighter, 0), stringTokenWasHighlighted)
    }

    fun testOptionalJavaScriptInjectorIsRegisteredWhenJavaScriptPluginIsAvailable() {
        val hasJavaScriptLanguage = com.intellij.lang.Language.findLanguageByID("JavaScript") != null
        val injectors = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(project)

        if (hasJavaScriptLanguage) {
            assertTrue(injectors.any { it.javaClass.name == "com.github.quantumweb.edgejsjetbrains.edge.injection.EdgeJavaScriptInjector" })
        }
    }

    fun testDirectiveArgumentInjectionMapsLeadingIdentifierStartToInjectedCode() {
        val hasJavaScriptLanguage = com.intellij.lang.Language.findLanguageByID("JavaScript") != null
        if (!hasJavaScriptLanguage) return

        val file = myFixture.configureByText(EdgeFileType, "@if(stepErrors.clann_member)")
        val hostOffset = file.text.indexOf("stepErrors")
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val hostElement = myFixture.file.viewProvider.getPsi(EdgeLanguage)?.findElementAt(hostOffset)
        val injectedFile = hostElement
            ?.let { InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(it) }
            ?.firstOrNull()
            ?.first
            ?.containingFile

        assertNotNull(injectedFile)

        val documentWindow = PsiDocumentManager.getInstance(project).getDocument(injectedFile!!) as? com.intellij.injected.editor.DocumentWindow
        assertNotNull(documentWindow)

        val injectedOffset = documentWindow!!.hostToInjected(hostOffset)
        assertTrue(injectedOffset >= 0)
        assertEquals("stepErrors.clann_member", injectedFile.text)
        assertEquals(injectedFile.text, 's', injectedFile.text[injectedOffset])
    }

    fun testDirectiveArgumentInjectionWrapsLeadingObjectLiteral() {
        val hasJavaScriptLanguage = com.intellij.lang.Language.findLanguageByID("JavaScript") != null
        if (!hasJavaScriptLanguage) return

        val file = myFixture.configureByText(EdgeFileType, "@let({ size: 'large' })")
        val hostOffset = file.text.indexOf('{')
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val hostElement = myFixture.file.viewProvider.getPsi(EdgeLanguage)?.findElementAt(hostOffset)
        val injectedFile = hostElement
            ?.let { InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(it) }
            ?.firstOrNull()
            ?.first
            ?.containingFile

        assertNotNull(injectedFile)
        assertEquals("({ size: 'large' })", injectedFile!!.text)
    }

    fun testInlineDirectiveIsRecognizedAwayFromLineStart() {
        val tokenTypes = lex("Hello @let(username = 'virk') {{ username }}")

        assertContainsElements(tokenTypes, EdgeTokenTypes.TAG)
        assertContainsElements(tokenTypes, EdgeTokenTypes.MUSTACHE)
    }

    fun testIncludeDirectiveResolvesToEdgeTemplate() {
        val target = myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("template.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("header") + 3)

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(target.virtualFile.path, reference?.resolve()?.containingFile?.virtualFile?.path)
        assertEquals("partials/header", referencedText(reference))
    }

    fun testIncludeDirectiveStringReferenceIsHighlighted() {
        myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("template-highlight.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("header") + 3)

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertInstanceOf(reference, HighlightedReference::class.java)
        assertEquals("partials/header", referencedText(reference))
    }

    fun testIncludeDirectiveCtrlMouseHighlightsStringRange() {
        myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("template-ctrl-hover.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val caretOffset = myFixture.file.text.indexOf("header") + 3
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val ctrlMouseData = GotoDeclarationOrUsageHandler2.getCtrlMouseData(myFixture.editor, myFixture.file, caretOffset)
        assertNotNull(ctrlMouseData)
        assertTrue(ctrlMouseData!!.isNavigatable)
        assertEquals(listOf("partials/header"), ctrlMouseData.ranges.map { myFixture.file.text.substring(it.startOffset, it.endOffset) })
    }

    fun testComponentTagDirectiveResolvesToComponentTemplate() {
        val target = myFixture.addFileToProject("resources/views/components/button.edge", "<button>OK</button>")

        val source = myFixture.addFileToProject("component.edge", "@!button({ size: 'large' })")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("button") + 3)

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(target.virtualFile.path, reference?.resolve()?.containingFile?.virtualFile?.path)
        assertEquals("button", referencedText(reference))
    }

    fun testBlockComponentDirectiveResolvesToComponentTemplate() {
        val target = myFixture.addFileToProject("resources/views/components/link.edge", "<a>Link</a>")

        val source = myFixture.addFileToProject(
            "block-component.edge",
            """
            @link({ route: 'home', text: 'Home' })
              <span>Home</span>
            @end
            """.trimIndent(),
        )
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("link") + 2)

        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull(reference)
        assertEquals(target.virtualFile.path, reference?.resolve()?.containingFile?.virtualFile?.path)
        assertEquals("link", referencedText(reference))
    }

    fun testIncludeDirectiveTargetParsingFindsStringRange() {
        val target = EdgeDirectiveSupport.findIncludeDirectiveTarget("@include('partials/header')")

        assertNotNull(target)
        assertEquals("partials/header", target?.value)
        assertEquals("partials/header", "@include('partials/header')".substring(target!!.range.startOffset, target.range.endOffset))
    }

    fun testInjectedIncludeReferenceFactoryResolvesTemplateFromHostTag() {
        val target = myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("injected-reference.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val caretOffset = myFixture.file.text.indexOf("header") + 3
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val injectedElement = findInjectedElementAtHostOffset(caretOffset)
        assertNotNull(injectedElement)

        val references = EdgeTagReferenceFactory.createInjectedStringReferences(injectedElement!!)
        assertEquals(1, references.size)
        assertEquals(target.virtualFile.path, references.single().resolve()?.containingFile?.virtualFile?.path)
        assertEquals("partials/header", referencedText(references.single()))
    }

    fun testInjectedIncludeReferenceFactoryUsesInjectedPsiRange() {
        val target = myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("injected-resolver.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val caretOffset = myFixture.file.text.indexOf("header") + 3
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val injectedElement = findInjectedElementAtHostOffset(caretOffset)
        assertNotNull(injectedElement)

        val references = EdgeTagReferenceFactory.createInjectedStringReferences(injectedElement!!)
        val reference = references.singleOrNull()

        assertNotNull(reference)
        assertEquals(target.virtualFile.path, reference?.resolve()?.containingFile?.virtualFile?.path)
        assertEquals("partials/header", referencedText(reference))
        assertEquals("'partials/header'", reference?.element?.text)
    }

    fun testInjectedIncludeResolvesFromInjectedPsiReference() {
        val target = myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")

        val source = myFixture.addFileToProject("injected-js-reference.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val caretOffset = myFixture.file.text.indexOf("header") + 3
        myFixture.editor.caretModel.moveToOffset(caretOffset)

        val injectedElement = findInjectedElementAtHostOffset(caretOffset)
        assertNotNull(injectedElement)
        assertTrue(injectedElement?.language != EdgeLanguage)

        val leafWithReference = generateSequence(injectedElement) { it.parent }
            .takeWhile { it.containingFile == injectedElement?.containingFile }
            .firstOrNull { it.reference != null }

        assertNotNull(leafWithReference)
        assertEquals(target.virtualFile.path, leafWithReference?.reference?.resolve()?.containingFile?.virtualFile?.path)
        assertEquals("partials/header", referencedText(leafWithReference?.reference))
    }

    fun testReferenceDebugUsesEdgePsiAtCaret() {
        val source = myFixture.addFileToProject("debug.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("header") + 3)

        val edgePsi = myFixture.file.viewProvider.getPsi(EdgeLanguage)
        val mainElement = myFixture.file.findElementAt(myFixture.caretOffset)
        val edgeElement = edgePsi?.findElementAt(myFixture.caretOffset)

        assertNotNull(edgePsi)
        assertNotNull(mainElement)
        assertNotNull(edgeElement)
        assertEquals(EdgeLanguage, mainElement?.language)
        assertEquals(EdgeLanguage, edgeElement?.language)
        assertEquals(EdgeTokenTypes.TAG, edgeElement?.node?.elementType)
        assertEquals(1, EdgeTagReferenceFactory.create(edgeElement!!).size)
    }

    fun testDebugFindsReferenceAtIncludeCaret() {
        val target = myFixture.addFileToProject("resources/views/partials/header.edge", "<div>Header</div>")
        val source = myFixture.addFileToProject("debug-include.edge", "@include('partials/header')")
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("header") + 2)

        val sourceElement = myFixture.file.findElementAt(myFixture.caretOffset)
        val directReference = sourceElement?.reference
        val targetReference = TargetElementUtil.findReference(myFixture.editor, myFixture.caretOffset)
        val fileReference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        val edgeElement = myFixture.file.viewProvider.getPsi(EdgeLanguage)?.findElementAt(myFixture.caretOffset)

        assertNotNull(sourceElement)
        assertNotNull(edgeElement)
        assertTrue(debugElement(sourceElement!!).isNotBlank())
        assertTrue(debugElement(edgeElement!!).isNotBlank())
        assertNotNull(fileReference)
        assertEquals(target.virtualFile.path, fileReference?.resolve()?.containingFile?.virtualFile?.path)
        assertNotNull(targetReference ?: directReference ?: fileReference)
    }

    fun testFindMatchingClosingParenSkipsNestedJavaScriptSyntax() {
        val text = """
            @let(value = foo(
              ')',
              ")",
              `template )`,
              bar(/* ) */ 1, 2), // )
              baz
            ))
            """.trimIndent()

        val openParen = text.indexOf('(')
        val closeParen = findMatchingClosingParen(text, openParen)

        assertNotNull(closeParen)
        assertEquals(text.lastIndexOf(')'), closeParen)
    }

    fun testLexerTreatsDirectiveWithNestedJavaScriptSyntaxAsSingleTag() {
        val text = """
            @let(value = foo(
              ')',
              ")",
              `template )`,
              bar(/* ) */ 1, 2), // )
              baz
            ))
            """.trimIndent()

        assertEquals(listOf(EdgeTokenTypes.TAG), lex(text))
    }

    private fun lex(text: String): List<IElementType> {
        val lexer = EdgeLexer()
        val tokenTypes = mutableListOf<IElementType>()

        lexer.start(text)
        while (lexer.tokenType != null) {
            tokenTypes += lexer.tokenType!!
            lexer.advance()
        }

        return tokenTypes
    }

    private fun debugElement(element: PsiElement): String {
        return "${element.javaClass.name}:${element.node?.elementType}:${element.text}"
    }

    private fun findInjectedElementAtHostOffset(hostOffset: Int): PsiElement? {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val hostElement = myFixture.file.viewProvider.getPsi(EdgeLanguage)?.findElementAt(hostOffset) ?: return null
        val injectedFile = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(hostElement)
            ?.firstOrNull()
            ?.first
            ?.containingFile
            ?: return null
        val documentWindow = PsiDocumentManager.getInstance(project).getDocument(injectedFile) ?: return null
        val injectedOffset = documentWindow.getTextLength().let {
            val mapped = (documentWindow as? com.intellij.injected.editor.DocumentWindow)?.hostToInjected(hostOffset) ?: return null
            mapped.coerceIn(0, it)
        }
        return injectedFile.findElementAt(injectedOffset)
            ?: injectedFile.findElementAt((injectedOffset - 1).coerceAtLeast(0))
    }

    private fun referencedText(reference: com.intellij.psi.PsiReference?): String? {
        if (reference == null) return null
        val elementText = reference.element.text
        val range = ReferenceRange.getRanges(reference).singleOrNull() ?: return null
        return elementText.substring(range.startOffset, range.endOffset)
    }

    private fun dumpHighlightTokens(
        text: String,
        highlighter: com.intellij.openapi.editor.highlighter.EditorHighlighter,
        startOffset: Int,
    ): String {
        val iterator = highlighter.createIterator(startOffset)
        val tokens = mutableListOf<String>()
        while (!iterator.atEnd()) {
            val tokenText = text.substring(iterator.start, iterator.end).replace("\n", "\\n")
            tokens += "[$tokenText -> ${iterator.textAttributesKeys.joinToString()}]"
            iterator.advance()
        }
        return tokens.joinToString(" ")
    }

    private fun collectHighlightTokens(
        text: String,
        highlighter: com.intellij.openapi.editor.highlighter.EditorHighlighter,
    ): List<HighlightToken> {
        val iterator = highlighter.createIterator(0)
        val tokens = mutableListOf<HighlightToken>()

        while (!iterator.atEnd()) {
            tokens += HighlightToken(
                start = iterator.start,
                end = iterator.end,
                text = text.substring(iterator.start, iterator.end),
                textAttributesKeys = iterator.textAttributesKeys.toList(),
            )
            iterator.advance()
        }

        return tokens
    }

    private data class HighlightToken(
        val start: Int,
        val end: Int,
        val text: String,
        val textAttributesKeys: List<TextAttributesKey>,
    )
}
