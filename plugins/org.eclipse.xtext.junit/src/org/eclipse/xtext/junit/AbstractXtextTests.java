/*******************************************************************************
 * Copyright (c) 2008-2010 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.junit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource.Diagnostic;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtend.XtendFacade;
import org.eclipse.xtend.expression.ExecutionContextImpl;
import org.eclipse.xtend.type.impl.java.JavaBeansMetaModel;
import org.eclipse.xtext.Constants;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.ISetup;
import org.eclipse.xtext.conversion.IValueConverterService;
import org.eclipse.xtext.diagnostics.ExceptionDiagnostic;
import org.eclipse.xtext.formatting.INodeModelFormatter;
import org.eclipse.xtext.junit.GlobalRegistries.GlobalStateMemento;
import org.eclipse.xtext.junit.serializer.SerializerTester;
import org.eclipse.xtext.junit.util.ResourceLoadHelper;
import org.eclipse.xtext.linking.ILinkingService;
import org.eclipse.xtext.linking.lazy.LazyLinkingResource;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.parser.IAstFactory;
import org.eclipse.xtext.parser.IParseResult;
import org.eclipse.xtext.parser.IParser;
import org.eclipse.xtext.parsetree.reconstr.IParseTreeConstructor;
import org.eclipse.xtext.resource.IResourceFactory;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.serializer.ISerializer;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.StringInputStream;

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

/**
 * @author Sven Efftinge - Initial contribution and API
 * 
 * @deprecated use org.eclipse.xtext.junit4.XtextRunner instead. This class will be removed in Xtext 2.9.
 */
@Deprecated
public abstract class AbstractXtextTests extends TestCase implements ResourceLoadHelper {

	private Injector injector;
	private boolean canCreateInjector;
	private List<String> testGrammarPaths;
	private GlobalStateMemento globalStateMemento;
	
	static {
		GlobalRegistries.initializeDefaults();
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		canCreateInjector = true;
		globalStateMemento = GlobalRegistries.makeCopyOfGlobalState();
	}

	@Override
	protected void tearDown() throws Exception {
		injector = null;
		globalStateMemento.restoreGlobalState();
		super.tearDown();
	}

	public String serialize(EObject obj) {
		return getSerializer().serialize(obj);
	}

	/**
	 * call this to set the language class to be used in the current test.
	 */
	protected void with(Module ... modules) throws Exception {
		assertTrue("super.setUp() has to be called before any injector is instantiated", canCreateInjector);
		setInjector(Guice.createInjector(modules));
	}

	protected void with(Class<? extends ISetup> setupClazz) throws Exception {
		assertTrue("super.setUp() has to be called before any injector is instantiated", canCreateInjector);
		ISetup instance = setupClazz.newInstance();
		setInjector(instance.createInjectorAndDoEMFRegistration());
	}

	public void with(ISetup setup) throws Exception {
		assertTrue("super.setUp() has to be called before any injector is instantiated", canCreateInjector);
		setInjector(setup.createInjectorAndDoEMFRegistration());
	}
	
	protected void setInjector(Injector injector) {
		this.injector = injector;
	}
	
	final public Injector getInjector() {
		if (injector==null)
			throw new IllegalStateException("No injector set. Did you forget to call something like 'with(new YourStadaloneSetup())'?");
		return injector;
	}

	public<T> T get(Class<T> clazz) {
		if (injector == null)
			injector = Guice.createInjector();
		return injector.getInstance(clazz);
	}

	public<T> T get(Key<T> key) {
		if (injector == null)
			injector = Guice.createInjector();
		return injector.getInstance(key);
	}
	
	public void injectMembers(Object object) {
		if (injector == null)
			injector = Guice.createInjector();
		injector.injectMembers(object);
	}

	protected IParser getParser() {
		return getInjector().getInstance(IParser.class);
	}

	protected ILinkingService getLinkingService() {
		return getInjector().getInstance(ILinkingService.class);
	}

	protected IAstFactory getASTFactory() {
		return getInjector().getInstance(IAstFactory.class);
	}

	protected IGrammarAccess getGrammarAccess() {
		return getInjector().getInstance(IGrammarAccess.class);
	}

	protected IParseTreeConstructor getParseTreeConstructor() {
		return getInjector().getInstance(IParseTreeConstructor.class);
	}

	protected IResourceFactory getResourceFactory()  {
		return getInjector().getInstance(IResourceFactory.class);
	}

	protected IValueConverterService getValueConverterService() {
		return getInjector().getInstance(IValueConverterService.class);
	}

	protected ISerializer getSerializer() {
		return getInjector().getInstance(ISerializer.class);
	}
	
	protected INodeModelFormatter getNodeModelFormatter() {
		return getInjector().getInstance(INodeModelFormatter.class);
	}

	protected IScopeProvider getScopeProvider() {
		return getInjector().getInstance(IScopeProvider.class);
	}
	
