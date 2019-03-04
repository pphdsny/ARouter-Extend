package com.pphdsny.lib.route.compiler;

import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.google.auto.service.AutoService;
import com.pphdsny.lib.route.compiler.model.FieldModel;
import com.pphdsny.lib.route.compiler.util.Consts;
import com.pphdsny.lib.route.compiler.util.Logger;
import com.pphdsny.lib.route.compiler.util.TextUtils;
import com.pphdsny.lib.route.compiler.util.TypeUtils;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.pphdsny.lib.route.compiler.util.Consts.ANNOTATION_TYPE_ROUTE;
import static com.pphdsny.lib.route.compiler.util.Consts.CONTEXT;
import static com.pphdsny.lib.route.compiler.util.Consts.KEY_GENERATE_DOC_NAME;
import static com.pphdsny.lib.route.compiler.util.Consts.KEY_MODULE_NAME;
import static com.pphdsny.lib.route.compiler.util.Consts.NO_MODULE_NAME_TIPS;
import static com.pphdsny.lib.route.compiler.util.Consts.PACKAGE_OF_GENERATE_FILE;
import static com.pphdsny.lib.route.compiler.util.Consts.ROUTER_MANAGER;
import static com.pphdsny.lib.route.compiler.util.Consts.WARNING_TIPS;

/**
 * Created by wangpeng on 2019/2/26.
 */
@AutoService(Processor.class)
@SupportedOptions({KEY_MODULE_NAME, KEY_GENERATE_DOC_NAME})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE})
public class RouterProcessor extends AbstractProcessor {
    private Map<String, Set<RouteMeta>> groupMap = new HashMap<>(); // ModuleName and routeMeta.
    private Map<String, String> rootMap = new TreeMap<>();  // Map of root metas, used for generate class file in order.
    private Filer mFiler;       // File util, write class file into disk.
    private Logger logger;
    private Types types;
    private Elements elements;
    private TypeUtils typeUtils;
    private String moduleName = null;   // Module name, maybe its 'app' or others
    private TypeMirror iProvider = null;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        mFiler = processingEnv.getFiler();                  // Generate class.
        types = processingEnv.getTypeUtils();            // Get type utils.
        elements = processingEnv.getElementUtils();      // Get class meta.

        typeUtils = new TypeUtils(types, elements);
        logger = new Logger(processingEnv.getMessager());   // Package the log utils.

        // Attempt to get user configuration [moduleName]
        Map<String, String> options = processingEnv.getOptions();
        if (options != null && !options.isEmpty()) {
            moduleName = options.get(KEY_MODULE_NAME);
        }

        if (moduleName != null && !moduleName.isEmpty()) {
            moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");

            logger.info("The user has configuration the module name, it was [" + moduleName + "]");
        } else {
            logger.error(NO_MODULE_NAME_TIPS);
            throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
        }

        iProvider = elements.getTypeElement(Consts.IPROVIDER).asType();

