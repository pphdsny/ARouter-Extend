package com.pphdsny.lib.route.compiler.model;

import javax.lang.model.type.TypeMirror;

/**
 * Created by wangpeng on 2019/2/27.
 */
public class FieldModel {
    private String name;
    private TypeMirror typeMirror;
    private String desc;

    public FieldModel(String name, TypeMirror typeMirror, String desc) {
        this.name = name;
        this.typeMirror = typeMirror;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TypeMirror getTypeMirror() {
        return typeMirror;
    }

    public void setTypeMirror(TypeMirror typeMirror) {
        this.typeMirror = typeMirror;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }
}