	protected InputStream getAsStream(String model) {
		return new StringInputStream(model);
	}
	
	// parse methods

	public EObject getModel(String model) throws Exception {
		return getModel(getAsStream(model));
	}
	
	public final EObject getModel(InputStream model) throws Exception {
		XtextResource resource = getResource(model);
		return getModel(resource);
	}
	
	public static final int EXPECT_ERRORS = -2;
	public static final int UNKNOWN_EXPECTATION = Integer.MIN_VALUE;

	public final EObject getModelAndExpect(String model, int errors) throws Exception {
		return getModelAndExpect(getAsStream(model), errors);
	}
	
	public final EObject getModelAndExpect(InputStream model, int errors) throws Exception {
		XtextResource resource = getResourceAndExpect(model, errors);
		return getModel(resource);
	}

	protected EObject getModel(XtextResource resource) {
		return resource.getParseResult().getRootASTElement();
	}

	protected final XtextResource getResourceFromString(String model) throws Exception {
		return getResource(getAsStream(model));
	}
	
	protected final XtextResource getResourceFromStringAndExpect(String model, int errors) throws Exception {
		return getResourceAndExpect(getAsStream(model), errors);
	}

	public final XtextResource getResource(InputStream in) throws Exception {
		return getResource(in, URI.createURI("mytestmodel."+getCurrentFileExtension()));
	}
	
	protected String getCurrentFileExtension() {
		String instance = getInjector().getInstance(Key.get(String.class,Names.named(Constants.FILE_EXTENSIONS)));
		if (instance.indexOf(',')==-1)
			return instance;
		return instance.split(",")[0];
	}
	
