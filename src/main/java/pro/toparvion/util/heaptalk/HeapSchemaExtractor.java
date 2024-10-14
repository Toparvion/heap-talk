package pro.toparvion.util.heaptalk;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;

import org.netbeans.lib.profiler.heap.Field;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Vladimir Plizga
 */
public class HeapSchemaExtractor {
    private static final Logger log = LoggerFactory.getLogger(HeapSchemaExtractor.class);

    private static final Map<String, String> HPROF_2_SQL_TYPE_MAPPING = Map.of(
            "boolean", "BOOLEAN",
            "char", "CHARACTER(1)",
            "float", "FLOAT",
            "double", "DOUBLE",
            "byte", "TINYINT",
            "short", "SMALLINT",
            "int", "INTEGER",
            "long", "BIGINT",
            
            "integer", "INTEGER"           // for boxed type which is named differently from its primitive fellow 
    );
    
    private final Heap heap;

    public HeapSchemaExtractor(String dumpPath) throws IOException {
        heap = HeapFactory.createFastHeap(new File(dumpPath));
    }

    public Set<JavaClass> findAppClasses(List<String> basePackages) {
        Set<JavaClass> appClasses = new HashSet<>();

        for (String basePackage : basePackages) {
            String appClassesRegexp = basePackage.replace(".", "\\.") + "\\.[\\w\\.]+";
            Collection<JavaClass> packageClasses = heap.getJavaClassesByRegExp(appClassesRegexp);
            appClasses.addAll(packageClasses);
        }

        return appClasses;
    }

    public String composeClassDdl(JavaClass javaClass) {
        String fqName = javaClass.getName();
        String entityName = fqName.substring(fqName.lastIndexOf('.') + 1);
        
        var ddl = new StringBuilder("\n-- table with entities of type '")
                .append(entityName)
                .append("'\n")
                .append("CREATE TABLE \"")
                .append(fqName)
                .append("\" (\n");

        // 1. Process STATIC fields
        processStaticFields(javaClass, ddl);

        // 2. Process INSTANCE fields
        processInstanceFields(javaClass, ddl);

        ddl.append(");");

        return ddl.toString();
    }

    private void processStaticFields(JavaClass javaClass, StringBuilder ddl) {
        List<FieldValue> staticFieldValues = javaClass.getStaticFieldValues()
                .stream()
                // skip synthetic fields like `<class>`
                .filter(not(entry -> entry.getField().getName().startsWith("<")))
                .toList();
        
        for (int i = 0; i < staticFieldValues.size(); i++) {
            FieldValue staticFieldEntry = staticFieldValues.get(i);
            Field field = staticFieldEntry.getField();
            String fieldName = field.getName();
            String fieldTypeName = field.getType().getName();
            String fieldValue = staticFieldEntry.getValue();
            String optionalComma = (i < staticFieldValues.size() - 1) ? "," : "";

            composeFieldLine(ddl, fieldName, fieldTypeName, fieldValue, optionalComma);
        }
        
        if (!staticFieldValues.isEmpty()) {
            ddl.append('\n');
        }
    }

    private void processInstanceFields(JavaClass javaClass, StringBuilder ddl) {
        List<Instance> instances = javaClass.getInstances();

        // explicitly add Calcite-introduced reference to the current object 
        ddl.append("\tthis INTEGER PRIMARY_KEY");

        if (instances.isEmpty()) {
            log.debug("No instances found for class '{}'. Skipping instance field processing.", javaClass.getName());
            ddl.append("\t\t-- unique ID for each entity")
                    .append('\n');
            return;
        } else {
            ddl.append(',')
                    .append("\t\t-- unique ID for each entity")
                    .append('\n');
        }

        Instance someInstance = instances.getFirst();
        // TODO learn to process more instances as this one may have null at some of the ref fields and thus won't 
        //  let to determine the type of the addressed object

        List<FieldValue> fieldValues = someInstance.getFieldValues();
        for (int i = 0; i < fieldValues.size(); i++) {
            FieldValue instanceFieldEntry = fieldValues.get(i);
            Field field = instanceFieldEntry.getField();
            String fieldValue = instanceFieldEntry.getValue();

            String optionalComma = (i < fieldValues.size() - 1) ? "," : "";
            String fieldName = field.getName();
            String fieldTypeName = field.getType().getName();

            composeFieldLine(ddl, fieldName, fieldTypeName, fieldValue, optionalComma);
        }
    }

    private void composeFieldLine(StringBuilder ddl, String fieldName, String fieldTypeName, String fieldValue, String optionalComma) {
        ddl.append('\t')
                .append(fieldName)
                .append(' ');

        if (fieldTypeName.equals("object")) {       // 1. Process object references
            if (fieldValue.equals("0")) {               // 1.1 Process NULL reference
                ddl.append("INTEGER")
                        .append(optionalComma)
                        .append("\t\t-- reference on '")
                        .append(fieldName)
                        .append("' object")
                        .append('\n');

            } else {                                    // 1.2 Process non-NULL reference
                long instanceId = Long.parseLong(fieldValue);
                Instance referencedInstance = heap.getInstanceByID(instanceId);
                JavaClass fieldClass = referencedInstance.getJavaClass();
                String fqnType = fieldClass.getName();
                
                if (fqnType.equals("java.lang.String")) {           // 1.2.1 Process String references
                    // separately handle Strings as there is dedicated `toString()` function in MAT Calcite SQL dialect
                    ddl.append("VARCHAR(1024)")          // TODO consider computing the length consciously
                            .append(optionalComma)
                            .append('\n');
                }
                else if (fieldClass.getSuperClass().getName().equals("java.lang.Number") 
                        && fqnType.startsWith("java.lang.")) {      // 1.2.2 Process boxed numbers
                    String unboxedName = fqnType.substring("java.lang.".length()).toLowerCase();
                    String sqlType = requireNonNull(HPROF_2_SQL_TYPE_MAPPING.get(unboxedName),
                            "No mapping SQL mapping found for unboxed type '%s'".formatted(unboxedName));
                    ddl.append(sqlType)
                            .append(optionalComma)
                            .append('\n');
                }
                else {                                              // 1.2.3 Process other references
                    int dollarIndex = fqnType.indexOf('$');  // to omit proxy names like JpaUser$$SpringCGLIB$$0
                    int rightBorder = (dollarIndex > 0) ? dollarIndex : fqnType.length();
                    String basicName = fqnType.substring(0, rightBorder);
                    ddl.append("INTEGER")
                            .append(optionalComma)
                            .append("\t\t-- can be joined with \"")
                            .append(basicName)
                            .append("\" on 'this'")
                            .append('\n');
                }
            }
        } else {                                    // 2. Process primitive value
            String sqlType = requireNonNull(HPROF_2_SQL_TYPE_MAPPING.get(fieldTypeName),
                    "No mapping SQL mapping found for unboxed type '%s'".formatted(fieldTypeName));
            ddl.append(sqlType)
                    .append(optionalComma)
                    .append('\n');
        }
    }

}
