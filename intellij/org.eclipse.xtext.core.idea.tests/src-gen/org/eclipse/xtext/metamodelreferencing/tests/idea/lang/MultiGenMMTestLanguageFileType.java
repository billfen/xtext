package org.eclipse.xtext.metamodelreferencing.tests.idea.lang;

import javax.swing.Icon;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;

public final class MultiGenMMTestLanguageFileType extends LanguageFileType {

	public static final MultiGenMMTestLanguageFileType INSTANCE = new MultiGenMMTestLanguageFileType();
	
	@NonNls 
	public static final String DEFAULT_EXTENSION = "multigenmmtestlanguage";

	private MultiGenMMTestLanguageFileType() {
		super(MultiGenMMTestLanguageLanguage.INSTANCE);
	}

	public String getDefaultExtension() {
		return DEFAULT_EXTENSION;
	}

	public String getDescription() {
		return "MultiGenMMTestLanguage files";
	}

	public Icon getIcon() {
		return null;
	}

	public String getName() {
		return "MultiGenMMTestLanguage";
	}

}
