/*
 *  Copyright 2012 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.dependency;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.teavm.common.*;
import org.teavm.common.ConcurrentCachedMapper.KeyListener;
import org.teavm.model.*;

/**
 *
 * @author Alexey Andreev
 */
public class DependencyChecker implements DependencyInformation {
    private static Object dummyValue = new Object();
    static final boolean shouldLog = System.getProperty("org.teavm.logDependencies", "false").equals("true");
    private ClassHolderSource classSource;
    private ClassLoader classLoader;
    private FiniteExecutor executor;
    private ConcurrentMap<MethodReference, Object> abstractMethods = new ConcurrentHashMap<>();
    private ConcurrentCachedMapper<MethodReference, MethodGraph> methodCache;
    private ConcurrentCachedMapper<FieldReference, DependencyNode> fieldCache;
    private ConcurrentMap<String, Object> achievableClasses = new ConcurrentHashMap<>();
    private ConcurrentMap<String, Object> initializedClasses = new ConcurrentHashMap<>();
    private List<DependencyListener> listeners = new ArrayList<>();
    Set<MethodReference> missingMethods = new HashSet<>();
    Set<String> missingClasses = new HashSet<>();
    Set<FieldReference> missingFields = new HashSet<>();

    public DependencyChecker(ClassHolderSource classSource, ClassLoader classLoader) {
        this(classSource, classLoader, new SimpleFiniteExecutor());
    }

    public DependencyChecker(ClassHolderSource classSource, ClassLoader classLoader, FiniteExecutor executor) {
        this.classSource = classSource;
        this.classLoader = classLoader;
        this.executor = executor;
        methodCache = new ConcurrentCachedMapper<>(new Mapper<MethodReference, MethodGraph>() {
            @Override public MethodGraph map(MethodReference preimage) {
                return createMethodGraph(preimage);
            }
        });
        fieldCache = new ConcurrentCachedMapper<>(new Mapper<FieldReference, DependencyNode>() {
            @Override public DependencyNode map(FieldReference preimage) {
                return createFieldNode(preimage);
            }
        });
        methodCache.addKeyListener(new KeyListener<MethodReference>() {
            @Override public void keyAdded(MethodReference key) {
                for (DependencyListener listener : listeners) {
                    listener.methodAchieved(DependencyChecker.this, key);
                }
                activateDependencyPlugin(key);
            }
        });
        fieldCache.addKeyListener(new KeyListener<FieldReference>() {
            @Override public void keyAdded(FieldReference key) {
                for (DependencyListener listener : listeners) {
                    listener.fieldAchieved(DependencyChecker.this, key);
                }
            }
        });
    }

    public DependencyNode createNode() {
        return new DependencyNode(this);
    }

    public ClassHolderSource getClassSource() {
        return classSource;
    }

    public void addDependencyListener(DependencyListener listener) {
        listeners.add(listener);
    }

    public void startListeners() {
        for (DependencyListener listener : listeners) {
            listener.started(this);
        }
    }

    public void addEntryPoint(MethodReference methodRef, String... argumentTypes) {
        ValueType[] parameters = methodRef.getDescriptor().getParameterTypes();
        if (parameters.length != argumentTypes.length) {
            throw new IllegalArgumentException("argumentTypes length does not match the number of method's arguments");
        }
        MethodGraph graph = attachMethodGraph(methodRef);
        DependencyNode[] varNodes = graph.getVariables();
        varNodes[0].propagate(methodRef.getClassName());
        for (int i = 0; i < argumentTypes.length; ++i) {
            varNodes[i + 1].propagate(argumentTypes[i]);
        }
    }

    public void schedulePropagation(final DependencyConsumer consumer, final String type) {
        executor.executeFast(new Runnable() {
            @Override public void run() {
                consumer.consume(type);
            }
        });
    }

    public FiniteExecutor getExecutor() {
        return executor;
    }

    boolean achieveClass(String className) {
        boolean result = achievableClasses.putIfAbsent(className, dummyValue) == null;
        if (result) {
            for (DependencyListener listener : listeners) {
                listener.classAchieved(this, className);
            }
        }
        return result;
    }

