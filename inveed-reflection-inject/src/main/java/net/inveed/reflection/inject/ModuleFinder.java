package net.inveed.reflection.inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javassist.NotFoundException;

public class ModuleFinder {
	private static final Logger LOG = LoggerFactory.getLogger(ModuleFinder.class);
	private static final String PLUGIN_PKG_ATTR = "Plugin-Package";
	private final ArrayList<File> pluginJARs = new ArrayList<>();
	private ClassPreProcessor registry;
	

	private List<URL> parseSystemClasspath() {
		ArrayList<URL> ret = new ArrayList<>();
	    ArrayList<ClassLoader> classLoaders = new ArrayList<>();
	    HashSet<ClassLoader> classLoadersSet = new HashSet<>();
	    classLoadersSet.add(ClassLoader.getSystemClassLoader());
	    classLoaders.add(ClassLoader.getSystemClassLoader());
	    if (classLoadersSet.add(Thread.currentThread().getContextClassLoader())) {
	        classLoaders.add(Thread.currentThread().getContextClassLoader());
	    }

	    for (ClassLoader cl : classLoaders) {
	        if (cl != null && (cl instanceof URLClassLoader)) {
	            for (URL url : ((URLClassLoader) cl).getURLs()) {
	            	ret.add(url);
	            }
	        }
	    }
	    return ret;
	}
	
	public void loadClasses(ClassPreProcessor registry) throws IOException, NotFoundException, ClassNotFoundException {
		this.registry = registry;
		
		List<URL> classpathElements = this.parseSystemClasspath();
		
		for (URL url : classpathElements) {
			String path = URLDecoder.decode(url.getPath(), "UTF-8");
			File root = new File(path).getCanonicalFile();
			if (root.isDirectory()) {
				readDirectory(root);
			} else if (root.isFile()) {
				this.loadJARFile(url);
			}
		}
	}
	
	public List<File> getPluginJarFiles() {
		return Collections.unmodifiableList(this.pluginJARs);
	}
	
	private void readDirectory(File root) throws IOException {
		if (root.isFile())
			return;
		File manifest = new File(root, "META-INF/MANIFEST.MF");
		if (manifest.exists() && manifest.isFile()) {
			this.loadManifestDir(root);
		} else {
			for (File f : root.listFiles()) {
				if (!f.isFile()) {
					readDirectory(f);
				} else if (f.getName().endsWith(".jar")) {
					this.loadJARClasses(f);
				}
			}
		}
	}
	private void loadJARFile(URL url) throws IOException {
		if (url.getProtocol().equals("jar")) {
			String nu = url.getPath();
			int nuidx = nu.indexOf(".jar!/");
			if (nuidx > 0 ) {
				nu = nu.substring(0, nuidx) + ".jar";
			} else if (!nu.endsWith(".jar"))
				return;
			
			url = new URL(nu);
			loadJARFile(url);
			return;
		} else if (url.getProtocol().equals("file")) {
			String path = URLDecoder.decode(url.getPath(), "UTF-8");
			if (!path.endsWith(".jar"))
				return;
			File f = new File(path);
			if (!f.exists())
				return;
			try {
				this.loadJARClasses(f);
			} catch (FileNotFoundException e) {
				return;
			}
		}
	}
	
	private void loadManifestDir(File dir) throws IOException {
		File manifest = new File(dir, "META-INF/MANIFEST.MF");
		if (!manifest.exists() || !manifest.isFile()) {
			return;
		}
		FileInputStream fis = null;
		Manifest mf = null;
		try{
			fis = new FileInputStream(manifest);
			mf = new Manifest(fis);
		} finally {
			if (fis != null)
				fis.close();
		}
		
		Attributes attrs = mf.getMainAttributes();
		if (attrs == null)
			return;
		
		String v = attrs.getValue(PLUGIN_PKG_ATTR);
		if (v == null)
			return;
		
		String[] packages = v.split(",");
		
		boolean isPluginJar = false;
		for (String p : packages) {
			p = p.trim();
			
			if (p.length() < 1)
				continue;
			p = p.replace('.', '/');
			
			boolean recursive = false;
			if (p.endsWith("/*")) {
				p = p.substring(0, p.length() - 1);
				recursive = true;
			}
			
			if (!p.endsWith("/")) {
				p = p + "/";
			}
			File pkgDir = new File(dir, p);
			if (!pkgDir.exists()) {
				//TODO: LOG
				continue;
			} else if (pkgDir.isFile()) {
				//TODO: LOG
				continue;
			}
			
			File[] filesInPkg;
			if (!recursive) {
				filesInPkg = pkgDir.listFiles();
			} else {
				ArrayList<File> l = new ArrayList<>();
				findFilesRecursively(pkgDir, l);
				filesInPkg = l.toArray(new File[l.size()]);
			}
			for (File fip : filesInPkg) {
				if (!fip.isFile())
					continue;
				
				if (!fip.getName().endsWith(".class")) 
					continue;
				InputStream ris = null;
				
				try {
					ris = new FileInputStream(fip);
					this.registry.registerClass(ris);
					isPluginJar = true;
				} catch (ClassNotFoundException | NotFoundException | RuntimeException e) {
					LOG.warn("Cannot load class " + fip.getAbsolutePath(), e);
				} finally {
					ris.close();
				}
			}
		}

		if (isPluginJar)
			this.pluginJARs.add(dir);
	}
	
	private static final void findFilesRecursively(File dir, List<File> list) {
		if (!dir.isDirectory()) {
			return;
		}
		for (File f : dir.listFiles()) {
			if (!f.isFile()) {
				findFilesRecursively(f, list);
			} else {
				list.add(f);
			}
		}
	}
	private void loadJARClasses(File f) throws IOException {
		JarFile jar = new JarFile(f);
		try {
			Manifest mf = jar.getManifest();
			if (mf == null)
				return;
			//boolean found = false;
			Attributes attrs = mf.getMainAttributes();
			if (attrs == null)
				return;
			
			String v = attrs.getValue(PLUGIN_PKG_ATTR);
			if (v == null)
				return;
			
			String[] packages = v.split(",");
			ArrayList<String> packagesList = new ArrayList<>(packages.length);
			for (String p : packages) {
				p = p.trim();
				
				if (p.length() < 1)
					continue;
				p = p.replace('.', '/');
				if (!p.endsWith("/")) {
					p = p + "/";
				}
				packagesList.add(p);
			}
			boolean isPluginJar = false;
			LOG.info("Loading JAR file: " + f.getAbsolutePath());
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry je = entries.nextElement();
				
				if (je.isDirectory())
					continue;
				
				if (!je.getName().endsWith(".class")) 
					continue;
				
				boolean validPackage = false;
				for (String pname : packagesList) {
					if (je.getName().startsWith(pname)) {
						validPackage = true;
						break;
					}
				}
				if (!validPackage)
					continue;
				
				InputStream ris = jar.getInputStream(je);
				
				try {
					this.registry.registerClass(ris);
					isPluginJar = true;
				} catch (ClassNotFoundException | NotFoundException | RuntimeException e) {
					LOG.warn("Cannot load class " + je.getName() + " from JAR " + f.getAbsolutePath(), e);
				} finally {
					ris.close();
				}
			}
			if (isPluginJar)
				this.pluginJARs.add(f);
		} finally {
			jar.close();
		}
	}
}
