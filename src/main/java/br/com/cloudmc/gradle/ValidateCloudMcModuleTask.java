package br.com.cloudmc.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@DisableCachingByDefault(because = "Validation has no output artifact and reports compatibility failures directly.")
public abstract class ValidateCloudMcModuleTask extends DefaultTask {
    private static final String MC_PREFIX = "net/minecraft/";

    @Input
    public abstract Property<Integer> getApiVersion();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getModuleJar();

    @Classpath
    public abstract ConfigurableFileCollection getStubClasspath();

    @TaskAction
    public void run() throws IOException {
        Map<String, TargetClass> stubs = readTargetClasses();
        if (stubs.isEmpty()) {
            throw new IllegalStateException("CloudMC API " + getApiVersion().get() + " stubs resolved to an empty classpath");
        }

        Set<Issue> issues = new TreeSet<>(Comparator.comparing(Issue::toString));
        inspectJar(Files.readAllBytes(getModuleJar().get().getAsFile().toPath()), stubs, issues);

        if (!issues.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("CloudMC API ").append(getApiVersion().get()).append(" incompatible module references:");
            issues.stream().limit(80).forEach(issue -> message.append(System.lineSeparator()).append(" - ").append(issue));
            if (issues.size() > 80) {
                message.append(System.lineSeparator()).append(" - ... ").append(issues.size() - 80).append(" more");
            }
            throw new IllegalStateException(message.toString());
        }

        getLogger().lifecycle("[CloudScript] CloudMC API {} validation passed for {}", getApiVersion().get(), getModuleJar().get().getAsFile());
    }