    public MethodGraph attachMethodGraph(MethodReference methodRef) {
        return methodCache.map(methodRef);
    }

    public void initClass(String className) {
        MethodDescriptor clinitDesc = new MethodDescriptor("<clinit>", ValueType.VOID);
        while (className != null) {
            if (initializedClasses.putIfAbsent(className, clinitDesc) != null) {
                break;
            }
            achieveClass(className);
            achieveInterfaces(className);
            ClassHolder cls = classSource.get(className);
            if (cls == null) {
                missingClasses.add(className);
                return;
            }
            if (cls.getMethod(clinitDesc) != null) {
                attachMethodGraph(new MethodReference(className, clinitDesc));
            }
            className = cls.getParent();
        }
    }

    private void achieveInterfaces(String className) {
        ClassHolder cls = classSource.get(className);
        if (cls == null) {
            missingClasses.add(className);
            return;
        }
        for (String iface : cls.getInterfaces()) {
            if (achieveClass(iface)) {
                achieveInterfaces(iface);
            }
        }
    }

    private MethodGraph createMethodGraph(final MethodReference methodRef) {
        initClass(methodRef.getClassName());
        ClassHolder cls = classSource.get(methodRef.getClassName());
        MethodHolder method;
        if (cls == null) {
            missingClasses.add(methodRef.getClassName());
            method = null;
        } else {
            method = cls.getMethod(methodRef.getDescriptor());
            if (method == null) {
                while (cls != null) {
                    method = cls.getMethod(methodRef.getDescriptor());
                    if (method != null) {
                        return methodCache.map(new MethodReference(cls.getName(), methodRef.getDescriptor()));
                    }
                    cls = cls.getParent() != null ? classSource.get(cls.getParent()) : null;
                }
                missingMethods.add(methodRef);
            }
        }
        ValueType[] arguments = methodRef.getParameterTypes();
        int paramCount = arguments.length + 1;
        int varCount = Math.max(paramCount, method != null ? method.getProgram().variableCount() : 0);
        DependencyNode[] parameterNodes = new DependencyNode[varCount];
        for (int i = 0; i < varCount; ++i) {
            parameterNodes[i] = new DependencyNode(this);
            if (shouldLog) {
                parameterNodes[i].setTag(methodRef + ":" + i);
            }
        }
        DependencyNode resultNode;
        if (methodRef.getDescriptor().getResultType() == ValueType.VOID) {
            resultNode = null;
        } else {
            resultNode = new DependencyNode(this);
            if (shouldLog) {
                resultNode.setTag(methodRef + ":RESULT");
            }
        }
        final MethodGraph graph = new MethodGraph(parameterNodes, paramCount, resultNode);
        if (method != null) {
            final MethodHolder currentMethod = method;
            executor.execute(new Runnable() {
                @Override public void run() {
                    DependencyGraphBuilder graphBuilder = new DependencyGraphBuilder(DependencyChecker.this);
                    graphBuilder.buildGraph(currentMethod, graph);
                }
            });
        }
        return graph;
    }

    public boolean isMethodAchievable(MethodReference methodRef) {
        return methodCache.caches(methodRef);
    }

    public boolean isAbstractMethodAchievable(MethodReference methodRef) {
        return abstractMethods.containsKey(methodRef);
    }

    @Override
    public Collection<MethodReference> getAchievableMethods() {
        return methodCache.getCachedPreimages();
    }

    @Override
    public Collection<FieldReference> getAchievableFields() {
        return fieldCache.getCachedPreimages();
    }

    @Override
    public Collection<String> getAchievableClasses() {
        return new HashSet<>(achievableClasses.keySet());
    }

    @Override
    public DependencyNode getField(FieldReference fieldRef) {
        return fieldCache.map(fieldRef);
    }

