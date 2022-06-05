package js.tools.apt;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import js.tools.script.gen.Builder;
import js.tools.script.gen.JsClass;
import js.tools.script.gen.JsMethod;

/**
 * Annotation processor for HTTP-RMI stub script generation. Process all remote methods from classes annotated with
 * "jakarta.ejb.Remote" - deprecated "javax.ejb.Remote" is also supported, and generate script classes for HTTP-RMI stub.
 * 
 * <pre>
 * In order to use this annotation processor from Eclipse IDE one may need to:
 * 1. on project properties / java compiler / annotation processing:
 *  - enable project specific settings
 *  - enable annotation processing
 *  - enable processing in editor
 *  - generated source directory: rmi
 *  - ensure rmi is declared as source in java build path / source tab
 * 2. on project properties / java compiler / annotation processing / factory path:
 *  - enable project specific settings
 *  - add js-apt-x.y.z.jar
 * </pre>
 * 
 * @author Iulian Rotaru
 * @since 1.0
 */
@SupportedAnnotationTypes({ "jakarta.ejb.Remote", "javax.ejb.Remote" })
public class GenerateRmiScript extends AbstractProcessor {
	/**
	 * Annotation processor files factory.
	 */
	private Filer filer;

	@Override
	public void init(ProcessingEnvironment env) {
		this.filer = env.getFiler();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
		for (Element element : env.getRootElements()) {
			if (element.getKind() != ElementKind.INTERFACE && element.getKind() != ElementKind.CLASS) {
				continue;
			}

			TypeElement typeElement = (TypeElement) element;
			boolean remoteType = isRemote(typeElement);

			JsClass jsClass = Builder.createJsClass();
			jsClass.setQualifiedClassName(typeElement.getQualifiedName().toString());

			for (Element innerElement : element.getEnclosedElements()) {
				if (innerElement.getKind() != ElementKind.METHOD) {
					continue;
				}
				ExecutableElement methodElement = (ExecutableElement) innerElement;
				if (isNotPublic(methodElement)) {
					continue;
				}
				if (isLocal(methodElement)) {
					continue;
				}
				if (!remoteType && !isRemote(methodElement)) {
					continue;
				}

				JsMethod jsMethod = jsClass.createMethod();
				jsMethod.setMethodName(methodElement.getSimpleName().toString());
				jsMethod.setReturnType(methodElement.getReturnType().toString());

				for (Element parameterElement : methodElement.getParameters()) {
					String type = parameterElement.asType().toString();
					String name = parameterElement.getSimpleName().toString();
					jsMethod.addParameter(type, name);
				}
				for (TypeMirror thrownElement : methodElement.getThrownTypes()) {
					jsMethod.addExceptionType(thrownElement.toString());
				}
				jsClass.addMethod(jsMethod);
			}
			if (!jsClass.hasMethods()) {
				continue;
			}

			String fileName = jsClass.getClassName() + ".js";
			Writer writer = null;
			try {
				FileObject file = this.filer.createResource(StandardLocation.SOURCE_OUTPUT, jsClass.getPackageName(), fileName, element);
				writer = file.openWriter();
				jsClass.serialize(writer);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				close(writer);
			}
		}
		return true;
	}

	/**
	 * Helper method to close a closeable instance, possible null. This method also takes care to print exception stack if close
	 * operation fails.
	 * 
	 * @param closeable closeable instance, possible null.
	 */
	private static void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Helper predicate to test if method modifier is private or protected.
	 * 
	 * @param methodElement method element.
	 * @return true if method is private or protected.
	 */
	private static boolean isNotPublic(ExecutableElement methodElement) {
		return methodElement.getModifiers().contains(Modifier.PRIVATE) || methodElement.getModifiers().contains(Modifier.PROTECTED);
	}

	/**
	 * Helper method to test if class or method is annotated as {@literal @}Remote.
	 * 
	 * @param element class or method to test.
	 * @return true if element is annotated with {@literal @}Remote.
	 */
	private static boolean isRemote(Element element) {
		for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
			String annotationType = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
			if (annotationType.toString().equals("Service")) {
				return true;
			}
			if (annotationType.toString().equals("Remote")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Helper method to test if class or method is annotated as {@literal @}Local.
	 * 
	 * @param element class or method to test.
	 * @return true if element is annotated with {@literal @}Local.
	 */
	private static boolean isLocal(Element element) {
		for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
			String annotationType = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
			if (annotationType.toString().equals("Local")) {
				return true;
			}
		}
		return false;
	}
}