        logInfo("RouterProcessor init.");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (set != null && !set.isEmpty()) {
            Set<? extends Element> routeElements = roundEnvironment.getElementsAnnotatedWith(Route.class);
            try {
                logInfo("Found routes, start...");
                this.parseRoutes(routeElements);

            } catch (Exception e) {
                logger.error(e);
            }
        }
        return false;
    }

    private void parseRoutes(Set<? extends Element> routeElements) {
        if (routeElements != null && !routeElements.isEmpty()) {
            logInfo("Found routes, size is " + routeElements.size());
            //新建一个统一的跳转工具类
            TypeSpec.Builder activityLaunchBuilder = TypeSpec.classBuilder(generateClassName())
                    .addJavadoc(WARNING_TIPS)
                    .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

            //添加方法
            List<MethodSpec> methodList = new ArrayList<>();
            for (Element element : routeElements) {
                //暂时只处理Activity
                TypeMirror activityTm = elements.getTypeElement(Consts.ACTIVITY).asType();
                boolean isActivity = false;
                if (types.isSubtype(element.asType(), activityTm)) {
                    isActivity = true;
                }
                if (!isActivity) {
                    continue;
                }

                Route route = element.getAnnotation(Route.class);

                List<FieldModel> requiredNames = new ArrayList<>();
                List<FieldModel> noRequiredNames = new ArrayList<>();
                for (Element field : element.getEnclosedElements()) {
                    if (field.getKind().isField() && field.getAnnotation(Autowired.class) != null) {
                        Autowired paramConfig = field.getAnnotation(Autowired.class);
                        String injectName = TextUtils.isEmpty(paramConfig.name()) ? field.getSimpleName().toString() : paramConfig.name();
                        boolean required = paramConfig.required();
                        if (required) {
                            requiredNames.add(new FieldModel(injectName, field.asType(), paramConfig.desc()));
                        } else {
                            noRequiredNames.add(new FieldModel(injectName, field.asType(), paramConfig.desc()));
                        }
                    }
                }
                //添加方法
                List<MethodSpec> methodSpecs =
                        generateLaunchMethodList("launch" + element.getSimpleName(),
                                route.path(),
                                requiredNames, noRequiredNames);
                methodList.addAll(methodSpecs);
            }

            for (MethodSpec method : methodList) {
                activityLaunchBuilder.addMethod(method);
            }
            //包名
            JavaFile javaFile = JavaFile.builder(PACKAGE_OF_GENERATE_FILE, activityLaunchBuilder.build())
                    .build();

            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String generateClassName() {
        StringBuilder builder = new StringBuilder();
        builder.append(moduleName.substring(0, 1).toUpperCase());
        builder.append(moduleName.substring(1, moduleName.length()));
        builder.append("ActivityLaunch");
        return builder.toString();
    }

    private List<MethodSpec> generateLaunchMethodList(String methodName, String routerPath, List<FieldModel> requiredNames, List<FieldModel> norequiredNames) {
        List<MethodSpec> methodSpecList = new ArrayList<>();
        //必选参数方法
        methodSpecList.add(generateLaunchMenthod(methodName, routerPath, requiredNames));
        //必选+非必选参数方法
        List<FieldModel> paramNames = new ArrayList<>();
        if (norequiredNames != null && norequiredNames.size() > 0) {
            paramNames.addAll(requiredNames);
            paramNames.addAll(norequiredNames);
            methodSpecList.add(generateLaunchMenthod(methodName, routerPath, paramNames));
        }
        //是否有独立参数ALONE
        for (int i = 0; i < paramNames.size(); i++) {
            FieldModel fieldModel = paramNames.get(i);
            if (TextUtils.equals(fieldModel.getDesc(), Consts.ALONE_DESC)) {
                methodSpecList.add(generateLaunchMenthod(methodName, routerPath, Collections.singletonList(fieldModel)));
            }
        }

        return methodSpecList;
    }

    private MethodSpec generateLaunchMenthod(String methodName, String routerPath, List<FieldModel> paramNames) {
        TypeMirror type_Context = elements.getTypeElement(CONTEXT).asType();

        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class);
        builder.addParameter(ClassName.get(type_Context), "context");
        for (int i = 0; i < paramNames.size(); i++) {
            builder.addParameter(TypeName.get(paramNames.get(i).getTypeMirror()), paramNames.get(i).getName());
        }
        //语法
        TypeMirror type_bundle = elements.getTypeElement(Consts.BUNDLE).asType();
        builder.addStatement("$T bundle = new $T()", ClassName.get(type_bundle), ClassName.get(type_bundle));
        for (int i = 0; i < paramNames.size(); i++) {
            int type = typeUtils.typeExchange(paramNames.get(i).getTypeMirror());
            String buildStatement = buildStatement(type, paramNames.get(i).getName());
            if (!TextUtils.isEmpty(buildStatement)) {
                builder.addStatement(buildStatement);
            }
        }
        TypeMirror routerManager = elements.getTypeElement(ROUTER_MANAGER).asType();
        builder.addStatement("$T.getInstance().build(\"" + routerPath + "\").with(bundle).navigation()", ClassName.get(routerManager));

        return builder.build();
    }

    private String buildStatement(int type, String paramName) {
        if (type == TypeKind.BOOLEAN.ordinal()) {
            return "bundle.putBoolean(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.BYTE.ordinal()) {
            return "bundle.putByte(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.SHORT.ordinal()) {
            return "bundle.putShort(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.INT.ordinal()) {
            return "bundle.putInt(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.LONG.ordinal()) {
            return "bundle.putLong(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.CHAR.ordinal()) {
            return "bundle.putChar(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.FLOAT.ordinal()) {
            return "bundle.putFloat(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.DOUBLE.ordinal()) {
            return "bundle.putDouble(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.STRING.ordinal()) {
            return "bundle.putString(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.SERIALIZABLE.ordinal()) {
            return "bundle.putSerializable(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.PARCELABLE.ordinal()) {
            return "bundle.putParcelable(\"" + paramName + "\", " + paramName + ")";
        } else if (type == TypeKind.OBJECT.ordinal()) {
//            statement = "serializationService.parseObject(substitute." + (isActivity ? "getIntent()." : "getArguments().") + (isActivity ? "getStringExtra($S)" : "getString($S)") + ", new com.alibaba.android.arouter.facade.model.TypeWrapper<$T>(){}.getType())";
            return "";
        }
        return "";
    }

    private void logInfo(String log) {
        logger.info(">>> DR " + log + " <<<");

    }
}