    private DependencyNode createFieldNode(FieldReference fieldRef) {
        initClass(fieldRef.getClassName());
        ClassHolder cls = classSource.get(fieldRef.getClassName());
        if (cls == null) {
            missingClasses.add(fieldRef.getClassName());
        } else {
            FieldHolder field = cls.getField(fieldRef.getFieldName());
            if (field == null) {
                while (cls != null) {
                    field = cls.getField(fieldRef.getFieldName());
                    if (field != null) {
                        return fieldCache.map(new FieldReference(cls.getName(), fieldRef.getFieldName()));
                    }
                    cls = cls.getParent() != null ? classSource.get(cls.getParent()) : null;
                }
                missingFields.add(fieldRef);
            }
        }
        DependencyNode node = new DependencyNode(this);
        if (shouldLog) {
            node.setTag(fieldRef.getClassName() + "#" + fieldRef.getFieldName());
        }
        return node;
    }

    private void activateDependencyPlugin(MethodReference methodRef) {
        ClassHolder cls = classSource.get(methodRef.getClassName());
        if (cls == null) {
            return;
        }
        MethodHolder method = cls.getMethod(methodRef.getDescriptor());
        if (method == null) {
            return;
        }
        AnnotationHolder depAnnot = method.getAnnotations().get(PluggableDependency.class.getName());
        if (depAnnot == null) {
            return;
        }
        ValueType depType = depAnnot.getValues().get("value").getJavaClass();
        String depClassName = ((ValueType.Object)depType).getClassName();
        Class<?> depClass;
        try {
            depClass = Class.forName(depClassName, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Dependency plugin not found: " + depClassName, e);
        }
        DependencyPlugin plugin;
        try {
            plugin = (DependencyPlugin)depClass.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException("Can't instantiate dependency plugin " + depClassName, e);
        }
        plugin.methodAchieved(this, methodRef);
    }

    public void addAbstractMethod(MethodReference methodRef) {
        if (abstractMethods.putIfAbsent(methodRef, methodRef) == null) {
            String className = methodRef.getClassName();
            while (className != null) {
                ClassHolder cls = classSource.get(className);
                if (cls == null) {
                    return;
                }
                MethodHolder method = cls.getMethod(methodRef.getDescriptor());
                if (method != null) {
                    abstractMethods.put(methodRef, methodRef);
                    return;
                }
                className = cls.getParent();
            }
        }
    }

    public ListableClassHolderSource cutUnachievableClasses() {
        MutableClassHolderSource cutClasses = new MutableClassHolderSource();
        for (String className : achievableClasses.keySet()) {
            ClassHolder classHolder = classSource.get(className);
            cutClasses.putClassHolder(classHolder);
            for (MethodHolder method : classHolder.getMethods().toArray(new MethodHolder[0])) {
                MethodReference methodRef = new MethodReference(className, method.getDescriptor());
                if (!methodCache.getCachedPreimages().contains(methodRef)) {
                    if (abstractMethods.containsKey(methodRef)) {
                        method.getModifiers().add(ElementModifier.ABSTRACT);
                        method.setProgram(null);
                    } else {
                        classHolder.removeMethod(method);
                    }
                }
            }
            for (FieldHolder field : classHolder.getFields().toArray(new FieldHolder[0])) {
                FieldReference fieldRef = new FieldReference(className, field.getName());
                if (!fieldCache.getCachedPreimages().contains(fieldRef)) {
                    classHolder.removeField(field);
                }
            }
        }
        return cutClasses;
    }

    @Override
    public DependencyMethodInformation getMethod(MethodReference methodRef) {
        return methodCache.getKnown(methodRef);
    }

    public void checkForMissingItems() {
        if (missingClasses.isEmpty() && missingMethods.isEmpty() && missingFields.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (!missingClasses.isEmpty()) {
            sb.append("Missing classes:\n");
            for (String cls : missingClasses) {
                sb.append("  ").append(cls).append("\n");
            }
        }
        if (!missingMethods.isEmpty()) {
            sb.append("Missing methods:\n");
            for (MethodReference method : missingMethods) {
                sb.append("  ").append(method).append("\n");
            }
        }
        if (!missingMethods.isEmpty()) {
            sb.append("Missing fields:\n");
            for (FieldReference field : missingFields) {
                sb.append("  ").append(field).append("\n");
            }
        }
        throw new IllegalStateException(sb.toString());
    }
}
