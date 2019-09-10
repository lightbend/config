/**
 * 
 */
package com.typesafe.config;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Set;

/**
 * @author jamesratzlaff
 *
 */
public interface ConfigFormat {

	/**
	 * 
	 * @return a set of lowercase extensions acceptable by this format
	 */
	Set<String> getExtensions();
	/**
	 * 
	 * @return a set of lowercase mime-types acceptable by this format
	 */
	Set<String> getMimeTypes();
	
	/**
	 * 
	 * @return a string that can be used for the "Accept header" when making an HTTP request
	 */
	default String acceptsContent() {
		return String.join("; ", getMimeTypes());
	}
	
	/**
	 * 
	 * @param acceptsContentString "Content-Type" header returned from an http response 
	 * @return
	 */
	default boolean acceptsContent(String contentType) {
		if(contentType==null) {
			return acceptsMimeType(contentType);
		}
		String[] contentTypes = contentType.split("[;]");
		boolean accepted = false;
		for(int i=0;!accepted&&i<contentTypes.length;i++) {
			String mimeType = contentTypes[i];
			accepted = acceptsMimeType(mimeType);
		}
		return accepted;
	}
	
	/**
	 * 
	 * @param extension
	 * @return {@code true} if the given {@code extension} is acceptable by this format
	 */
	default boolean acceptsExtension(String extension) {
		return getExtensions().contains(extension!=null?extension.toLowerCase():null);
	}
	
	default boolean acceptsMimeType(String mimeType) {
		return getMimeTypes().contains(mimeType!=null?mimeType.toLowerCase():null);
	}
	
	default boolean test(URL url) {
		String extension = getExtension(url);
		return acceptsExtension(extension);
	}
	
	default boolean test(File file) {
		String extension = getExtension(file);
		return acceptsExtension(extension);
	}
	
	default boolean test(Path path) {
		String extension =getExtension(path);
		return acceptsExtension(extension);
	}
	
	default boolean test(String resource) {
		String extension = getExtension(resource);
		return acceptsExtension(extension);
	}
	
	static String getExtension(File file) {
		String extension = null;
		if(file!=null) {
			extension=getExtension(file.toPath());
		}
		return extension;
	}
	
	static String getExtension(Path path) {
		String extension = null;
		if(path!=null) {
			extension=path.getFileName().toString();
		}
		return extension;
	}
	
	static String getExtension(String str) {
		String extension = null;
		if(str!=null&&!str.isEmpty()) {
			int lastSlash = str.lastIndexOf('/');
			lastSlash+=1;
			int lastDot = str.lastIndexOf('.', lastSlash);
			if(lastDot!=-1) {
				extension=str.substring((lastDot+1));
			}
		}
		return extension;
	}
	
	static String getExtension(URL url) {
		String extension = null;
		if(url!=null) {
			String path = url.getPath();
			extension=getExtension(path);
		}
		return extension;
	}
	
	
}
