package com.uestc.hb.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

public abstract class BaseActivity extends ActionBarActivity{
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(setRootView());
		initLayout();
		initListener();
		initValue();
	}
	/**
	 * ��ʼ�����֣���Ҫ��findView��inflate
	 */
	abstract protected void initLayout();
	/**
	 * ��ʼ��������������setOnClickListener
	 */
	abstract protected void initListener();
	/**
	 * ��ʼ��ֵ������setAdapter��setText
	 */
	abstract protected void initValue();

	
	/**
	 * ���ø���ͼlayout
	 * @return layoutResID
	 */
	abstract protected int setRootView();
	
}