    private Map<String, TargetClass> readTargetClasses() throws IOException {
        Map<String, TargetClass> classes = new HashMap<>();
        for (File file : getStubClasspath().getFiles()) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                readClassesFromJar(Files.readAllBytes(file.toPath()), classes);
            } else if (file.isDirectory()) {
                try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(file.toPath())) {
                    paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try {
                                readClass(Files.readAllBytes(path), classes);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                }
            }
        }
        return classes;
    }

    private void readClassesFromJar(byte[] jar, Map<String, TargetClass> classes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    readClass(zip.readAllBytes(), classes);
                }
            }
        }
    }

    private void readClass(byte[] bytes, Map<String, TargetClass> classes) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        TargetClass target = new TargetClass(node.superName, node.interfaces == null ? List.of() : node.interfaces);
        node.fields.forEach(field -> target.fields.add(new Member(field.name, field.desc)));
        node.methods.forEach(method -> target.methods.add(new Member(method.name, method.desc)));
        classes.put(node.name, target);
    }

    private void inspectJar(byte[] jar, Map<String, TargetClass> stubs, Set<Issue> issues) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    inspectClass(zip.readAllBytes(), stubs, issues);
                }
            }
        }
    }

    private void inspectClass(byte[] bytes, Map<String, TargetClass> stubs, Set<Issue> issues) {
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        checkClass(node.superName, stubs, issues);
        node.interfaces.forEach(name -> checkClass(name, stubs, issues));
        node.fields.forEach(field -> checkDescriptor(field.desc, stubs, issues));
        node.methods.forEach(method -> {
            checkMethodDescriptor(method.desc, stubs, issues);
            method.instructions.forEach(instruction -> {
                if (instruction instanceof TypeInsnNode nodeInsn) {
                    checkClass(nodeInsn.desc, stubs, issues);
                } else if (instruction instanceof FieldInsnNode fieldInsn) {
                    checkClass(fieldInsn.owner, stubs, issues);
                    checkDescriptor(fieldInsn.desc, stubs, issues);
                    if (fieldInsn.owner.startsWith(MC_PREFIX) &&
                        !hasMember(stubs, fieldInsn.owner, new Member(fieldInsn.name, fieldInsn.desc), false)) {
                        issues.add(new Issue("field", fieldInsn.owner + "." + fieldInsn.name + " " + fieldInsn.desc));
                    }
                } else if (instruction instanceof MethodInsnNode methodInsn) {
                    checkClass(methodInsn.owner, stubs, issues);
                    checkMethodDescriptor(methodInsn.desc, stubs, issues);
                    if (methodInsn.owner.startsWith(MC_PREFIX) &&
                        !hasMember(stubs, methodInsn.owner, new Member(methodInsn.name, methodInsn.desc), true)) {
                        issues.add(new Issue("method", methodInsn.owner + "." + methodInsn.name + methodInsn.desc));
                    }
                } else if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) {
                    checkType(type, stubs, issues);
                } else if (instruction instanceof MultiANewArrayInsnNode array) {
                    checkDescriptor(array.desc, stubs, issues);
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    checkMethodDescriptor(dynamic.desc, stubs, issues);
                    checkHandle(dynamic.bsm, stubs, issues);
                    for (Object arg : dynamic.bsmArgs) {
                        if (arg instanceof Type type) checkType(type, stubs, issues);
                        if (arg instanceof Handle handle) checkHandle(handle, stubs, issues);
                    }
                }
            });
        });
    }

    private void checkHandle(Handle handle, Map<String, TargetClass> stubs, Set<Issue> issues) {
        checkClass(handle.getOwner(), stubs, issues);
        if (handle.getTag() <= Opcodes.H_PUTSTATIC) {
            checkDescriptor(handle.getDesc(), stubs, issues);
            if (handle.getOwner().startsWith(MC_PREFIX) &&
                !hasMember(stubs, handle.getOwner(), new Member(handle.getName(), handle.getDesc()), false)) {
                issues.add(new Issue("field", handle.getOwner() + "." + handle.getName() + " " + handle.getDesc()));
            }
        } else {
            checkMethodDescriptor(handle.getDesc(), stubs, issues);
            if (handle.getOwner().startsWith(MC_PREFIX) &&
                !hasMember(stubs, handle.getOwner(), new Member(handle.getName(), handle.getDesc()), true)) {
                issues.add(new Issue("method", handle.getOwner() + "." + handle.getName() + handle.getDesc()));
            }
        }
    }

    private void checkClass(String name, Map<String, TargetClass> stubs, Set<Issue> issues) {
        if (name != null && name.startsWith(MC_PREFIX) && !stubs.containsKey(name)) {
            issues.add(new Issue("class", name));
        }
    }

    private void checkDescriptor(String descriptor, Map<String, TargetClass> stubs, Set<Issue> issues) {
        checkType(Type.getType(descriptor), stubs, issues);
    }

    private void checkMethodDescriptor(String descriptor, Map<String, TargetClass> stubs, Set<Issue> issues) {
        for (Type argument : Type.getArgumentTypes(descriptor)) checkType(argument, stubs, issues);
        checkType(Type.getReturnType(descriptor), stubs, issues);
    }

    private void checkType(Type type, Map<String, TargetClass> stubs, Set<Issue> issues) {
        Type value = type.getSort() == Type.ARRAY ? type.getElementType() : type;
        if (value.getSort() == Type.OBJECT) checkClass(value.getInternalName(), stubs, issues);
    }

    private boolean hasMember(Map<String, TargetClass> stubs, String owner, Member member, boolean method) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            TargetClass target = stubs.get(current);
            if (target == null) continue;
            if ((method ? target.methods : target.fields).contains(member)) return true;
            if (method && "<init>".equals(member.name)) continue;
            if (target.superName != null) pending.add(target.superName);
            pending.addAll(target.interfaces);
        }
        return false;
    }

    private record Issue(String kind, String reference) {
        @Override
        public String toString() {
            return kind + " " + reference;
        }
    }

    private record Member(String name, String descriptor) {
    }

    private static final class TargetClass {
        final String superName;
        final List<String> interfaces;
        final Set<Member> fields = new HashSet<>();
        final Set<Member> methods = new HashSet<>();

        private TargetClass(String superName, List<String> interfaces) {
            this.superName = superName;
            this.interfaces = new ArrayList<>(interfaces);
        }
    }
}
