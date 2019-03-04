# [基于ARouter做的一些扩展](https://www.jianshu.com/p/2127607f6440)

> [ARouter](https://github.com/alibaba/ARouter)是一个用于帮助 Android App 进行组件化改造的框架 —— 支持模块间的路由、通信、解耦

## 前言

最近在做全面的页面URL化，之前引入了ARouter，但是并没有真正的用起来，页面跳转还是通过以下方式

```java
@Route(path = "/app/test")
public class TestActivity extends BaseActivity {
    public static final String EXTRA_HOUSE_ID = "extra_house_id";      //小区id
    public static final String EXTRA_CITY_ID = "extra_city_id";        //城市id

    private int mHouseId;
    private int mCityId;
    
    //...
    @Override
    protected void initIntentParams(Intent intent) {
        super.initIntentParams(intent);
        mHouseId = intent.getIntExtra(EXTRA_HOUSE_ID, 0);
        mCityId = intent.getIntExtra(EXTRA_CITY_ID, 0);
    }
    //...

    public static void launchActivity(Context context, int cityId, int houseId) {
        Bundle bundle = new Bundle();
        if (cityId <= 0) {
            //如传递的cityId无效，则使用全局的cityId
            cityId = CityManager.getInstance().getCityId();
        }
        bundle.putInt(EXTRA_CITY_ID, cityId);
        bundle.putInt(EXTRA_HOUSE_ID, houseId);
        ARouter.getInstance().build("/app/test").with(bundle).navigation();
    }
}
```

目前这种方法的最大问题就是==>重复的模板代码

- `launchActivity`中的参数赋值
- `initIntentParams`中的参数解析
- `cityId`的有效性检测

ARouter的官方用法：

```java
// 在支持路由的页面上添加注解(必选)
// 这里的路径需要注意的是至少需要有两级，/xx/xx
@Route(path = "/test/activity")
public class YourActivity extend Activity {
    ...
}
//-------------------
// 1. 应用内简单的跳转(通过URL跳转在'进阶用法'中)
ARouter.getInstance().build("/test/activity").navigation();

// 2. 跳转并携带参数
ARouter.getInstance().build("/test/1")
            .withLong("key1", 666L)
            .withString("key3", "888")
            .withObject("key4", new Test("Jack", "Rose"))
            .navigation();
```

其实ARouter对于我使用过程遇到的这些问题都能解决，为什么我一直都没有真正的用起来呢？

最主要的原因就是ARouter的**path路径**和**页面参数定义**，也就是我不喜欢`ARouter.getInstance().build("/test/activity").navigation()`这种跳转方式，原因有以下几点：

- **path路径**和**页面参数定义**最清楚的应该是模块开发者（A），对于调用方（B）并不是很清楚path和params是什么？
- B调用A的页面时候，并不确定哪些参数是可选的，哪些是必选？
- 当A开发的模块需要新增个必选参数时候，B并不知道，而且编译不会报错。

当然，上面说的这些，你都可以说B不知道去看开发文档么、A会告诉B的…，这些都是建立在理想情况下，再说每次开发都看文档那得多累，不是么….

如果能把目前项目中用到的`launchActivity`方法自动生成的话，那该多爽啊….

说干就干！！！

## 目标

- 自动生成`launchActivity`方法
- `launchActivity`方法中区分必选参数和可选参数
- 参数的有效性检测
- URL跳转参数类型转换

## 自动生成`launchActivity`方法

实现方案：APT，整体参考ARouter-compiler中的实现，关于APT有很多文章介绍，就不多说了，直接上代码：

### 依赖库

```groovy
apply plugin: 'java-library'

sourceCompatibility = "8"
targetCompatibility = "8"

dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation 'com.alibaba:arouter-annotation:1.0.6'
    implementation 'com.google.auto.service:auto-service:1.0-rc3'
    implementation 'com.squareup:javapoet:1.8.0'
}
```

> com.alibaba:arouter-annotation:1.0.6 —> 基于ARouter的注解扩展
>
> com.google.auto.service:auto-service:1.0-rc3 —>方便生成编译依赖
>
> com.squareup:javapoet:1.8.0 —>方便生成java文件

### Processor API

```java
public interface Processor {
    //支持的可选参数，可通过getOptions()获取==>AROUTER_MODULE_NAME
    Set<String> getSupportedOptions();

    //支持的注解类型==>com.alibaba.android.arouter.facade.annotation.Route
    Set<String> getSupportedAnnotationTypes();

    //支持的源码版本==>SourceVersion.RELEASE_8
    SourceVersion getSupportedSourceVersion();

    //初始化，可以获取运行的一些环境参数
    void init(ProcessingEnvironment var1);

    //对注解进行处理，返回true的话，后续拦截器无法处理(阿里自己的RouteProcessor就返回了true，蛋疼！！！)
    boolean process(Set<? extends TypeElement> var1, RoundEnvironment var2);

    Iterable<? extends Completion> getCompletions(Element var1, AnnotationMirror var2, ExecutableElement var3, String var4);
}
```

### 实现Processor

那些Processor的方法，可以使用下列注解实现

```java
@AutoService(Processor.class)
@SupportedOptions({KEY_MODULE_NAME, KEY_GENERATE_DOC_NAME})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE})
public class RouterProcessor extends AbstractProcessor {
 //...   
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
        //一定要返回false，不然就会想ARouter自己的RouterProcessor一样，别人就没办法继续处理此Annotation了
        return false;
    }
    //...
}
```

### parseRoutes

```java
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
			//获取使用@Autowired标注的参数
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
        //写入包名，生成文件
        JavaFile javaFile = JavaFile.builder(PACKAGE_OF_GENERATE_FILE, activityLaunchBuilder.build())
                .build();
        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### 生成launchActivity方法

#### generateLaunchMethodList

生成Activity支持带参数启动的方法列表，目前支持三种模式

- `Autowired`中`required = true`的所有参数
- `Autowired`修饰的所有参数（必选+非必选参数）
- `Autowired`中`desc = "ALONE"`的独立参数

```java
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
```

#### generateLaunchMenthod

根据入参list，生成launchActivity方法

```java
private MethodSpec generateLaunchMenthod(String methodName, String routerPath, List<FieldModel> paramNames) {
    TypeMirror type_Context = elements.getTypeElement(CONTEXT).asType();

    MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(void.class);
    //添加context参数
    builder.addParameter(ClassName.get(type_Context), "context");
    //添加自定义参数
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
    //这部分可替换成ARouter的跳转方法
    builder.addStatement("$T.getInstance().navigation(context, \"" + routerPath + "\", bundle)", ClassName.get(routerManager));

    return builder.build();
}
```

## 最终效果

==>源文件

```java
@Route(path = "/app/test")
public class TestActivity extends Activity {

    @Autowired(required = true)
    public int cityId;
    @Autowired
    public int houseId;
    @Autowired(desc = "ALONE")
    public Object test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //一定要写
        ARouter.getInstance().inject(this);
        setContentView(R.layout.activity_test);
    }
}
```

==>生成的launchActivity文件

```java
public final class AppActivityLaunch {
  public static void launchTestActivity(Context context, int cityId) {
    Bundle bundle = new Bundle();
    bundle.putString("cityId", cityId);
    RouterManager.getInstance().navigation(context, "/app/test", bundle);
  }

