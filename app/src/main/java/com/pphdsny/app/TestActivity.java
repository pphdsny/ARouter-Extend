package com.pphdsny.app;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.launcher.ARouter;
import com.pphdsny.app.databinding.ActivityTestBinding;

/**
 * Created by wangpeng on 2019/3/4.
 */
@Route(path = "/app/test")
public class TestActivity extends AppCompatActivity {

    @Autowired(required = true)
    public String name;
    @Autowired
    public int age;
    @Autowired(desc = "ALONE")
    public int id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //一定要写
        ARouter.getInstance().inject(this);
        ActivityTestBinding testBinding = DataBindingUtil.setContentView(this, R.layout.activity_test);
        testBinding.tvPage.setText(name + age + id);

    }
}