	@Override
	public final XtextResource getResourceFor(InputStream stream) {
		try {
			return getResourceAndExpect(stream, AbstractXtextTests.UNKNOWN_EXPECTATION);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public final XtextResource getResourceAndExpect(InputStream in, int errors) throws Exception {
		return getResourceAndExpect(in, URI.createURI("mytestmodel."+getCurrentFileExtension()), errors);
	}

	public final XtextResource getResource(InputStream in, URI uri) throws Exception {
		return getResourceAndExpect(in, uri, 0);
	}
	
	public final XtextResource getResource(String contents, String uri) throws Exception {
		return getResource(getAsStream(contents), URI.createURI(uri));
	}
	
	public final XtextResource getResourceAndExpect(InputStream in, URI uri, int expectedErrors) throws Exception {
		XtextResource resource = doGetResource(in, uri);
		if (expectedErrors != UNKNOWN_EXPECTATION) {
			if (expectedErrors == EXPECT_ERRORS)
				assertFalse(resource.getErrors().toString(), resource.getErrors().isEmpty());
			else
				assertEquals(resource.getErrors().toString(), expectedErrors, resource.getErrors().size());
		}
		for(Diagnostic d: resource.getErrors()) {
			if (d instanceof ExceptionDiagnostic)
				fail(d.getMessage());
		}

		for(Diagnostic d: resource.getWarnings())
			System.out.println("Resource Warning: "+d);
				
		if (expectedErrors == 0 && resource.getContents().size() > 0 && shouldTestSerializer(resource)) {
			SerializerTester tester = get(SerializerTester.class);
			EObject obj = resource.getContents().get(0);
			tester.assertSerializeWithNodeModel(obj);
			tester.assertSerializeWithoutNodeModel(obj);
		}

		return resource;
	}

	protected boolean shouldTestSerializer(XtextResource resource) {
		return !("org.eclipse.xtext.Xtext".equals(resource.getLanguageName()) 
				// TODO: fix serializer issues in refactoring tests
				|| "org.eclipse.xtext.ui.tests.refactoring.RefactoringTestLanguage".equals(resource.getLanguageName()));
	}

	protected XtextResource doGetResource(InputStream in, URI uri) throws Exception {
		XtextResourceSet rs = get(XtextResourceSet.class);
		rs.setClasspathURIContext(getClass());
		XtextResource resource = (XtextResource) getResourceFactory().createResource(uri);
		rs.getResources().add(resource);
		resource.load(in, null);
		if (resource instanceof LazyLinkingResource) {
			((LazyLinkingResource) resource).resolveLazyCrossReferences(CancelIndicator.NullImpl);
		} else {
			EcoreUtil.resolveAll(resource);
		}
		return resource;
	}

	protected final ICompositeNode getRootNode(InputStream model) throws Exception {
		XtextResource resource = getResource(model);
		return getRootNode(resource);
	}
	
	protected final ICompositeNode getRootNodeAndExpect(InputStream model, int errors) throws Exception {
		XtextResource resource = getResourceAndExpect(model, errors);
		return getRootNode(resource);
	}

	protected final ICompositeNode getRootNode(XtextResource resource) {
		return resource.getParseResult().getRootNode();
	}

	protected final IParseResult getParseResult(String model) throws Exception {
		return getResourceFromString(model).getParseResult();
	}
	
	protected final IParseResult getParseResultAndExpect(String model, int errors) throws Exception {
		return getResourceFromStringAndExpect(model, errors).getParseResult();
	}
	
	protected final ICompositeNode getRootNode(String model) throws Exception {
		return getRootNode(getAsStream(model));
	}
	
	protected final ICompositeNode getRootNodeAndExpect(String model, int errors) throws Exception {
		return getRootNodeAndExpect(getAsStream(model), errors);
	}
	
	// Xtend helper methods

	protected void assertWithXtend(String left, String right, Object _this) {
		assertWithXtend(left + " != " + right, left, right, _this);
	}

	protected Object invokeWithXtend(String expression, Object _this) {
		XtendFacade f = getXtendFacade();
		f = f.cloneWithExtensions(getImportDeclarations() + "invoke(Object this) : " + expression + ";");
		return f.call("invoke", _this);
	}

	protected String[] importedExtensions() {
		return new String[0];
	}

	protected void assertWithXtend(String message, String left, String right, Object _this) {
		XtendFacade f = getXtendFacade();
		StringBuffer code = getImportDeclarations();
		code.append("__compare(Object this) : __left(this) == __right(this);__left(Object this) : " + left
				+ "; __right(Object this) :" + right + ";");
		f = f.cloneWithExtensions(code.toString());
		Boolean result = (Boolean) f.call("__compare", _this);
		if (!result) {
			Object leftResult = f.call("__left", _this);
			Object rightResult = f.call("__right", _this);
			fail(message + " was : " + leftResult + "("
					+ (leftResult != null ? leftResult.getClass().getSimpleName() : "") + ") != " + rightResult + "("
					+ (leftResult != null ? leftResult.getClass().getSimpleName() : "") + ")");
		}
	}

	private StringBuffer getImportDeclarations() {
		StringBuffer code = new StringBuffer();
		for (String _import : importedExtensions()) {
			code.append("extension ").append(_import).append(";");
		}
		return code;
	}
	
	private static JavaBeansMetaModel mm = new JavaBeansMetaModel();

	protected XtendFacade getXtendFacade() {
		ExecutionContextImpl ctx = new ExecutionContextImpl();
		ctx.registerMetaModel(mm);
		return XtendFacade.create(ctx);
	}

	protected String readFileIntoString(String filePath) throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		URL url = classLoader.getResource(filePath);
		if (url == null) {
			fail("Could not read resource: '" + filePath + "'. Is your file system case sensitive?");
		} else {
			if(!new File(url.getPath()).getCanonicalPath().endsWith(filePath))
				throw new RuntimeException(filePath + ":\n" +
						"The file does not exist exactly as it was named.\n" +
						"The test is likely to cause trouble on the build server.\n" +
						"Is your filesystem case insensitive? Please verify the spelling.");

			InputStream resourceAsStream = classLoader.getResourceAsStream(filePath);
			try {
				if (resourceAsStream == null) {
					fail("Could not read resource: '" + filePath + "'. Is your file system case sensitive?");
				} else {
						byte[] buffer = new byte[2048];
						int bytesRead = 0;
						StringBuffer b = new StringBuffer();
						do {
							bytesRead = resourceAsStream.read(buffer);
							if (bytesRead != -1)
								b.append(new String(buffer, 0, bytesRead));
						} while (bytesRead != -1);
						String model = b.toString();
						return model;
				}
			} finally {
				if (resourceAsStream != null)
					resourceAsStream.close();
			}
		}
		throw new IllegalStateException("May not happen, but helps to suppress false positives in eclipse' control flow analysis.");
	}

	protected List<String> getAllTestGrammarPaths(boolean firstOnly) {
		if (testGrammarPaths == null) {
			testGrammarPaths = Lists.newArrayList();
			try {
				String mweFile = readFileIntoString("org/eclipse/xtext/GenerateAllTestLanguages.mwe2");
				Pattern pattern = Pattern.compile("uri\\s*=\\s*\"([^\"]*)\"");
				Matcher matcher = pattern.matcher(mweFile);
				while (matcher.find()) {
					String grammarURI = matcher.group(1);
					String grammarPath = grammarURI.replaceFirst("classpath:/", "");
					testGrammarPaths.add(grammarPath);
					if(firstOnly) 
						break;
				}
			} catch (Exception exc) {
	
			}
		}
		return testGrammarPaths;
	}

	public static final class Keys {
		private static final TypeLiteral<Provider<XtextResourceSet>> resourceSetLiteral = new TypeLiteral<Provider<XtextResourceSet>>(){
		};

		public static final Key<Provider<XtextResourceSet>> RESOURCE_SET_KEY = Key.get(resourceSetLiteral);
	}
}