  public static void launchTestActivity(Context context, int cityId, int houseId, Object test) {
    Bundle bundle = new Bundle();
    bundle.putString("cityId", cityId);
    bundle.putInt("houseId", houseId);
    RouterManager.getInstance().navigation(context, "/app/test", bundle);
  }

  public static void launchTestActivity(Context context, Object test) {
    Bundle bundle = new Bundle();
    RouterManager.getInstance().navigation(context, "/app/test", bundle);
  }
}
```

后续跳转直接就通过`AppActivityLaunch` 中的`launchXXXActivity`方法，方便、直接、明了、不易出错！

## 遇到的一些坑

1. 自己独立编译能生成文件，和ARouter集成后无法生成文件

   注意以下的顺序，是不是写在ARouter_compiler后面了

   ```groovy
   //必须写在arouter_compiler前面，因为arouter_compiler对注解处理返回true，后续没办法处理
   kapt dependencies.router_compiler		//自己的route_compiler
   kapt dependencies.arouter_compiler		//阿里的ARouter_compiler
   ```

2. 使用`@Autowired`标识的属性无法获取

   可以从以下几方面继续排查：

   - 是否在`onCreate`方法中写了：`ARouter.getInstance().inject(this);`
   - 注意属性的名称是否一致
   - 注意属性的类型是否一致，Float和double，int和String是无法接受的
   - Serializable类型的属性无法获取，注意router-api的版本是否是最新的1.4.1，之前使用的1.3.1的版本中Serializable被当做对象，需要自己写解析器才能解析。
   - 再无法获取，可以全局搜索ARoute_complier的生成类`TestActivity$$ARouter$$Autowired`，debug一下具体情况

3. 全局参数有效性的补充逻辑

   例如`cityId`是一个全局参数，当入参的`cityId`不存在或者无效，需要填充全局的`cityId`作为入参。

   刚开始是考虑在`route_compiler`中填充这部分判断代码，后面考虑了一下，耦合性太大，于是借用了@Route中的`extras`属性，使用`ARouter`中提供的**拦截器（IInterceptor）**。

   ```java
   @Interceptor(priority = 1, name = "CityID有效性检测")
   public class CityIdInterceptor implements IInterceptor {
       @Override
       public void process(Postcard postcard, InterceptorCallback callback) {
           int extra = postcard.getExtra();
           if ((extra & RouterExtraCons.CITY_ID_CHECK) == RouterExtraCons.CITY_ID_CHECK) {
               //检测cityId是否有效
               int cityId = postcard.getExtras().getInt("cityId");
               if (cityId <= 0) {
                   //填充全局的cityId
                   postcard.getExtras().putInt("cityId", CityManager.getInstance().getCityId());
               }
           }
           callback.onContinue(postcard);
       }
   
       @Override
       public void init(Context context) {
   
       }
   }
   ```

4. 外部Uri跳转进来的时候，由于通过Uri通过`getQueryParameter`获取的参数都是String，如果目标`Activity`需要的入参是Int的时候，就没办法匹配上了，咋办？

   目前没有比较好的方法，用了个兼容性差但是能满足业务要求的方法==>获取到String后强转，代码如下：

   ```java
       /**
        * 默认强制转换格式Int,Float,Boolean
        *
        * @param param
        * @return
        */
       private static Object parseParamType(String param) {
           if (param == null) return "";
           //int
           try {
               return Integer.parseInt(param);
           } catch (NumberFormatException e) {
               LogUtils.d("parseParamType is not Integer");
           }
           //Float
           try {
               return Float.parseFloat(param);
           } catch (NumberFormatException e) {
               LogUtils.d("parseParamType is not Float");
           }
           //boolean
           try {
               if (TextUtils.equals(param, "true") || TextUtils.equals(param, "false"))
                   return Boolean.parseBoolean(param);
           } catch (NumberFormatException e) {
               LogUtils.d("parseParamType is not Boolean");
           }
           return param;
       }
   ```

## 后记

目前做的这些，还是很有限的，只是对`ARouter`的一点点扩展，基于自己的业务和第三方库的配套使用，底层技术点都是相通的，在注解的扩展性上`ARouter`还是有待加强的。

欢迎交流~ ~