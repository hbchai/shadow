package com.github.jengelman.gradle.plugins.shadow.tasks;

import com.github.jengelman.gradle.plugins.shadow.ShadowStats;
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.internal.DependencyFilter;
import com.github.jengelman.gradle.plugins.shadow.internal.GradleVersionUtil;
import com.github.jengelman.gradle.plugins.shadow.internal.ZipCompressor;
import com.github.jengelman.gradle.plugins.shadow.relocation.Relocator;
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator;
import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.GroovyExtensionModuleTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.util.PatternSet;

import java.util.ArrayList;
import java.util.List;

public class ShadowJar extends Jar implements ShadowSpec {

    private List<Transformer> transformers;
    private List<Relocator> relocators;
    private List<Configuration> configurations;
    private DependencyFilter dependencyFilter;

    private final ShadowStats shadowStats = new ShadowStats();
    private final GradleVersionUtil versionUtil;

    public ShadowJar() {
        versionUtil = new GradleVersionUtil(getProject().getGradle().getGradleVersion());
        dependencyFilter = new DefaultDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        transformers = new ArrayList<Transformer>();
        relocators = new ArrayList<Relocator>();
        configurations = new ArrayList<Configuration>();
    }

    @Override
    protected CopyAction createCopyAction() {
        DocumentationRegistry documentationRegistry = getServices().get(DocumentationRegistry.class);
        return new ShadowCopyAction(getArchivePath(), getInternalCompressor(), documentationRegistry,
                transformers, relocators, getRootPatternSet(), shadowStats);
    }

    protected ZipCompressor getInternalCompressor() {
        return versionUtil.getInternalCompressor(getEntryCompression(), this);
    }

    @TaskAction
    protected void copy() {
        from(getIncludedDependencies());
        super.copy();
        getLogger().info(shadowStats.toString());
    }

    @InputFiles @Optional
    public FileCollection getIncludedDependencies() {
        return dependencyFilter.resolve(configurations);
    }

    /**
     * Utility method for assisting between changes in Gradle 1.12 and 2.x
     * @return
     */
    protected PatternSet getRootPatternSet() {
        return versionUtil.getRootPatternSet(getMainSpec());
    }

    /**
     * Configure inclusion/exclusion of module &amp; project dependencies into uber jar
     * @param c
     * @return
     */
    public ShadowJar dependencies(Action<DependencyFilter> c) {
        if (c != null) {
            c.execute(dependencyFilter);
        }
        return this;
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     * @param clazz
     * @return
     */
    public ShadowJar transform(Class<? extends Transformer> clazz) throws InstantiationException, IllegalAccessException {
        return transform(clazz, null);
    }

    /**
     * Add a Transformer instance for modifying JAR resources and configure.
     * @param clazz
     * @param c
     * @return
     */
    public <T extends Transformer> ShadowJar transform(Class<T> clazz, Action<T> c) throws InstantiationException, IllegalAccessException {
        T transformer = clazz.newInstance();
        if (c != null) {
            c.execute(transformer);
        }
        transformers.add(transformer);
        return this;
    }

    /**
     * Add a preconfigured transformer instance
     * @param transformer
     * @return
     */
    public ShadowJar transform(Transformer transformer) {
        transformers.add(transformer);
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    public ShadowJar mergeServiceFiles() {
        try {
            transform(ServiceFileTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    public ShadowJar mergeServiceFiles(final String rootPath) {
        try {
            transform(ServiceFileTransformer.class, new Action<ServiceFileTransformer>() {

                @Override
                public void execute(ServiceFileTransformer serviceFileTransformer) {
                    serviceFileTransformer.setPath(rootPath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging service files in JARs
     * @return
     */
    public ShadowJar mergeServiceFiles(Action<ServiceFileTransformer> configureClosure) {
        try {
            transform(ServiceFileTransformer.class, configureClosure);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntactic sugar for merging Groovy extension module descriptor files in JARs
     * @return
     */
    public ShadowJar mergeGroovyExtensionModules() {
        try {
            transform(GroovyExtensionModuleTransformer.class);
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Syntax sugar for merging service files in JARs
     * @return
     */
    public ShadowJar append(final String resourcePath) {
        try {
            transform(AppendingTransformer.class, new Action<AppendingTransformer>() {
                @Override
                public void execute(AppendingTransformer transformer) {
                    transformer.setResource(resourcePath);
                }
            });
        } catch (IllegalAccessException e) {
        } catch (InstantiationException e) {
        }
        return this;
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination
     * @param pattern
     * @param destination
     * @return
     */
    public ShadowJar relocate(String pattern, String destination) {
        return relocate(pattern, destination, null);
    }

    /**
     * Add a class relocator that maps each class in the pattern to the provided destination
     * @param pattern
     * @param destination
     * @param configure
     * @return
     */
    public ShadowJar relocate(String pattern, String destination, Action<SimpleRelocator> configure) {
        SimpleRelocator relocator = new SimpleRelocator(pattern, destination, new ArrayList<String>(), new ArrayList<String>());
        if (configure != null) {
            configure.execute(relocator);
        }
        relocators.add(relocator);
        return this;
    }

    /**
     * Add a relocator instance
     * @param relocator
     * @return
     */
    public ShadowJar relocate(Relocator relocator) {
        relocators.add(relocator);
        return this;
    }

    /**
     * Add a relocator of the provided class and configure
     * @param relocatorClass
     * @return
     */
    public ShadowJar relocate(Class<? extends Relocator> relocatorClass) throws InstantiationException, IllegalAccessException {
        return relocate(relocatorClass, null);
    }

    /**
     * Add a relocator of the provided class and configure
     * @param relocatorClass
     * @param configure
     * @return
     */
    public <R extends Relocator> ShadowJar relocate(Class<R> relocatorClass, Action<R> configure) throws InstantiationException, IllegalAccessException {
        R relocator = relocatorClass.newInstance();
        if (configure != null) {
            configure.execute(relocator);
        }
        relocators.add(relocator);
        return this;
    }

    public List<Transformer> getTransformers() {
        return this.transformers;
    }

    public void setTransformers(List<Transformer> transformers) {
        this.transformers = transformers;
    }

    public List<Relocator> getRelocators() {
        return this.relocators;
    }

    public void setRelocators(List<Relocator> relocators) {
        this.relocators = relocators;
    }

    public List<Configuration> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<Configuration> configurations) {
        this.configurations = configurations;
    }

    public DependencyFilter getDependencyFilter() {
        return this.dependencyFilter;
    }

    public void setDependencyFilter(DependencyFilter filter) {
        this.dependencyFilter = filter;
    }

    // This code is only to make IntelliJ happy.
    private transient MetaClass metaClass = InvokerHelper.getMetaClass(this.getClass());

    public Object getProperty(String property) {
        return this.getMetaClass().getProperty(this, property);
    }

    public void setProperty(String property, Object newValue) {
        this.getMetaClass().setProperty(this, property, newValue);
    }

    public Object invokeMethod(String name, Object args) {
        return this.getMetaClass().invokeMethod(this, name, args);
    }

    public MetaClass getMetaClass() {
        if(this.metaClass == null) {
            this.metaClass = InvokerHelper.getMetaClass(this.getClass());
        }

        return this.metaClass;
    }

    public void setMetaClass(MetaClass metaClass) {
        this.metaClass = metaClass;
    }
}
