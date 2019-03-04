package com.pphdsny.lib.route.compiler.util;

import com.alibaba.android.arouter.facade.enums.TypeKind;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.pphdsny.lib.route.compiler.util.Consts.BOOLEAN;
import static com.pphdsny.lib.route.compiler.util.Consts.BYTE;
import static com.pphdsny.lib.route.compiler.util.Consts.CHAR;
import static com.pphdsny.lib.route.compiler.util.Consts.DOUBEL;
import static com.pphdsny.lib.route.compiler.util.Consts.FLOAT;
import static com.pphdsny.lib.route.compiler.util.Consts.INTEGER;
import static com.pphdsny.lib.route.compiler.util.Consts.LONG;
import static com.pphdsny.lib.route.compiler.util.Consts.PARCELABLE;
import static com.pphdsny.lib.route.compiler.util.Consts.SERIALIZABLE;
import static com.pphdsny.lib.route.compiler.util.Consts.SHORT;
import static com.pphdsny.lib.route.compiler.util.Consts.STRING;

/**
 * Utils for type exchange
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/21 下午1:06
 */
public class TypeUtils {

    private Types types;
    private Elements elements;
    private TypeMirror parcelableType;
    private TypeMirror serializableType;

    public TypeUtils(Types types, Elements elements) {
        this.types = types;
        this.elements = elements;

        parcelableType = this.elements.getTypeElement(PARCELABLE).asType();
        serializableType = this.elements.getTypeElement(SERIALIZABLE).asType();
    }

    /**
     * Diagnostics out the true java type
     *
     * @return Type class of java
     */
    public int typeExchange(TypeMirror typeMirror) {

        // Primitive
        if (typeMirror.getKind().isPrimitive()) {
            return typeMirror.getKind().ordinal();
        }

        switch (typeMirror.toString()) {
            case BYTE:
                return TypeKind.BYTE.ordinal();
            case SHORT:
                return TypeKind.SHORT.ordinal();
            case INTEGER:
                return TypeKind.INT.ordinal();
            case LONG:
                return TypeKind.LONG.ordinal();
            case FLOAT:
                return TypeKind.FLOAT.ordinal();
            case DOUBEL:
                return TypeKind.DOUBLE.ordinal();
            case BOOLEAN:
                return TypeKind.BOOLEAN.ordinal();
            case CHAR:
                return TypeKind.CHAR.ordinal();
            case STRING:
                return TypeKind.STRING.ordinal();
            default:    // Other side, maybe the PARCELABLE or SERIALIZABLE or OBJECT.
                if (types.isSubtype(typeMirror, parcelableType)) {  // PARCELABLE
                    return TypeKind.PARCELABLE.ordinal();
                } else if (types.isSubtype(typeMirror, serializableType)) {  // PARCELABLE
                    return TypeKind.SERIALIZABLE.ordinal();
                } else {    // For others
                    return TypeKind.OBJECT.ordinal();
                }
        }
    }
}
