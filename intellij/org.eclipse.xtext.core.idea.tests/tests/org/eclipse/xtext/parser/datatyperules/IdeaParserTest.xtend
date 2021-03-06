package org.eclipse.xtext.parser.datatyperules

import com.google.common.io.CharStreams
import com.google.inject.Injector
import java.io.InputStream
import java.io.InputStreamReader
import org.eclipse.emf.common.util.URI
import org.eclipse.xtend.lib.annotations.FinalFieldsConstructor
import org.eclipse.xtext.idea.tests.TestDecorator
import org.eclipse.xtext.idea.tests.parsing.AbstractLanguageParsingTestCase
import org.eclipse.xtext.idea.tests.parsing.ModelChecker
import org.eclipse.xtext.parser.datatyperules.idea.lang.DatatypeRulesTestLanguageFileType
import org.eclipse.xtext.parser.datatyperules.idea.lang.DatatypeRulesTestLanguageLanguage
import org.junit.Ignore

@TestDecorator
class IdeaParserTest extends AbstractLanguageParsingTestCase {
	
	IdeaParserTestDelegate delegate
	
	new() {
		super(DatatypeRulesTestLanguageFileType.INSTANCE)
		delegate = new IdeaParserTestDelegate(this)
	}
	
	override protected getTestDataPath() {
		"./testData/parsing/datatyperules/parserTest";
	}
	
	override protected setUp() throws Exception {
		super.setUp
		delegate.setUp
	}
	
}

@Ignore
@FinalFieldsConstructor
class IdeaParserTestDelegate extends ParserTest {
	
	val ModelChecker modelChecker
	
	override setUp() throws Exception {
		super.setUp
		injector = DatatypeRulesTestLanguageLanguage.INSTANCE.getInstance(Injector)
	}
	
	override protected doGetResource(InputStream in, URI uri) throws Exception {
		modelChecker.checkResource(CharStreams.toString(new InputStreamReader(in)), false)
	}
	
}